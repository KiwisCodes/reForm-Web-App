# 18. Spring Event-Driven Architecture & Dependency Injection
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. `@Autowired` vs Constructor Injection (`@RequiredArgsConstructor`)

### Why `@Autowired` Field Injection is Bad Practice:
1. **Breaks Immutability:** Fields annotated with `@Autowired` cannot be declared as `final`. They can be mutated at runtime.
2. **Hides Dependencies:** A class can have 15 `@Autowired` fields without notice. Constructor injection forces dependencies into the constructor signature, instantly signaling code smells.
3. **Hinders Unit Testing:** You cannot instantiate the class cleanly in unit tests (`new MyService()`) without using reflection or Spring test contexts.
4. **Hides Circular Dependencies:** `@Autowired` hides circular references until runtime crashes occur.

### The Production Standard: Constructor Injection with Lombok
We use Lombok's `@RequiredArgsConstructor` combined with `private final` fields:

```java
@Service
@RequiredArgsConstructor // Automatically generates a public constructor for all final fields
public class SessionContextService {

    private final List<IAiModelProviderStrategy> modelStrategies; // Immutable & required
    private final WorkspaceRepository workspaceRepository;
    private final EncryptionService encryptionService;
}
```

---

## 2. What is a POJO (Plain Old Java Object)?

A **POJO (Plain Old Java Object)** is an ordinary Java object with no special framework annotations, no inheritance from framework classes (like Spring `Component` or JPA `Entity`), and no heavy magic.

In Java 21, **`record`** is the ultimate POJO construct for events:
```java
// Immutable POJO Event class using Java 21 record
public record FormLayoutModificationEvent(String formId, String userIntent) {}
```

---

## 3. How to Define Event Publishers and Listeners in Spring

### A. Defining the Event Publisher
Inject `ApplicationEventPublisher` as a `private final` field:

```java
@Component
@RequiredArgsConstructor
public class GeminiLiveVoiceAdapter implements IAiVoiceAdapter {

    private final ApplicationEventPublisher eventPublisher; // Injected via constructor

    public void handleToolCall(String formId, String userIntent) {
        // Publishes event onto Spring's internal ApplicationContext event bus
        eventPublisher.publishEvent(new FormLayoutModificationEvent(formId, userIntent));
    }
}
```

### B. Defining the Event Listener
Annotate listener methods with `@EventListener` and `@Async` (for non-blocking background threads):

```java
@Component
@RequiredArgsConstructor
public class LayoutAgent {

    private final Gemini36FlashService textLlmService;

    @Async // Executes asynchronously in a background worker thread pool
    @EventListener
    public void handleLayoutModification(FormLayoutModificationEvent event) {
        // Executes Mode 2 Gemini 3.6 Flash schema generation and pushes WebSocket updates
    }
}
```

---

## 4. Event-Driven Architecture (EDA): Deep Problem & Trade-off Analysis

### A. What problem existed BEFORE Event-Driven Architecture? (Concrete Example)
Imagine a traditional monolithic checkout method in `OrderService`:

```java
// ❌ BEFORE EVENT-DRIVEN (Tight Coupling & Cascading Failures)
@Transactional
public void processOrder(OrderRequest request) {
    Order order = orderRepository.save(new Order(request)); // 1. DB Save (5ms)
    paymentService.chargeCard(request.getCreditCard());     // 2. Stripe API (500ms)
    emailService.sendReceiptEmail(order.getUserEmail());    // 3. Email Gateway (800ms) - If this fails, checkout crashes!
    inventoryService.reserveStock(order.getItems());        // 4. Inventory DB (100ms)
    analyticsService.trackPurchase(order);                  // 5. Analytics API (300ms)
} // Total Time: 1,705ms! If Email Gateway times out, user's entire order fails!
```

### B. How Event-Driven Architecture Solves This:
```java
// ✅ AFTER EVENT-DRIVEN (Loose Coupling & Failure Isolation)
@Transactional
public void processOrder(OrderRequest request) {
    Order order = orderRepository.save(new Order(request));
    paymentService.chargeCard(request.getCreditCard());
    
    // Publishes event in 1ms and returns HTTP 200 OK immediately!
    eventPublisher.publishEvent(new OrderCompletedEvent(order));
} // Total Time: 505ms! Email, inventory & analytics run asynchronously in background listeners.
```

### C. Pros and Cons of Event-Driven Architecture

| Pros | Cons |
| :--- | :--- |
| **Loose Coupling:** Publisher does not know or care who listens. | **Increased Complexity:** Harder to trace code execution paths. |
| **Microsecond Latency:** Callers return immediately without waiting for background tasks. | **No Immediate Transaction Rollback:** Asynchronous listeners cannot abort the caller's DB transaction. |
| **Failure Isolation:** If email sending fails, the core order remains valid. | **Eventual Consistency:** Data across background listeners updates asynchronously. |

### D. The Right Questions to Ask to Know: *"Is it time for Event-Driven?"*
Ask yourself these 3 questions:
1. *"Does the main user request need to wait for this secondary task to finish before responding?"* (If **NO** $\rightarrow$ Use Events).
2. *"If this secondary task fails, should the main operation abort and roll back?"* (If **NO** $\rightarrow$ Use Events).
3. *"Will multiple unrelated features (e.g. analytics, notifications, audits) need to trigger off this single action in the future?"* (If **YES** $\rightarrow$ Use Events).

---

## 5. Message Queues (RabbitMQ vs. Kafka vs. Spring In-Memory Events)

### A. When to move from Spring Events to a Distributed Message Queue?
Spring `ApplicationEventPublisher` operates **in-memory within a single JVM process**.

#### What problem existed BEFORE Message Queues?
* **Server Crashes:** If your server crashes while processing an in-memory Spring Event, **the event is permanently lost from RAM**.
* **Multi-Node Clusters:** Server A cannot publish an in-memory Spring Event to a listener running on Server B.

### B. Key Questions to Ask: *"Is it time for a Message Queue?"*
1. *"Must this background job be guaranteed to survive a full server crash/restart?"* (If **YES** $\rightarrow$ Message Queue with persistent disk storage).
2. *"Do I need to distribute workload across 10 background worker servers?"* (If **YES** $\rightarrow$ Message Queue).

---

## 6. RabbitMQ vs. Apache Kafka Comparison Matrix

| Dimension | RabbitMQ | Apache Kafka |
| :--- | :--- | :--- |
| **Architecture** | **AMQP Broker:** Message queue with complex routing exchanges (Direct, Topic, Fanout). | **Distributed Commit Log:** Append-only ordered topic partitions. |
| **Message Delivery** | Messages are deleted from queue once acknowledged by a consumer. | Messages persist on disk for N days; consumers track their own log offsets. |
| **Routing Flexibility**| Complex routing topologies, priority queues, dead-letter exchanges. | Simple topic partition key hashing. |
| **Throughput** | ~20,000 to 50,000 msgs/sec | **> 1,000,000 msgs/sec** (Extreme throughput). |
| **Best Used For** | Transactional background tasks, order fulfillment, retries, worker queues. | Event streaming, log analytics, clickstream telemetry, real-time metrics. |
| **Pricing / Deployment** | Open Source (Free in Docker/K8s) or CloudAMQP. | Open Source (Free in Docker/K8s) or Confluent Cloud. |
