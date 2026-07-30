# AI Form Filler Multi-Agent Workflow (Phase B) Deep Dive
**Document Version:** 2.0  
**Location:** `backend/knowledge/pth/week3/03_ai_form_filler_multiagent_workflow.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Concept: Multi-Agent Interview Execution

In **Phase B**, Candidate Sarah takes an AI voice interview. A single conversational AI cannot handle billing, safety moderation, state tracking, and post-session analysis simultaneously without latency degradation. 

reForm deploys a **Multi-Agent Pipeline** where specialized background agents operate concurrently.

```text
                  THE reForm MULTI-AGENT INTERVIEW SYSTEM
                  
                             [ Candidate Sarah ]
                                      │
                                      ▼
                         [ VoiceSyncWSHandler (WSS) ]
                                      │
                                      ▼
                         [ GeminiLiveVoiceAdapter ]
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          ▼                           ▼                           ▼
   [ Guardrail Agent ]       [ Memory/Goal Agent ]       [ Billing Agent ]
   (pgvector Moderation)     (Redis State Hash)          (VAD Silence Meter)
          │                           │                           │
          ▼                           ▼                           ▼
   [ RAG Vector Agent ]      [ Form Canvas Sync ]        [ Evaluation Agent ]
   (Document Search)         (Answers Ingestion)         (Post-Session Summary)
```

---

## 2. Comprehensive Catalog of 6 Specialized Agents

### 1. Guardrail Agent (Content Moderation & Security)
- **Problem Solved**: Prevents prompt injection, toxic language, and jailbreak attempts.
- **How It Works**: Converts incoming transcript chunks into embeddings via `text-embedding-004` and runs a cosine similarity check against safety rules in `pgvector` (~2ms).
- **Action**: If similarity score > 0.85, interrupts AI stream and triggers safety fallback response.

### 2. Memory & Goal Tracking Agent (State Management)
- **Problem Solved**: Ensures the AI interviewer covers all required evaluation goals without getting stuck in loops.
- **How It Works**: Maintains an active checklist in Redis `opsForHash()` (`session:{userId}:goals`).
- **Action**: Marks goals as `VERIFIED` or `PENDING` as Sarah speaks. Injects remaining `PENDING` goals into the next turn context.

### 3. Billing & VAD Agent (Credit Metering & Protection)
- **Problem Solved**: Prevents runaway token API costs during candidate pauses or open mics.
- **How It Works**: Monitors frontend Voice Activity Detection (VAD) signals.
- **Action**: If silence exceeds 45 seconds, fires a reminder prompt ("Are you still there?") or safely pauses the stream.

### 4. RAG Search Agent (Knowledge Retrieval)
- **Problem Solved**: Answers candidate questions about company policies or job descriptions accurately during the interview.
- **How It Works**: Performs hybrid semantic vector search over PostgreSQL `pgvector` document embeddings.
- **Action**: Injects retrieved context passages into Gemini's turn context.

### 5. Form Canvas Sync Agent (Live Answer Ingestion)
- **Problem Solved**: Maps spoken answers directly into form fields in real time.
- **How It Works**: Listens to candidate transcripts, extracts structured JSON field values (e.g. `experienceYears: 5`), and saves partial draft responses.

### 6. Evaluation & Analytics Agent (Post-Session Processing)
- **Problem Solved**: Evaluates candidate performance after the call ends without adding in-session latency.
- **How It Works**: Triggered asynchronously (`@Async`) when the socket closes.
- **Action**: Prompts **Gemini 3.6 Flash** with the complete transcript, calculates candidate match score (0-100), generates a 1-page summary report, and persists the `Submission` record in PostgreSQL.

---

## 3. Dynamic Sub-Agent Spawning Capability

Can the AI spin off new sub-agents dynamically?

**YES.** Using Spring's `ApplicationEventPublisher` and worker thread pools (`TaskExecutor`), the system dynamically spawns background sub-agents whenever a specific trigger occurs during the voice session (e.g., spawning a **Code Analysis Sub-Agent** if Sarah submits a code snippet during a technical interview).
