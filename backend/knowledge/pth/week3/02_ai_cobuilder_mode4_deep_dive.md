# AI Co-Builder Mode 4 (Phase A) Architecture Deep Dive
**Document Version:** 2.0  
**Location:** `backend/knowledge/pth/week3/02_ai_cobuilder_mode4_deep_dive.md`  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  

---

## 1. Concept: Verbal Form Co-Building

In **Phase A**, the Form Builder (John) builds or modifies a form using voice interaction. The AI acts as a **Form Architect Co-Pilot**.

```text
┌──────────────────────────┐
│ John (Form Builder)      │ ──► Speaks: "Add a contact section with full name and email."
└──────────────────────────┘
             │
             ▼
┌──────────────────────────┐
│ Gemini 3.1 Live API      │ ──► Responds out loud (~300ms): "Sure John! Adding that now."
│ (Voice Engine)           │     Emits toolCall JSON frame: modifyFormLayout(...)
└──────────────────────────┘
             │
             ▼
┌──────────────────────────┐
│ GeminiLiveVoiceAdapter   │ ──► Parses toolCall frame
└──────────────────────────┘
             │
             ▼
┌──────────────────────────┐
│ Spring Event Bus         │ ──► Publishes FormLayoutModificationEvent
└──────────────────────────┘
             │
             ▼
┌──────────────────────────┐
│ LayoutAgent              │ ──► Sub-Agent executes Mode 2 (Gemini 3.6 Flash)
│ (@Async Event Listener)  │     Generates valid AbstractBlock JSON (Static / Conversational)
│                          │     Saves to PostgreSQL & broadcasts WebSocket Canvas update
└──────────────────────────┘
```

---

## 2. Under the Hood: Step-by-Step Component Execution

### Step 1: Voice Setup Handshake
1. John connects to `/ws/v1/voice?token=JWT&formId=form_123&role=FORM_BUILDER`.
2. `SessionContextService` compiles the **Form Architect Persona**:
   ```text
   You are an expert AI Form Architect. Help the user design form layouts verbally.
   When the user requests structural modifications or new fields, call the 'modifyFormLayout' tool.
   ```
3. Function declaration schema `modifyFormLayout` is registered in `tools`.

### Step 2: Speech & Intent Extraction
1. John says: *"I want to create a customer service feedback form with an AI interview block."*
2. Gemini 3.1 Live processes speech natively and decides to invoke `modifyFormLayout`.

### Step 3: Tool Execution & Event Dispatching
Gemini Live emits:
```json
{
  "toolCall": {
    "functionCalls": [
      {
        "id": "call_abc123",
        "name": "modifyFormLayout",
        "args": {
          "userIntent": "ADD_CONVERSATIONAL_BLOCK",
          "label": "Customer Feedback Voice Interview",
          "persona": "Polite Customer Support Specialist",
          "prompt": "Evaluate ticket satisfaction, response speed, and recommendation NPS",
          "maxQuestions": 5
        }
      }
    ]
  }
}
```

`GeminiLiveVoiceAdapter` intercepts this frame:
1. Publishes `FormLayoutModificationEvent`.
2. Sends back `toolResponse` acknowledgment frame to Google WSS.

### Step 4: LayoutAgent Processing & Canvas Sync
1. `LayoutAgent` catches the event asynchronously via `@Async @EventListener`.
2. Uses **Gemini 3.6 Flash (Mode 2)** to format valid `ConversationalBlock` / `StaticBlock` objects.
3. Mutates `form.getBlocks()` in PostgreSQL.
4. Broadcasts a WebSocket message to John's frontend client (`/topic/form-canvas/{formId}`), causing the live preview canvas to render the new field instantly!
