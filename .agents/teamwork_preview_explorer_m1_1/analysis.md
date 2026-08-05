# Milestone 1: System Architecture & Event Foundation - Event Analysis Report

## Executive Summary
This document details the architectural investigation and exact specifications for the 6 core Spring `ApplicationEvent` classes required for the reForm multi-agent event bus (`com.reForm.backend.ai.event`). It also maps supporting domain entities (`AbstractBlock`) and details necessary modifications to existing mock/prototype code (`FormLayoutModificationEvent`, `ModifyFormLayoutToolHandler`, `LayoutAgent`).

---

## 1. Backend Structure Investigation

### 1.1 Existing Package Layout (`backend/src/main/java/com/reForm/backend/`)
- `ai/event/`: Holds Spring event models. Currently contains a single prototype record `FormLayoutModificationEvent.java`.
- `ai/agent/`: Spring `@Component` `@Async` `@EventListener` agents. Currently contains `LayoutAgent.java`.
- `ai/tool/handler/`: Tool handlers for voice/chat co-builder function calls. Currently contains tool handlers such as `ModifyFormLayoutToolHandler.java`.
- `form/entity/block/`: Contains `AbstractBlock.java` and block inheritance hierarchy (`StaticBlock`, `ConversationalBlock`, etc.).
- `core/config/`: Spring infrastructure configuration (`RedisConfig`, `SecurityConfig`, etc.).

---

## 2. Verification of Supporting Entities (`AbstractBlock`)

- **Class Path**: `backend/src/main/java/com/reForm/backend/form/entity/block/AbstractBlock.java`
- **Package**: `com.reForm.backend.form.entity.block`
- **Interface Implemented**: `IFormBlock` (`com.reForm.backend.form.entity.block.IFormBlock`)
- **Deserialization Annotation**: `@JsonDeserialize(using = AbstractBlockDeserializer.class)`
- **Lombok Annotations**: `@Getter`, `@Setter`
- **Fields**:
  - `private UUID id = UUID.randomUUID();`
  - `private String label;`
  - `private String description;`
  - `private boolean isRequired;`
  - `private Integer sortOrder;`
- **Subclasses**:
  - `ConversationalBlock` (`com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock`)
  - `StaticBlock` (`com.reForm.backend.form.entity.block.staticblock.StaticBlock`) - 11 concrete leaf implementations (e.g. `ShortTextStaticBlock`, `EmailStaticBlock`, `PhoneStaticBlock`, `ChoiceStaticBlock`, etc.)

---

## 3. Specifications for the 6 Required Spring ApplicationEvent Classes

All events reside in package `com.reForm.backend.ai.event`.
In Spring Boot / Java 21, Java `record` types provide immutable, thread-safe event payloads with zero-boilerplate canonical constructors and accessors out of the box.

Below are the exact specs for all 6 events:

### 3.1 `FormLayoutModificationEvent`
- **Purpose**: Published by Mode 2 chat or Mode 4 voice tool handlers when a user or AI requests layout changes on a form canvas. Consumed asynchronously by `LayoutAgent`.
- **Target File Path**: `backend/src/main/java/com/reForm/backend/ai/event/FormLayoutModificationEvent.java`
- **Package**: `com.reForm.backend.ai.event`
- **Imports**:
  - `com.reForm.backend.form.entity.block.AbstractBlock`
  - `java.util.List`
  - `java.util.UUID`
- **Signature / Fields**:
  ```java
  package com.reForm.backend.ai.event;

  import com.reForm.backend.form.entity.block.AbstractBlock;
  import java.util.List;
  import java.util.UUID;

  public record FormLayoutModificationEvent(
      UUID formId,
      String userIntent,
      List<AbstractBlock> targetBlocks,
      String sessionId
  ) {}
  ```
- **Accessors**: `formId()`, `userIntent()`, `targetBlocks()`, `sessionId()`
- **Constructors**: Canonical constructor `public FormLayoutModificationEvent(UUID formId, String userIntent, List<AbstractBlock> targetBlocks, String sessionId)`

### 3.2 `GuardrailValidationEvent`
- **Purpose**: Published during active Form Filler sessions (Modes 2-4) to validate input/output text for safety, toxicity, and prompt injection via `GuardrailAgent`.
- **Target File Path**: `backend/src/main/java/com/reForm/backend/ai/event/GuardrailValidationEvent.java`
- **Package**: `com.reForm.backend.ai.event`
- **Imports**:
  - `java.util.UUID`
- **Signature / Fields**:
  ```java
  package com.reForm.backend.ai.event;

  import java.util.UUID;

  public record GuardrailValidationEvent(
      UUID sessionId,
      String inputContent,
      String direction
  ) {}
  ```
