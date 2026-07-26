# 13. BYOK Security & User Model Selection Architecture
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. User Model Selection Flow

Form Builders (John) can select their preferred AI model from a dropdown menu in the frontend UI (`GEMINI_3_1_LIVE` vs `GEMINI_3_5_FLASH`).

### Execution Flow:
1. **Saved to Database:** Selection is saved in PostgreSQL in `form_agent_configs.model_key`.
2. **Passed in Handshake URL:** When a candidate connects, Next.js opens:
   `GET /ws/v1/voice?token=JWT_TOKEN&modelKey=GEMINI_3_1_LIVE`
3. **Extracted in Interceptor:** `JwtHandshakeInterceptor.beforeHandshake()` extracts `modelKey` and writes it to `attributes.put("modelKey", modelKey)`.
4. **Resolved via Strategy Pattern (Zero `if/else`):** `SessionContextService.buildSetupContext()` resolves the target strategy bean using stream matching:
   ```java
   private IAiModelProviderStrategy resolveModelStrategy(String modelKey) {
       return modelStrategies.stream()
               .filter(strategy -> strategy.supports(modelKey))
               .findFirst()
               .orElse(modelStrategies.get(0)); // Default fallback strategy
   }
   ```

---

## 2. Bring Your Own Key (BYOK) Security Architecture

Form Creators (John) can supply their own Google Gemini or OpenAI API keys so that reForm does not bill them per minute for voice API usage.

```text
  [ Client Connects ] ──► [ VoiceSyncWSHandler ]
                                   │
                                   ▼
                       [ GeminiLiveVoiceAdapter ]
                                   │
                    (1) Requests Resolved API Key & Setup Context
                                   │
                                   ▼
                       [ SessionContextService ]
                                   │
                    (2) Queries Workspace Settings in PostgreSQL
                                   │
             ┌─────────────────────┴─────────────────────┐
             ▼                                           ▼
  [ User BYOK API Key ]                       [ Platform Default Key ]
  (Decrypted from PostgreSQL)                 (From application.yml)
             │                                           │
             └─────────────────────┬─────────────────────┘
                                   │
                                   ▼
         Returns Resolved Key + Setup Payload to Adapter
                                   │
                                   ▼
  Opens Outbound WSS: wss://generativelanguage.googleapis.com/.../BidiGenerateContent?key=RESOLVED_KEY
```

### Encryption & Decryption Pipeline:
1. **Database Encryption:** API keys are sensitive credentials. They are stored in PostgreSQL under `workspaces.byok_api_key_encrypted` encrypted using **AES-256-GCM**.
2. **RAM Decryption Lifetime:** `SessionContextService.resolveApiKey(workspaceId)` decrypts the key in server RAM only for the split-second required to establish the outbound WebSocket connection to Google.
3. **Billing Integration:** If a workspace BYOK key is resolved, `BillingAgent` sets `chargeCredits = false` for voice minutes, but continues to log session minutes for user analytics.
