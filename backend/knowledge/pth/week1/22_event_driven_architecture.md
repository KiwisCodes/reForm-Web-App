# Architectural Specification: Coupling, Dependency Injection, and In-Process Event-Driven Architecture (EDA)
**Document Version:** 1.1  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Introduction: The Pragmatic Monolith

In software engineering, there is no single "correct" pattern. Every architectural choice is a calculated trade-off.

When building **Reform**, we face a fundamental design question: **How should our modules (such as User, Workspace, Billing, and Forms) communicate?**

If the `User` module needs to trigger workspace creation inside the `Workspace` module upon registration, we have two primary architectural patterns to choose from:
1.  **Interface-Based Dependency Injection (Direct Service Calls)**
2.  **In-Process Event-Driven Architecture (Domain Events)**

This document analyzes both patterns, maps their mechanisms, and compares their technical trade-offs so you can make informed decisions as our system grows.

---

## 2. Decoupling Deciphered: Implementation vs. Domain Coupling

To choose between Dependency Injection and Events, we must first distinguish between two different types of coupling: **Implementation Coupling** and **Domain (Compile-Time) Coupling**.

```text
               THE TWO DIMENSIONS OF COUPLING
               
   [DIMENSION A: Implementation Coupling]
   
     TIGHT: Class depends on another concrete Class.
     DECOUPLED: Class depends on an Interface (resolved via Spring DI).
     
   
   [DIMENSION B: Domain / Compile-Time Coupling]
   
     TIGHT: Module A imports a class from Module B's package.
     DECOUPLED: Module A publishes an Event; Module B listens. No imports.
```

### Dimension A: Implementation Coupling (Your View)
When `UserServiceImpl` injects `IWorkspaceService` (the interface), you have decoupled the **implementation**.

```text
                IMPLEMENTATION DECOUPLING (DI)
                
  [UserServiceImpl] ──> Injects: [IWorkspaceService] <── Implemented by: [WorkspaceServiceImpl]
```

*   **How it works:** `UserServiceImpl` does not care *how* `IWorkspaceService` does its job (e.g., whether it saves to Postgres or MongoDB). It only cares about the contract.
*   **The Limitation:** While the implementation is decoupled, **the domains are still tightly coupled.**

---

### Dimension B: Domain / Compile-Time Coupling (The Event View)
Even when using an interface, the `/user` folder still contains a physical import pointing to the `/workspace` folder:

```java
package com.reform.app.user.service;

import com.reform.app.workspace.port.IWorkspaceService; // <-- Compile-time import!
```

This means the `user` module **cannot compile or run** if the `workspace` module is missing.

```text
                  DOMAIN / COMPILE-TIME COUPLING
                  
    📁 USER MODULE                               📁 WORKSPACE MODULE
  ┌────────────────────────┐                   ┌────────────────────────┐
  │ UserServiceImpl        │ ──(Depends on)──> │ IWorkspaceService      │
  └────────────────────────┘                   └────────────────────────┘
```

By switching to **In-Process Domain Events**, we eliminate compile-time imports entirely. The `user` module simply publishes an event, remaining completely unaware of who (if anyone) is listening.

```text
                 COMPLETE DOMAIN ISOLATION (EDA)
                 
    📁 USER MODULE                               📁 WORKSPACE MODULE
  ┌────────────────────────┐                   ┌────────────────────────┐
  │ UserServiceImpl        │                   │ WorkspaceEventListener │
  │                        │                   │                        │
  │ (Publishes:            │                   │ (Listens for:          │
  │  UserRegisteredEvent)  │                   │  UserRegisteredEvent)  │
  └───────────┬────────────┘                   └───────────▲────────────┘
              │                                            │
              └─────────────── (Spring Event Bus) ─────────┘
```

---

## 3. Deep Dive: In-Process Domain Events in Spring

Inside our modular monolith, Event-Driven Architecture does not require the operational complexity of external brokers like Kafka or RabbitMQ. Instead, we use Spring's built-in, in-memory **`ApplicationEventPublisher`**.

```text
                                 THE SPRING EVENT BUS PIPELINE
                                 
  [UserService] ──(Publish Event)──> [Spring ApplicationContext]
                                              │
                                              ├─> [WorkspaceListener] (Runs on thread pool)
                                              │
                                              └─> [EmailListener]     (Runs on thread pool)
```

1.  **Publishing:** The `UserService` publishes a Java record (`UserRegisteredEvent`) to Spring’s `ApplicationContext`.
2.  **Dispatching:** Spring scans its active container beans for any method annotated with `@EventListener` or `@TransactionalEventListener` that accepts `UserRegisteredEvent` as its input parameter.
3.  **Execution:** Spring passes the event object to those listeners. The execution can run synchronously on the main thread, or asynchronously on a separate thread pool.

---

## 4. The Engineering Trade-off Matrix

There is no "perfect" pattern. Here is the objective architectural comparison of both approaches:

