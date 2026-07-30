# Agent Engineering Mechanics & Dynamic Sub-Agent Spawning
**Document Version:** 1.0  
**Location:** `backend/knowledge/pth/week3/07_agent_mechanics_and_subagent_spawning.md`  
**Target System:** reForm Platform (`com.reForm.backend.ai`)  

---

## 1. What Are Agents in Software Engineering Terms?

In reForm, an **Agent** is NOT a single opaque AI model or an external process. In concrete Java Spring Boot terms, an Agent is a **Spring `@Component` service bean that encapsulates LLM calls, vector DB queries, state management, and business logic**:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ CONCRETE AGENT ANATOMY (Spring Boot Component)                              │
│                                                                             │
│  @Component / @Service                                                      │
│  public class LayoutAgent {                                                 │
│      private final Gemini36FlashService llmService; // LLM Client           │
│      private final FormRepository formRepository;     // PostgreSQL Access   │
│      private final SimpMessagingTemplate messaging;   // Canvas WS Push      │
│                                                                             │
│      @Async // Non-blocking background worker thread pool                   │
│      @EventListener                                                         │
│      public void handleLayoutModification(FormLayoutModificationEvent event){│
│          // 1. Prompts LLM for JSON schema                                  │
│          // 2. Mutates Form Entity blocks in PostgreSQL                      │
│          // 3. Pushes WebSocket update to UI Canvas                          │
│      }                                                                      │
│  }                                                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Agent Catalog & Implementation Matrix

| Agent Name | Technical Structure | Trigger Mechanism | Input / Output Data | Responsibilities & Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`LayoutAgent`** | Spring `@Component` + `@Async @EventListener` | Spring `FormLayoutModificationEvent` | Input: `userIntent` (String)<br/>Output: `ConversationalBlock` / `StaticBlock` JSON | Receives `toolCall` event, prompts Gemini 3.6 Flash (Mode 2), constructs layout blocks, updates PostgreSQL `Form.blocks` column, and pushes canvas update to frontend. |
| **`GuardrailAgent`** | Spring `@Component` | Called synchronously per turn in `GeminiLiveVoiceAdapter` | Input: Transcript text<br/>Output: `boolean safe` (score 0-1) | Runs `text-embedding-004` embedding generation and `pgvector` HNSW cosine similarity search against toxic/jailbreak prompt vectors (~2ms). |
| **`MemoryGoalAgent`** | Spring `@Service` | Called per turn in adapter | Input: Transcript text<br/>Output: Goal state Map (`VERIFIED`/`PENDING`) | Manages goal checklist in Redis `opsForHash()` (`session:{userId}:goals`). Injects remaining `PENDING` goals into turn context. |
| **`BillingAgent`** | Spring `@Component` | Scheduled task / VAD socket frame | Input: VAD binary frames<br/>Output: Account balance deduction | Monitors VAD silence frames. If candidate is silent for 45s, triggers reminder prompt or pauses streaming. |
| **`RagSearchAgent`** | Spring `@Service` | Triggered when candidate asks question | Input: Query string<br/>Output: Top-3 relevant document passages | Executes hybrid vector distance search over `document_embeddings` table using `pgvector`. |
| **`EvaluationAgent`** | Spring `@Service` + `@Async @EventListener` | Socket closed event (`afterConnectionClosed`) | Input: Complete session transcript<br/>Output: Candidate score (0-100) & PDF report | Fired asynchronously after call ends. Prompts Gemini 3.6 Flash, computes candidate score, generates summary report, saves `Submission`. |

---

## 3. Dynamic Sub-Agent Spawning (How, When, Why, and What)

### A. Why Spin Off Sub-Agents?
During a voice session, a single main agent cannot perform heavy specialized operations (e.g. compiling user code, extracting tabular OCR data from an uploaded PDF, or computing complex rubrics) without stalling the real-time voice pipeline.

### B. What Sub-Agents Are Spawned?

1. **`CodeAnalysisSubAgent`**: Spawned when a candidate submits or speaks code during a technical interview. Compiles and executes code in a sandboxed container.
2. **`DocumentOcrSubAgent`**: Spawned when a form builder uploads a new PDF/Image during a live building session. Preprocesses document via OCR and populates vector index.
3. **`ScoringSubAgent`**: Spawned during multi-criteria evaluations to score domain competencies in parallel worker threads.

### C. How & When Sub-Agents Are Spawned (Java Implementation)

Sub-agents are dynamically instantiated using a **`SubAgentFactory`** and executed via Spring's **`AsyncTaskExecutor`**:

```java
@Component
@RequiredArgsConstructor
public class SubAgentFactory {

    private final ApplicationContext context;
    private final AsyncTaskExecutor taskExecutor;

    public void spawnCodeAnalysisSubAgent(String sessionContextId, String codeSnippet) {
        taskExecutor.execute(() -> {
            CodeAnalysisSubAgent subAgent = context.getBean(CodeAnalysisSubAgent.class);
            subAgent.analyzeCode(sessionContextId, codeSnippet);
        });
    }
}
```

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ DYNAMIC SUB-AGENT SPAWNING FLOW                                              │
│                                                                             │
│ Main Voice Stream (Gemini Live) ──► Candidate submits code snippet          │
│                                           │                                 │
│                                           ▼                                 │
│                          SubAgentFactory.spawnCodeAnalysisSubAgent()        │
│                                           │                                 │
│                                           ▼                                 │
│                    Spring AsyncTaskExecutor (Worker Thread Pool)            │
│                                           │                                 │
│                        ┌──────────────────┴──────────────────┐              │
│                        ▼                                     ▼              │
│            [ CodeAnalysisSubAgent ]                [ DocumentOcrSubAgent ]  │
│            (Runs in background thread)             (Runs in background thread)│
└─────────────────────────────────────────────────────────────────────────────┘
```
