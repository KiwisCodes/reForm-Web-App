# AI Function Calling Tool Routing: Strategy Pattern Refactoring

**Document Version:** 1.0  
**Location:** `backend/knowledge/pth/week4/07_tool_call_strategy_pattern_refactoring.md`  
**Target System:** reForm Platform (`com.reForm.backend.ai.tool`)  

---

## 1. Executive Summary: Problems to Solutions

### Bad Initial State (Monolithic `if-else` Chain)
Initially, when tool calls were received from Google Gemini Live over WebSockets, `GeminiLiveVoiceAdapter.java` handled execution inside a monolithic `processFunctionCall()` method using hardcoded `if-else if-else` statements:

```java
// ❌ BEFORE (Monolithic Code Smell inside GeminiLiveVoiceAdapter.java)
private Map<String, Object> processFunctionCall(WebSocketSession clientSession, JsonNode functionCall, String callId, String functionName) {
    if ("modifyFormLayout".equals(functionName)) {
        // ... layout modification logic ...
    } else if ("searchUserDocument".equals(functionName)) {
        // ... document search logic ...
    } else if ("endSession".equals(functionName)) {
        // ... session teardown logic ...
    } else {
        return Map.of("id", callId, "name", functionName, "response", Map.of("result", Map.of("status", "SUCCESS")));
    }
}
```

### Why It Was Bad (Architectural Code Smells)

1. **Violation of the Open-Closed Principle (OCP)**:
   - Every time a developer wanted to add a new tool (e.g. `saveFieldResponse`, `publishForm`, `evaluateResponse`), they had to open and edit `GeminiLiveVoiceAdapter.java`. Software modules should be *open for extension, but closed for modification*.
2. **Violation of the Single Responsibility Principle (SRP)**:
   - `GeminiLiveVoiceAdapter` had two completely unrelated responsibilities:
     1. Managing low-latency WebSocket audio proxying between browser and Google.
     2. Housing business logic for 18+ individual domain tools.
3. **Monolithic Testing Bottleneck**:
   - To write a unit test for a single tool (e.g., `endSession`), developers had to instantiate and mock the entire WebSocket infrastructure.
4. **Team Git Merge Collisions**:
   - In a multi-developer team, developers building different tools would simultaneously modify `GeminiLiveVoiceAdapter.java`, creating constant Git merge conflicts.

---

### New Refactored State (Strategy Pattern + Spring Auto-Registration)

We refactored tool execution into a decoupled, domain-driven **Strategy Pattern** architecture under `com.reForm.backend.ai.tool`:

```java
// ✅ AFTER (Clean 1-line Delegation inside GeminiLiveVoiceAdapter.java)
private Map<String, Object> processFunctionCall(WebSocketSession clientSession, JsonNode functionCall, String callId, String functionName) {
    return toolCallRegistry.executeTool(clientSession, functionCall, callId, functionName);
}
```

---

## 2. Design Patterns & Engineering Concepts Explained

### 1. Strategy Pattern (`IToolCallHandler`)
- **Concept**: Defines a family of algorithms, encapsulates each one, and makes them interchangeable.
- **Application in reForm**: `IToolCallHandler` is the strategy interface. Each concrete tool class (`EndSessionToolHandler`, `ModifyFormLayoutToolHandler`, `SearchUserDocumentToolHandler`) encapsulates the execution algorithm for one specific tool.

```java
public interface IToolCallHandler {
    String getFunctionName();
    Map<String, Object> execute(WebSocketSession clientSession, JsonNode functionCall, String callId);
}
```

### 2. Registry / Command Router Pattern (`ToolCallRegistry`)
- **Concept**: Centralizes lookup and routing of strategy implementations via an $O(1)$ Hash Map lookup instead of runtime `if-else` conditionals.
- **Application in reForm**: `ToolCallRegistry` maintains a `Map<String, IToolCallHandler>` mapping tool function names to their corresponding handler beans.

