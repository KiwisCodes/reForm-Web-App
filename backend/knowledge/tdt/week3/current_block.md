# Week 3 Progression Snapshot & Open Design Challenge

This document summarizes our technical progress for **Week 3 (AI Form Builder & Chat-to-Build)** and highlights the structural challenge we are currently navigating regarding unmapped LLM attributes.

---

## 1. Week 3 Progress Overview

Our primary focus for Week 3 has been establishing the **AI Form Builder (Chat-to-Build)** data boundaries [1]. We designed a pipeline that allows users to build forms via conversational prompts without exposing our physical database entities directly to the unpredictable outputs of the Large Language Model (LLM) [1].

### Milestones Met So Far:
1.  **Polymorphic DTO Boundary Design:** We created an intermediate API contract layer using Java records to cleanly separate the LLM's raw JSON output from our persistent JPA database entities [1]:
    *   **`BlockCategory` (Enum):** Distinguishes between `STATIC` and `CONVERSATIONAL` processing paths [1].
    *   **`AiBlockDto` (Interface):** The polymorphic base interface annotated with Jackson's `@JsonTypeInfo(property = "category")` and `@JsonSubTypes` to dynamically dispatch incoming payloads [1].
    *   **`AiStaticBlockDto` (Record):** A flat, simplified contract holding properties relevant to standard static fields (label, staticType, options, maxScale) [1].
    *   **`AiConversationalBlockDto` (Record):** A flat contract containing parameters relevant to AI-driven voice/visual interviews (prompt, persona, maxQuestions) [1].
2.  **Preservation of Your Subclass Schema:** We mapped out how this DTO layer integrates with your existing polymorphic `AbstractBlock` subclass architecture (e.g., `ChoiceStaticBlock`, `StarRatingStaticBlock`, `OpinionScaleStaticBlock`) [1].
3.  **Intelligent Defaults Pattern:** We established that the LLM only needs to generate 20% of the core content schema (the prompt, the labels, the basic choices) [1]. The remaining 80% of technical execution details (default regexes, file size limits, complex scale labels) are applied natively by your Java class constructors during the instantiation phase [1].

---

## 2. The Current Core Problem: The "Stretching DTO" Trap

As we prepare to write the translation logic in `BlockFactory`, we have run into a major architectural design question: **How do we handle unmapped or expanding LLM attributes without constantly hardcoding new fields into `AiStaticBlockDto` [1]?**

### The Scenario:
*   We asked Gemini to generate a `ChoiceStaticBlock` [1].
*   Our compiled `AiStaticBlockDto` only contains standard flat fields: `staticType`, `label`, `isRequired`, `options`, and `maxScale` [1].
*   However, during a conversation, the user might ask: *"Make it a dropdown list instead of radio buttons"* or *"Let me upload multiple files"*.
*   If the LLM responds by adding new fields directly to the JSON payload—such as `"selectionType": "DROPDOWN"` or `"multipleFile": true` [1]—our current `AiStaticBlockDto` record cannot capture them because those fields do not exist in the record's Java signature [1].

### The Danger of Hardcoding:
*   If we simply add a new field to `AiStaticBlockDto` every time the LLM wants to configure a specific property, our DTO will bloat to dozens of fields.
*   This makes the system highly brittle. Any change to your database classes forces you to edit your DTOs and recompile the application, violating the open-closed principle.

---

## 3. Potential "Smarter Ways" to Resolve the Challenge

To prevent the hardcoding trap, we are evaluating three standard industry solutions to handle unmapped AI properties cleanly:

### Solution A: Strict API JSON Schema Enforcements (Model Level)
We configure Google's Gemini API call using its native `responseSchema` or structured output capability. 
*   **How it works:** We pass our exact `AiStaticBlockDto` structure as a JSON Schema schema directly to the Gemini API during the WebClient handshake [1]. 
*   **The Result:** The model is physically constrained. It is incapable of outputing unauthorized fields like `selectionType` [1]. It must translate user requests (like "dropdown") into structures we do support, or we map those intents upstream in the system prompt [1].

### Solution B: Dynamic Properties Map (`@JsonAnySetter`)
We can add a generic, catch-all map directly inside our DTO.
*   **How it works:** We modify `AiStaticBlockDto` to contain a fallback map:
    ```java
    Map<String, Object> additionalProperties
    ```
    We annotate this map with Jackson's `@JsonAnySetter`.
*   **The Result:** If Gemini generates standard properties, they map to your record fields [1]. If it generates unexpected properties (like `"selectionType": "DROPDOWN"`), Jackson automatically catches and drops them into the `additionalProperties` map without throwing an exception or failing compile boundaries. `BlockFactory` can then safely inspect the map [1].

### Solution C: Natural Language Mapping via System Instructions
We let Gemini output only core properties, but we instruct it inside `IPromptBuilder` [1] to translate advanced design requests into our standard types (for example, instructing Gemini: *"If the user wants a multi-select field, do not output 'allowMultiSelect: true', instead output 'staticType: CHECKBOX'"*).

---

## 4. Next Session Handover Plan
When you return, we will immediately tackle this design challenge together. We will pick a path to resolve how the DTO manages unexpected attributes, finalize your `BlockFactory` mapping logic [1], and then write your `IPromptBuilder` system prompts [1]!