package com.reForm.backend.ai.port;

import org.springframework.web.socket.WebSocketSession;

/**
 * AI VOICE ADAPTER INTERFACE (Bridge / Adapter Pattern Port)
 * 
 * Decouples the Spring Boot WebSocket connection handler from specific 
 * real-time AI vendor streaming API protocols (such as Gemini Live or OpenAI Realtime).
 */
public interface IAiVoiceAdapter {

    /**
     * Called when the candidate's connection is opened. Initializes the connection to the AI provider.
     * 
     * @param userId The ID of the authenticated user
     * @param clientSession The candidate's active WebSocket session
     */
    void startSession(String userId, WebSocketSession clientSession);

    /**
     * Forwards raw binary audio frames (PCM 16-bit) received from the candidate's mic to the AI provider.
     * 
     * @param audioData The raw binary audio payload
     */
    void sendClientAudio(byte[] audioData);

    /**
     * Called when the client terminates the connection or a network drop occurs.
     * Closes the active session with the AI provider.
     */
    void closeSession();
}
