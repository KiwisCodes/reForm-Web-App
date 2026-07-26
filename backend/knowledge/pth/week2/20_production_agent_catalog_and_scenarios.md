# 20. Production Agent Catalog & End-to-End Scenarios
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Production Agent Catalog

To maintain low latency and high reliability across Modes 2, 3, and 4, reForm deploys 6 specialized agents:

```text
                  THE reForm AGENTIC PIPELINE
                  
                          [ Client Session ]
                                  │
                                  ▼
                     [ AI Gateway / Streaming Pipe ]
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
   [ 1. Guardrail Agent ]  [ 2. Layout Agent ]     [ 3. Memory/Goal Agent ]
   (Content Moderation)    (JSON Schema Gen)       (State Tracking)
          │                       │                       │
          ▼                       ▼                       ▼
   [ 4. Billing Agent ]    [ 5. Cache Agent ]      [ 6. Evaluation Agent ]
   (Credit Monitoring)     (Redis Pre-fetch)       (Post-Session Summary)
```

1. **Guardrail Agent (Content Moderation):** Evaluates input audio/text against prompt injection and toxic language rules using **pgvector** cosine similarity embeddings (~2ms latency).
2. **Layout Agent (Form Schema Generator):** Listens for `FormLayoutModificationEvent` via `@Async @EventListener`, prompts Gemini 3.6 Flash (Mode 2), generates valid `AbstractBlock` DTOs, saves to PostgreSQL, and broadcasts WebSocket canvas updates.
3. **Memory & Goal Tracking Agent:** Manages session context and goal checklists inside Redis `opsForHash()` (`Goal 1: VERIFIED`, `Goal 2: PENDING`).
4. **Billing Agent (Credit Metering):** Monitors credit balances and client VAD activity. If a candidate is silent for 45 seconds, it triggers a reminder prompt or pauses streaming to save tokens.
5. **Cache Agent (Redis Pre-fetcher):** Pre-loads workspace settings, BYOK keys, and `FormAgentConfig` into Redis RAM (~5ms) on socket handshake.
6. **Evaluation & Analytics Agent:** Fired asynchronously after session disconnect. Takes the full candidate transcript, prompts Gemini 3.6 Flash, computes candidate match scores (e.g. 92/100), generates a 1-page summary, and saves the final `Submission` record.

---

## 2. Real-World End-to-End Scenarios

### PHASE A: Form Builder (John) Builds a "Senior Java Engineer Interview Form" in Mode 4

1. **Session Handshake:**
   * John connects via Mode 4 Co-Builder (`/ws/v1/voice?token=JWT&formId=form_789`).
   * **Cache Agent:** Pre-fetches workspace settings, BYOK keys, and `FormAgentConfig` into Redis RAM (~5ms).
   * **Billing Agent:** Validates John's workspace credit balance ($50.00) and starts meter.

2. **John Speaks Instructions:**
   * John says out loud: *"Create a technical interview form for a Senior Java Developer with Spring Boot and PostgreSQL questions."*

3. **Multi-Agent Co-Builder Execution:**
   * **Gemini 3.1 Live (Voice Engine):** Responds immediately out loud (~300ms): *"Sure John! Setting up the Senior Java Developer interview form now."*
   * **Gemini 3.1 Live** fires a `modifyFormLayout` tool call.
   * **GeminiLiveVoiceAdapter** catches `toolCall` and dispatches `FormLayoutModificationEvent` via Spring's `ApplicationEventPublisher`.
   * **Guardrail Agent:** Validates intent string against prompt injection rules.
   * **LayoutAgent (`@Async @EventListener`):** Catches event, prompts **Gemini 3.6 Flash (Mode 2)**, generates valid `AbstractBlock` DTOs, saves to PostgreSQL, and broadcasts a WebSocket canvas update to render the live preview on John's screen!

4. **Session Termination:**
   * John says: *"Looks perfect, publish it!"*
   * **Billing Agent:** Metering stops (2.5 minutes used = $0.0675 deducted). Session closes cleanly.

---

### PHASE B: Form Filler (Sarah) Takes the AI Recruiter Voice Interview in Mode 4

1. **Candidate Connects:**
   * Sarah clicks the link to take the interview. Her browser opens Mode 4 voice stream.
   * **Cache Agent:** Pre-loads John's custom system prompt, target goals, and voice selection (`Puck`) into Redis.

2. **Real-Time Recruiter Interview:**
   * **AI Recruiter:** Speaks out loud: *"Hi Sarah! Welcome. Can you describe your experience with Spring Boot microservices?"*
   * **Sarah Speaks:** *"I built microservices at my last company using Spring Cloud and Redis."*

3. **In-Session Agent Execution:**
   * **Guardrail Agent:** Runs microsecond cosine similarity checks using **pgvector** embeddings on Sarah's audio transcript to detect prompt injections or toxic language. Passes cleanly.
   * **Memory & Goal Tracking Agent:** Evaluates Sarah's transcript against the goal checklist in Redis `opsForHash()`:
     * `Goal 1 (Spring Boot Experience): VERIFIED`
     * `Goal 2 (PostgreSQL Indexing): PENDING`
   * **Memory Agent** signals the AI Recruiter to ask about Goal 2 next!
   * **Billing Agent:** Monitors client VAD. If Sarah stops speaking for 45 seconds, it fires a reminder prompt or safely pauses streaming to save tokens.

4. **Post-Interview Async Processing:**
   * Sarah finishes and disconnects.
   * **Evaluation & Analytics Agent:** Fired asynchronously via `@Async`. It takes Sarah's full audio transcript, sends it to **Gemini 3.6 Flash**, computes a 92/100 candidate match score, generates a 1-page summary, and saves the final `Submission` record to PostgreSQL for John to review!
