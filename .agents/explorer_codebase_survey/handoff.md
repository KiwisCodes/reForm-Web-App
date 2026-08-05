# HANDOFF REPORT — explorer_codebase_survey

## 1. Observation
* **Java Source Directory**: `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/`
* **Target Classes Inspected**:
  * `LayoutAgent.java` (`com.reForm.backend.ai.agent.LayoutAgent`): `@Async @EventListener @Transactional public void handleLayoutModification(FormLayoutModificationEvent event)` (lines 49-94).
  * `IToolCallHandler.java` (`com.reForm.backend.ai.tool.port.IToolCallHandler`): Interface defining `String getFunctionName()` (line 22) and `Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId)` (line 32).
  * `ToolCallRegistry.java` (`com.reForm.backend.ai.tool.registry.ToolCallRegistry`): Spring `@Service` collecting `List<IToolCallHandler>` into `Map<String, IToolCallHandler>` via `Collectors.toMap(IToolCallHandler::getFunctionName, Function.identity())` (lines 33-34).
* **18 Tool Handler Strategy Classes Inspected**:
  1. `SaveAudioRecordingToolHandler` (`com.reForm.backend.ai.tool.handler.audio.SaveAudioRecordingToolHandler`): function `saveAudioRecording`
  2. `SaveSessionTranscriptToolHandler` (`com.reForm.backend.ai.tool.handler.audio.SaveSessionTranscriptToolHandler`): function `saveSessionTranscript`
  3. `ConfigureFillerPersonaToolHandler` (`com.reForm.backend.ai.tool.handler.builder.ConfigureFillerPersonaToolHandler`): function `configureFillerPersona`
  4. `GenerateContentFromDocToolHandler` (`com.reForm.backend.ai.tool.handler.builder.GenerateContentFromDocToolHandler`): function `generateContentFromDocument`
  5. `ModifyFormLayoutToolHandler` (`com.reForm.backend.ai.tool.handler.builder.ModifyFormLayoutToolHandler`): function `modifyFormLayout`
  6. `PublishFormToolHandler` (`com.reForm.backend.ai.tool.handler.builder.PublishFormToolHandler`): function `publishForm`
  7. `AnalyzeUploadedFileToolHandler` (`com.reForm.backend.ai.tool.handler.file.AnalyzeUploadedFileToolHandler`): function `analyzeUploadedFile`
  8. `ExtractStructuredDataToolHandler` (`com.reForm.backend.ai.tool.handler.file.ExtractStructuredDataToolHandler`): function `extractStructuredData`
  9. `RequestFileUploadToolHandler` (`com.reForm.backend.ai.tool.handler.file.RequestFileUploadToolHandler`): function `requestFileUpload`
  10. `EvaluateResponseToolHandler` (`com.reForm.backend.ai.tool.handler.filler.EvaluateResponseToolHandler`): function `evaluateResponse`
  11. `FlagForHumanReviewToolHandler` (`com.reForm.backend.ai.tool.handler.filler.FlagForHumanReviewToolHandler`): function `flagForHumanReview`
  12. `LookupFormProgressToolHandler` (`com.reForm.backend.ai.tool.handler.filler.LookupFormProgressToolHandler`): function `lookupFormProgress`
  13. `SaveFieldResponseToolHandler` (`com.reForm.backend.ai.tool.handler.filler.SaveFieldResponseToolHandler`): function `saveFieldResponse`
  14. `SkipQuestionToolHandler` (`com.reForm.backend.ai.tool.handler.filler.SkipQuestionToolHandler`): function `skipQuestion`
  15. `RenderDynamicUIToolHandler` (`com.reForm.backend.ai.tool.handler.ui.RenderDynamicUIToolHandler`): function `renderDynamicUI`
  16. `SendNotificationToolHandler` (`com.reForm.backend.ai.tool.handler.ui.SendNotificationToolHandler`): function `sendNotification`
  17. `EndSessionToolHandler` (`com.reForm.backend.ai.tool.handler.universal.EndSessionToolHandler`): function `endSession`
  18. `SearchUserDocumentToolHandler` (`com.reForm.backend.ai.tool.handler.universal.SearchUserDocumentToolHandler`): function `searchUserDocument`
