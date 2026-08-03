package com.reForm.backend.ai.port;

import java.util.List;
import java.util.Map;

// Decouples AiChatService from a specific vendor's text-generation wire protocol — mirrors
// IAiVoiceAdapter's exact shape, the same decoupling voice mode already has for its own vendor
// call. Formalized as a port from the start (unlike FormChatPromptBuilder), since multi-vendor
// scalability is an explicit priority for this piece.
public interface IAiChatClient {

    /**
     * Sends a schema-constrained text-generation request and returns Gemini's response text
     * (the JSON body AiResponseParser expects) — never the full vendor response envelope.
     *
     * @param systemInstruction compiled by FormChatPromptBuilder / SessionContextService
     * @param contents          conversation history + new message, from
     *                          FormChatPromptBuilder.buildConversationContents
     * @param responseSchema    from BlockSchemaGenerator, constrains the model's output shape
     */
    String generateFormResponse(String systemInstruction, List<Map<String, Object>> contents,
                                 Map<String, Object> responseSchema);
}
