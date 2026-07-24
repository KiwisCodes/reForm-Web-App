# 17. Function Calling (`toolCall`) Mechanics
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Who Invented `toolCall`? Is it Custom or Standard?

Function Calling (`toolCall`) was **invented by OpenAI AI researchers (June 2023)** and adopted as an industry standard by **Google Gemini, Anthropic Claude, and Mistral**.

* **Is it custom code?** You do **NOT** write the neural network engine that decides when to call a tool. Google Gemini's AI model does that automatically!
* **What you do as a developer:** You declare a JSON Schema description of your tool in the connection setup payload. When the AI model determines that an action is required (e.g. John asks to change layout), the AI stops outputting audio/text and outputs a structured `toolCall` JSON frame instead.

---

## 2. The 4-Step Concrete Code & Payload Execution Flow

---

### Step 1: Tool Declaration (JSON Schema in Setup Payload)
In `SessionContextService`, we inject the tool schema into Gemini's setup payload:

```json
{
  "tools": [
    {
      "functionDeclarations": [
        {
          "name": "modifyFormLayout",
          "description": "Triggers structural modifications to the form layout canvas.",
          "parameters": {
            "type": "OBJECT",
            "properties": {
              "userIntent": {
                "type": "STRING",
                "description": "The extracted intent of the user (e.g., 'ADD_CONTACT_SECTION', 'DELETE_BLOCK_2')"
              },
              "targetBlockId": {
                "type": "STRING",
                "description": "Optional block UUID to modify or remove."
              }
            },
            "required": ["userIntent"]
          }
        }
      ]
    }
  ]
}
```

---

### Step 2: Inbound `toolCall` Frame Emission from LLM
When John says *"Add a contact section with email and phone"*, the AI engine returns a `toolCall` JSON frame instead of text:

```json
{
  "toolCall": {
    "functionCalls": [
      {
        "id": "call_1098abc",
        "name": "modifyFormLayout",
        "args": {
          "userIntent": "Add contact section containing Full Name, Email, and Phone fields",
          "targetBlockId": null
        }
      }
    ]
  }
}
```

---

### Step 3: Backend Handler Catching & Event Dispatching
In `GeminiLiveVoiceAdapter` (or `CascadedVoiceAdapter`), we parse the `toolCall` payload and dispatch a Spring application event:

```java
// Inside GeminiLiveVoiceAdapter.java
public void handleTextMessage(WebSocketSession session, TextMessage message) {
    JsonNode rootNode = objectMapper.readTree(message.getPayload());
    
    if (rootNode.has("toolCall")) {
        JsonNode functionCall = rootNode.get("toolCall").get("functionCalls").get(0);
        String functionName = functionCall.get("name").asText();
        String callId = functionCall.get("id").asText();
        
        if ("modifyFormLayout".equals(functionName)) {
            String userIntent = functionCall.get("args").get("userIntent").asText();
            String formId = (String) session.getAttributes().get("formId");

            // 1. Dispatch Spring event to LayoutAgent asynchronously
            eventPublisher.publishEvent(new FormLayoutModificationEvent(formId, userIntent));

            // 2. Return toolResponse frame back to Google WSS to acknowledge tool execution
            sendToolResponseAck(session, callId, functionName, Map.of("status", "SUCCESS"));
        }
    }
}
```

---

### Step 4: `toolResponse` Acknowledgment Frame Sent Back to LLM
To inform the LLM that the backend has successfully received and initiated the tool action, the adapter sends a `toolResponse` frame back:

```json
{
  "toolResponse": {
    "functionResponses": [
      {
        "response": { "output": { "status": "SUCCESS", "message": "Layout modification queued." } },
        "id": "call_1098abc"
      }
    ]
  }
}
```