| Architectural Metric | **Interface-Based DI (Direct Service)** | **In-Process EDA (Domain Events)** |
| :--- | :--- | :--- |
| **Domain Coupling** | **Medium-High:** Modules are bound together at compile-time by package imports. | **Zero:** Modules are completely isolated. No cross-package imports are needed. |
| **Transactional Integrity (ACID)** | **Excellent (Strong Consistency):** Both operations run inside the same SQL transaction. If workspace creation fails, registration rolls back automatically. | **Complex (Eventual Consistency):** If running asynchronously, the workspace creation can fail *after* the user is already saved, creating orphaned records. |
| **Cognitive Overhead** | **Very Low:** The code is linear and easy to trace. You can hold `Ctrl` and click the method to see exactly what runs next. | **High:** The execution path is indirect. It is harder for new developers to track "who is listening" to a specific event. |
| **Circular Dependencies** | **High Risk:** If `User` calls `Workspace`, and `Workspace` needs to call `User`, Spring Boot will fail to start (`CircularDependencyException`). | **No Risk:** Circular dependencies are structurally impossible since neither module references the other. |
| **Performance (Latency)** | **Slower:** The user’s HTTP request must wait for the database writes of *both* the User and the Workspace to complete. | **Faster:** The user's HTTP request completes instantly after the User is saved; Workspace creation runs silently in the background. |
| **Microservice Readiness** | **Hard:** Requires rewriting code and removing interface imports to split features into separate servers. | **Instant:** You can move the `/user` folder to its own project immediately; you only have to swap the local publisher for a Kafka/Rabbit client. |

---

## 5. Transactional Boundaries & Failure Propagation

When using Events, how we configure our **Transaction Boundaries** determines how our system behaves under failures.

```text
                      TRANSACTION BOUNDARY STRATEGIES
                      
    ┌───────────────────────────────────────────────────────────────────┐
    │ OPTION A: Synchronous @EventListener                              │
    │   - User & Workspace share the same Thread & Database Transaction.│
    │   - If Workspace fails, User registration ROLLS BACK.             │
    └───────────────────────────────────────────────────────────────────┘
    
    ┌───────────────────────────────────────────────────────────────────┐
    │ OPTION B: Asynchronous @Async @EventListener                      │
    │   - User commits first. Workspace runs on a separate Thread.      │
    │   - If Workspace fails, User is STILL SAVED (Orphaned User).      │
    └───────────────────────────────────────────────────────────────────┘
    
    ┌───────────────────────────────────────────────────────────────────┐
    │ OPTION C: @TransactionalEventListener(phase = AFTER_COMMIT)       │
    │   - Listener only runs AFTER User transaction is fully committed. │
    │   - Guarantees we never process events for users who failed to save.│
    └───────────────────────────────────────────────────────────────────┘
```

---

## 6. Step-by-Step Implementation Guide

Here is the decoupled design implementation for **Reform**.

### Step 1: Define the Base Event Contract
Create this interface in `com.reform.app.core.event`.

```java
package com.reform.app.core.event;

import java.time.Instant;

public interface IDomainEvent {
    /**
     * @return The exact timestamp when this event occurred.
     */
    Instant getOccurredAt();
}
```

### Step 2: Implement the Publisher Utility
Create this class in `com.reform.app.core.event`. It wraps Spring's event tool, keeping our core business domains free from direct framework dependency.

```java
package com.reform.app.core.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    public void publishEvent(IDomainEvent event) {
        springPublisher.publishEvent(event);
    }
}
```

### Step 3: Create the Concrete Event Payload
Create this record in `com.reform.app.user.event`. This immutable "envelope" contains only the lightweight data required by listeners (IDs and names).

```java
package com.reform.app.user.event;

import com.reform.app.core.event.IDomainEvent;
import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
    UUID userId,
    String username,
    Instant occurredAt
) implements IDomainEvent {

    public UserRegisteredEvent(UUID userId, String username) {
        this(userId, username, Instant.now());
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }
}
```

### Step 4: Write the Workspace Event Listener
Create this class in your `workspace/service` or `workspace/event` directory.

Depending on your transactional needs, choose **one** of these listener configurations:

```java
package com.reform.app.workspace.service;

import com.reform.app.user.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkspaceEventListener {

    // private final IWorkspaceService workspaceService;

    /**
     * OPTION A: Asynchronous Background Processing
     * Best for: Rapid API response times.
     */
    @Async
    @EventListener
    public void onUserRegisteredAsync(UserRegisteredEvent event) {
        log.info("[ASYNC] User registered: {}. Creating workspace on thread: {}", 
            event.username(), Thread.currentThread().getName());
        // workspaceService.createDefaultWorkspace(event.userId());
    }

    /**
     * OPTION B: Transactional & Eventual Consistency Guaranteed
     * Best for: Ensuring we only create workspaces for users who successfully saved in the DB.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegisteredAfterCommit(UserRegisteredEvent event) {
        log.info("[TRANSACTIONAL] User {} saved successfully. Executing workspace logic...", 
            event.username());
        // workspaceService.createDefaultWorkspace(event.userId());
    }
}
```

---

## 🏁 7. Socratic Review & Homework

To master these design patterns, analyze these three trade-off scenarios:

1.  **The Distributed Transaction Problem:** Suppose you choose **Option A (Asynchronous)**. A user registers, their account is saved, and the transaction commits. The background `WorkspaceEventListener` triggers, but your database server suddenly runs out of disk space, causing workspace creation to fail.
    *   *Question:* You now have an "orphaned" user who can log in but has no workspace, causing null-pointer crashes on their dashboard. How would you design a **reconciliation background job** or **retry mechanism** to heal this eventually consistent data?
2.  **The Testing Dilemma:** In our modular monolith, if we use the direct Interface Dependency Injection approach, we can easily use `@SpringBootTest` to test a registration end-to-end.
    *   *Question:* When testing an Event-Driven setup with `@Async` listeners, our test thread might finish and assert results *before* the background listener thread even starts running. How do you solve this asynchronous testing race condition in JUnit? (Hint: Research Awaitility or Spring's `SyncTaskExecutor`).
3.  **The Pragmatic Choice:** Given Reform's current state as an **MVP for thousands of users**, which communication pattern is more pragmatic to start with? Direct interface calls or Domain Events? *Defend your choice using the criteria of "Development Speed" vs. "Long-Term Scalability."*