- **Accessors**: `sessionId()`, `inputContent()`, `direction()`
- **Constructors**: Canonical constructor `public GuardrailValidationEvent(UUID sessionId, String inputContent, String direction)`

### 3.3 `SessionEndedEvent`
- **Purpose**: Published upon termination of a filler/builder session (completion, user disconnect, timeout, error) to trigger post-session evaluation (`EvaluationAgent`), transcript saving, and billing finalization.
- **Target File Path**: `backend/src/main/java/com/reForm/backend/ai/event/SessionEndedEvent.java`
- **Package**: `com.reForm.backend.ai.event`
- **Imports**:
  - `java.util.UUID`
- **Signature / Fields**:
  ```java
  package com.reForm.backend.ai.event;

  import java.util.UUID;

  public record SessionEndedEvent(
      UUID sessionId,
      UUID formId,
      UUID submissionId,
      String closeReason
  ) {}
  ```
- **Accessors**: `sessionId()`, `formId()`, `submissionId()`, `closeReason()`
- **Constructors**: Canonical constructor `public SessionEndedEvent(UUID sessionId, UUID formId, UUID submissionId, String closeReason)`

### 3.4 `BillingUsageEvent`
- **Purpose**: Published asynchronously when metered system resources (voice seconds, LLM tokens, vector searches) are consumed to update workspace credit balance via `BillingAgent`.
- **Target File Path**: `backend/src/main/java/com/reForm/backend/ai/event/BillingUsageEvent.java`
- **Package**: `com.reForm.backend.ai.event`
- **Imports**:
  - `java.util.UUID`
- **Signature / Fields**:
  ```java
  package com.reForm.backend.ai.event;

  import java.util.UUID;

  public record BillingUsageEvent(
      UUID workspaceId,
      UUID sessionId,
      String meterType,
      long unitsUsed
  ) {}
  ```
- **Accessors**: `workspaceId()`, `sessionId()`, `meterType()`, `unitsUsed()`
- **Constructors**: Canonical constructor `public BillingUsageEvent(UUID workspaceId, UUID sessionId, String meterType, long unitsUsed)`

### 3.5 `DocumentIngestionEvent`
- **Purpose**: Published when a user uploads documents (PDF, PNG, DOCX) for schema generation or form context background ingestion via `DocumentIngestionAgent`.
- **Target File Path**: `backend/src/main/java/com/reForm/backend/ai/event/DocumentIngestionEvent.java`
- **Package**: `com.reForm.backend.ai.event`
- **Imports**:
  - `java.util.UUID`
- **Signature / Fields**:
  ```java
  package com.reForm.backend.ai.event;

  import java.util.UUID;

  public record DocumentIngestionEvent(
      UUID documentId,
      byte[] content,
      String mimeType
  ) {}
  ```
- **Accessors**: `documentId()`, `content()`, `mimeType()`
- **Constructors**: Canonical constructor `public DocumentIngestionEvent(UUID documentId, byte[] content, String mimeType)`

### 3.6 `RagQueryEvent`
- **Purpose**: Published to execute asynchronous vector similarity search queries against form document embeddings via `RagSearchAgent`.
- **Target File Path**: `backend/src/main/java/com/reForm/backend/ai/event/RagQueryEvent.java`
- **Package**: `com.reForm.backend.ai.event`
- **Imports**:
  - `java.util.UUID`
- **Signature / Fields**:
  ```java
  package com.reForm.backend.ai.event;

  import java.util.UUID;

  public record RagQueryEvent(
      UUID formId,
      String queryText,
      int topK
  ) {}
  ```
- **Accessors**: `formId()`, `queryText()`, `topK()`
- **Constructors**: Canonical constructor `public RagQueryEvent(UUID formId, String queryText, int topK)`

---

## 4. Impact Analysis & Refactoring Guidance

1. **Refactoring `FormLayoutModificationEvent.java`**:
   - Prototype version in codebase used `(String formId, String userIntent, String targetBlockId)`.
   - Milestone 1 specification requires `(UUID formId, String userIntent, List<AbstractBlock> targetBlocks, String sessionId)`.
2. **Dependent Components to align**:
   - `ModifyFormLayoutToolHandler.java` (`backend/src/main/java/com/reForm/backend/ai/tool/handler/builder/ModifyFormLayoutToolHandler.java`):
     Update constructor call from `new FormLayoutModificationEvent(formId, userIntent, targetBlockId)` to convert `formId` string into `UUID`, pass `targetBlocks` list, and `sessionId`.
   - `LayoutAgent.java` (`backend/src/main/java/com/reForm/backend/ai/agent/LayoutAgent.java`):
     Update `handleLayoutModification(FormLayoutModificationEvent event)` to read `event.formId()` directly as UUID and process `event.targetBlocks()` when supplied.