* **Entity & Repository Classes Inspected**:
  * `FormAiAgentProfile.java` (`com.reForm.backend.form.entity.FormAiAgentProfile`): `@Entity @Table(name = "form_ai_agent_profiles")`, fields: `form`, `modelKey`, `systemPromptTemplate`, `voiceName`, `temperature`, `byokApiKeyEncrypted`.
  * `FormAiAgentProfileRepository.java` (`com.reForm.backend.form.repository.FormAiAgentProfileRepository`): `Optional<FormAiAgentProfile> findByFormId(UUID formId)`.
* **Services & WebSockets Inspected**:
  * `SessionContextService.java` (`com.reForm.backend.ai.service.SessionContextService`): `buildSetupContext`, `compileSystemInstruction`, `buildToolDeclarations`.
  * `VoiceSyncWSHandler.java` (`com.reForm.backend.ai.websocket.VoiceSyncWSHandler`): `afterConnectionEstablished`, `handleBinaryMessage`, `handleTextMessage`, `afterConnectionClosed`.
  * `WebSocketConfig.java` (`com.reForm.backend.ai.config.WebSocketConfig`): `/ws/v1/voice` endpoint mapping with 10MB text/binary message buffer.

---

## 2. Logic Chain
1. **Observation**: `IToolCallHandler` defines `getFunctionName()` and `execute(...)`. `ToolCallRegistry` receives `List<IToolCallHandler>` via constructor dependency injection and constructs a `Map<String, IToolCallHandler>`.
2. **Logic Step**: Spring Boot automatically discovers all 18 `@Component` handler beans at application startup and passes them to `ToolCallRegistry`. When Google Gemini Live or STT/LLM sends a `toolCall` JSON frame over WebSocket, `ToolCallRegistry.executeTool()` performs $O(1)$ lookup and dispatches to the matching strategy bean.
3. **Observation**: `ModifyFormLayoutToolHandler` publishes `FormLayoutModificationEvent` via Spring's `ApplicationEventPublisher`. `LayoutAgent` listens with `@Async` and `@EventListener`.
4. **Logic Step**: Layout modifications triggered via voice/chat are decoupled from the real-time WebSocket event loop. `LayoutAgent` processes database mutations asynchronously on a background thread pool, modifying the form's `blocks` JSONB array and persisting via `FormRepository`.
5. **Observation**: `SessionContextService.buildToolDeclarations()` gates the 18 tools dynamically based on user security `Role` (`FORM_BUILDER` vs `FORM_FILLER`), document availability (`hasDocuments`), and audio capabilities (`hasAudioCapability`).
6. **Logic Step**: The platform enforces strict role-based tool gating. Form fillers cannot invoke layout mutation tools (`modifyFormLayout`, `configureFillerPersona`), while form builders receive canvas editing tools.

---

## 3. Caveats
* **No Caveats**: The entire codebase in `backend/src/main/java` was surveyed and verified directly against source files. All 18 tool handlers were located, read, and cataloged.

---

## 4. Conclusion
The existing Java codebase in `backend/` provides a production-grade implementation of the built agentic components (`LayoutAgent`, `FormAiAgentProfile`, `ToolCallRegistry`, and all 18 `IToolCallHandler` strategies). The architecture adheres strictly to SOLID design principles, utilizing Strategy, Registry, Factory, Observer, and Adapter patterns to achieve zero-coupling extension for new AI agents and tools.

---

## 5. Verification Method
1. **Inspect Analysis Report**: View `/Users/apple/Coding-projects/reForm-Web-App/.agents/explorer_codebase_survey/analysis.md`.
2. **Inspect Java Interfaces & Classes**:
   * `view_file` on `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/tool/port/IToolCallHandler.java`
   * `view_file` on `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/tool/registry/ToolCallRegistry.java`
   * `view_file` on `/Users/apple/Coding-projects/reForm-Web-App/backend/src/main/java/com/reForm/backend/ai/agent/LayoutAgent.java`
3. **Verify Tool Count**: Confirm 18 strategy files in `backend/src/main/java/com/reForm/backend/ai/tool/handler/` across subpackages `audio` (2), `builder` (4), `file` (3), `filler` (5), `ui` (2), and `universal` (2).
