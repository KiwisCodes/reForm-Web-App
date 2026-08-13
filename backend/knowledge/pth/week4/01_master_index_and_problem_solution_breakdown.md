# Week 4 Refactoring Specification: Mode Selection, Factory Decoupling & DB Context Hydration

**Document Version:** 1.0  
**Target System:** reForm Core Monolith (`com.reForm.backend.ai` & Next.js Frontend)  
**Authors:** reForm Architecture Team  

---

## 1. Executive Summary & Problem-Solution Matrix

During Week 4, we completed a major architectural refactoring of reForm's real-time voice system. The goal was to transform a single-adapter prototype into an enterprise, multi-tiered voice platform supporting dynamic switching between **Mode 3 (Cascaded Voice)** and **Mode 4 (Native Live Voice)**, while reading custom AI agent personas directly from PostgreSQL.

### Problem-Solution Matrix

| # | Problem Encountered | Architectural Root Cause | Solution Implemented |
|---|:---|:---|:---|
| **1** | **Hardcoded Strategy Coupling** | `VoiceSyncWSHandler` directly injected `IAiVoiceAdapter` (specifically `GeminiLiveVoiceAdapter`), preventing Mode 3 fallback or multi-vendor selection. | Implemented **`AiVoiceAdapterFactory`** (Factory Pattern) to dynamically resolve strategy beans by `VoiceMode`. |
| **2** | **Redundant Map State & Leak Risk** | `VoiceSyncWSHandler` maintained a parallel `ConcurrentHashMap<String, IAiVoiceAdapter> sessionAdapters` map requiring manual cleanup. | Eliminated the second map entirely! Stored the resolved strategy directly in Spring's **`session.getAttributes().put("voiceAdapter", adapter)`**. |
| **3** | **Hardcoded Prompts in Java Code** | `SessionContextService` defaulted to hardcoded role prompts ("playful cat") for testing, ignoring PostgreSQL DB configs. | Enhanced `SessionContextService` to query **`FormAiAgentProfileRepository`** and apply custom prompt templates & `voiceName` from PostgreSQL. |
| **4** | **Missing Seed Data for DB Prompt Testing** | No DB initializer existed to seed test forms and `FormAiAgentProfile` entities on startup. | Created **`FormAiDataInitializer`** (`CommandLineRunner`) seeding candidate recruiter (`Kore`) and builder co-pilot (`Puck`) DB profiles. |
| **5** | **Platform Line Break Inconsistencies** | Prompt string concatenations used hardcoded `\n` literals, which vary across Windows/Linux/macOS. | Replaced all `\n` prompt concatenations with **`System.lineSeparator()`** (`nl`) for OS-agnostic formatting. |

---

## 2. Step-by-Step Architecture Deep-Dive

### Step 2.1: Enum & Strategy Decoupling (`VoiceMode.java` & `AiVoiceAdapterFactory.java`)
We created `VoiceMode` enum representing `MODE_1`, `MODE_2`, `MODE_3`, and `MODE_4`.

`AiVoiceAdapterFactory` leverages Spring's automatic `Map<String, IAiVoiceAdapter>` injection:
```java
@Component
@RequiredArgsConstructor
public class AiVoiceAdapterFactory {
    // Spring populates this map automatically with all IAiVoiceAdapter beans!
    private final Map<String, IAiVoiceAdapter> voiceAdapters;

    public IAiVoiceAdapter getAdapter(VoiceMode mode) {
        return switch (mode) {
            case MODE_4 -> voiceAdapters.get("geminiLiveVoiceAdapter");
            case MODE_3 -> voiceAdapters.get("cascadedVoiceAdapter");
        };
    }
}
```

---

### Step 2.2: Memory Model Refactoring (`VoiceSyncWSHandler.java`)
Instead of keeping a parallel map in RAM, `VoiceSyncWSHandler` attaches the strategy object to the WebSocket session attributes during `afterConnectionEstablished`:

```java
// 1. Resolve strategy from factory
IAiVoiceAdapter adapter = adapterFactory.getAdapter(mode);

// 2. Store inside Spring's session attributes (Zero extra maps, zero memory leaks!)
safeSession.getAttributes().put("voiceAdapter", adapter);
```
When messages arrive or when Tomcat closes the connection, the handler simply retrieves `(IAiVoiceAdapter) session.getAttributes().get("voiceAdapter")`.

---

### Step 2.3: Dynamic Prompt & Voice Hydration (`SessionContextService.java`)
`SessionContextService` constructs Google's official `BidiGenerateContentSetup` JSON frame dynamically:

```text
PostgreSQL `form_ai_agent_profiles` Table:
  ├── form_id (UUID)
  ├── model_key ("GEMINI_3_1_LIVE")
  ├── system_prompt_template ("You are an AI recruiter...")
  ├── voice_name ("Kore" / "Puck")
  └── temperature (0.7)
```

`SessionContextService` reads these columns and builds the setup JSON payload:
```json
{
  "setup": {
    "model": "models/gemini-3.1-flash-live-preview",
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "speechConfig": {
        "voiceConfig": {
          "prebuiltVoiceConfig": { "voiceName": "Kore" }
        }
      },
      "temperature": 0.7
    },
    "systemInstruction": {
      "parts": [{ "text": "[ROLE & PERSONA]\nYou are an AI Technical Recruiter..." }]
    }
  }
}
```

---

### Step 2.4: Database Seeding (`FormAiDataInitializer.java`)
To test database prompt loading without manual SQL inserts, `FormAiDataInitializer` runs on application startup and seeds two forms:

1. **Form Candidate Interviewer (`11111111-1111-1111-1111-111111111111`)**:
   - **Voice:** `Kore` (Calm Female)
   - **Prompt:** Cheerful, funny, welcoming AI recruiter interview persona.
2. **Form Builder Co-Pilot (`22222222-2222-2222-2222-222222222222`)**:
   - **Voice:** `Puck` (Energetic Male)
   - **Prompt:** Form Architect Co-Pilot with `modifyFormLayout` tool calling.
