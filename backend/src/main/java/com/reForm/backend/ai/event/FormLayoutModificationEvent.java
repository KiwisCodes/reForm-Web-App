package com.reForm.backend.ai.event;

/**
 * FORM LAYOUT MODIFICATION EVENT (Step 1 - Immutable Event Record)
 * 
 * WHAT IS THIS CLASS?
 * This is an immutable POJO Event record (Java 21) used in Spring's Event-Driven Architecture.
 * 
 * WHY IS IT NEEDED?
 * Decouples the AI WebSocket streaming proxy (GeminiLiveVoiceAdapter) from the Form Builder business logic (LayoutAgent).
 * When Gemini Live issues a toolCall (modifyFormLayout), GeminiLiveVoiceAdapter creates an instance of this event
 * and publishes it onto Spring's ApplicationEventPublisher event bus.
 * 
 * WHO PUBLISHES IT?
 * GeminiLiveVoiceAdapter (inside handleTextMessage when "toolCall" is received).
 * 
 * WHO LISTENS TO IT?
 * LayoutAgent (annotated with @Async @EventListener). LayoutAgent receives this event in a background thread,
 * prompts Gemini 3.6 Flash (Mode 2) for valid JSON schema blocks, updates PostgreSQL Form.blocks, and pushes
 * a WebSocket canvas update frame to the user's UI screen.
 * 
 * FIELDS EXPLAINED:
 * @param formId The unique UUID string of the form being modified.
 * @param userIntent The extracted user intent (e.g. "ADD_CONTACT_SECTION", "DELETE_BLOCK_2").
 * @param targetBlockId Optional block UUID string if modifying or deleting a specific block (null if creating new section).
 */
public record FormLayoutModificationEvent(
    String formId,
    String userIntent,
    String targetBlockId
) {}