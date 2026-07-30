# AI Co-Builder (Phase A) & Form Filler Multi-Agent Workflow (Phase B)
**Document Version:** 1.0  
**Location:** `backend/knowledge/pth/week3/02_ai_cobuilder_mode4_and_form_filler_multiagent.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Phase A: AI Co-Builder Mode 4 Architecture

In **Phase A**, the Form Builder (John) uses Mode 4 to verbally design forms. The AI acts as a **Form Architect Co-Pilot**.

```text
┌────────────────────────┐
│ John (Form Builder)    │ ──► Speaks: "I want a customer service feedback form."
└────────────────────────┘
            │
            ▼
┌────────────────────────┐
│ AI Form Architect      │ ──► Speaks: "Great! What key questions or goals do you want to ask?"
│ (Gemini 3.1 Live)      │     Fires modifyFormLayout tool call
└────────────────────────┘
            │
            ▼
┌────────────────────────┐
│ GeminiLiveVoiceAdapter │ ──► Catches toolCall {"name": "modifyFormLayout", "args": {...}}
└────────────────────────┘
            │
            ▼
┌────────────────────────┐
│ Spring Event Bus       │ ──► Publishes FormLayoutModificationEvent
└────────────────────────┘
            │
            ▼
┌────────────────────────┐
│ LayoutAgent            │ ──► Prompts Gemini 3.6 Flash (Mode 2) for JSON Schema
│ (@Async Listener)      │     Creates ConversationalBlock / StaticBlock
│                        │     Saves to PostgreSQL & pushes WebSocket update to Canvas UI
└────────────────────────┘
```

### Domain Data Model: `ConversationalBlock`
Form fields and conversational blocks are stored as JSONB objects in the `Form` entity:
```java
public class ConversationalBlock extends AbstractBlock {
    private String label;        // e.g. "Customer Support Feedback Interview"
    private String persona;      // e.g. "Empathetic Customer Success Specialist"
    private String prompt;       // e.g. "Evaluate: 1. Ticket resolution 2. Speed 3. NPS"
    private Integer maxQuestions;// e.g. 5
    private Mode mode;           // MODE_4
}
```

---

## 2. Phase B: Form Filler Multi-Agent Workflow Architecture

In **Phase B**, Candidate Sarah takes the AI voice interview. Underneath, 6 decoupled agents run in parallel:

```text
                  THE MULTI-AGENT INTERVIEW PIPELINE
                  
                           [ Candidate Sarah ]
                                    │
                                    ▼
                       [ VoiceSyncWSHandler (WSS) ]
                                    │
                                    ▼
                       [ GeminiLiveVoiceAdapter ]
                                    │
           ┌────────────────────────┼────────────────────────┐
           ▼                        ▼                        ▼
    [ Guardrail Agent ]    [ Memory/Goal Agent ]     [ Billing Agent ]
    (pgvector Moderation)  (Redis Goal Checklist)    (VAD Metering)
           │                        │                        │
           ▼                        ▼                        ▼
    [ RAG Vector Agent ]   [ Form Canvas Context ]   [ Evaluation Agent ]
    (Document Search)      (Live Answers)            (Post-Session Summary)
```

### Agent Roles & Responsibilities:

1. **Guardrail Agent (Content Moderation)**:
   - Uses `pgvector` cosine similarity embeddings to evaluate input audio/text against prompt injection and toxic language rules in ~2ms.
2. **Memory & Goal Tracking Agent**:
   - Manages session context and goal checklists inside Redis `opsForHash()` (`Goal 1: VERIFIED`, `Goal 2: PENDING`).
   - Signals Gemini Live to pivot to remaining unanswered goals.
3. **Billing Agent (Credit Metering)**:
   - Monitors VAD activity. If the candidate is silent for 45 seconds, it fires a reminder prompt or pauses streaming to save tokens.
4. **RAG Vector Search Agent**:
   - Retrieves relevant context from uploaded user documents when candidate asks questions during the interview.
5. **Evaluation Agent (Post-Session Summary)**:
   - Fired asynchronously via `@Async` upon socket disconnection. Takes full transcript, prompts Gemini 3.6 Flash, computes candidate match score (e.g. 92/100), generates 1-page summary, and saves `Submission` record.