### 3. Inversion of Control (IoC) & Spring Component Scanning
- **Concept**: The framework discovers and wires components automatically at startup rather than requiring manual registration.
- **Application in reForm**: Spring's dependency injection automatically scans all `@Component` beans implementing `IToolCallHandler` and passes them as a `List<IToolCallHandler>` to `ToolCallRegistry`'s constructor:

```java
@Service
public class ToolCallRegistry {
    private final Map<String, IToolCallHandler> handlerMap;

    public ToolCallRegistry(List<IToolCallHandler> handlers) {
        this.handlerMap = handlers.stream()
            .collect(Collectors.toMap(IToolCallHandler::getFunctionName, Function.identity()));
    }
}
```

---

## 3. Visual Architecture Diagram

```mermaid
graph TD
    Gemini[Google Gemini Live API] -->|toolCall JSON Frame| Adapter[GeminiLiveVoiceAdapter]
    Adapter -->|1-line Delegate Call| Registry[ToolCallRegistry]
    
    subgraph Tool Strategy Handlers (com.reForm.backend.ai.tool.handler.*)
        Registry -->|Lookup 'endSession'| H1[EndSessionToolHandler]
        Registry -->|Lookup 'modifyFormLayout'| H2[ModifyFormLayoutToolHandler]
        Registry -->|Lookup 'searchUserDocument'| H3[SearchUserDocumentToolHandler]
        Registry -->|Lookup Unimplemented Tool| Fallback[Generic Success Fallback]
    end

    H1 -->|SESSION_ENDED + Teardown| Browser[Client Browser]
    H2 -->|FormLayoutModificationEvent| EventBus[Spring Event Bus]
    H3 -->|RAG Passages| VectorDB[pgvector DB]
```

---

## 4. Package Directory & Component Organization

```text
com.reForm.backend.ai.tool/
├── port/
│   └── IToolCallHandler.java                 <-- Strategy Port Interface
├── registry/
│   └── ToolCallRegistry.java                <-- Spring Registry Service
└── handler/
    ├── universal/
    │   ├── EndSessionToolHandler.java       <-- [Universal Tool]
    │   └── SearchUserDocumentToolHandler.java<-- [Universal Tool]
    ├── builder/
    │   ├── ModifyFormLayoutToolHandler.java <-- [Form Builder Tool]
    │   ├── ConfigureFillerPersonaToolHandler.java
    │   ├── PublishFormToolHandler.java
    │   └── GenerateContentFromDocToolHandler.java
    ├── filler/
    │   ├── SaveFieldResponseToolHandler.java
    │   ├── EvaluateResponseToolHandler.java
    │   ├── SkipQuestionToolHandler.java
    │   ├── LookupFormProgressToolHandler.java
    │   └── FlagForHumanReviewToolHandler.java
    ├── file/
    │   ├── RequestFileUploadToolHandler.java
    │   ├── AnalyzeUploadedFileToolHandler.java
    │   └── ExtractStructuredDataToolHandler.java
    ├── audio/
    │   ├── SaveAudioRecordingToolHandler.java
    │   └── SaveSessionTranscriptToolHandler.java
    └── ui/
        ├── RenderDynamicUIToolHandler.java
        └── SendNotificationToolHandler.java
```

---

## 5. Architectural Benefits Summary

| Metric | Before (Monolithic `if-else`) | After (Strategy Pattern + Registry) |
|:---|:---|:---|
| **Extensibility** | Edit `GeminiLiveVoiceAdapter.java` for every tool | Add 1 standalone `@Component` class |
| **Coupling** | High (Adapter bound to all 18 tools' logic) | Zero (Adapter bound only to `ToolCallRegistry`) |
| **Testability** | Hard (Must mock full WebSocket streaming context) | Easy (Test each tool handler in isolated unit tests) |
| **Git Conflicts** | High risk across team members | Zero risk (Each developer works in isolated handler files) |
| **SOLID Score** | Violates OCP & SRP | Adheres strictly to OCP, SRP, and DIP |
