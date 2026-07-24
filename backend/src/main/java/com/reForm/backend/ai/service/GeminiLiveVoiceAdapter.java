package com.reForm.backend.ai.service;

import com.reForm.backend.ai.port.IAiVoiceAdapter;
import com.reForm.backend.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * GEMINI LIVE VOICE ADAPTER (Reusable Concrete Adapter)
 * 
 * Implements the IAiVoiceAdapter interface to connect candidates or creators to the 
 * Gemini Multimodal Live API via WebSockets.
 * 
 * DESIGN PRINCIPLE:
 * This class is completely generic regarding the AI's behavior. It manages the connection protocol
 * (base64 audio translation and WSS frames). The specific AI role behavior (Form Architect vs 
 * Interviewer) is dynamically injected during session initialization by the SessionContextService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiLiveVoiceAdapter implements IAiVoiceAdapter {

    private final SessionContextService sessionContextService;

    // TODO: Create references to client and target Google API WebSocket connections
    //       private WebSocketSession clientSession;
    //       private WebSocketSession geminiSession;

    @Override
    public void startSession(String userId, WebSocketSession clientSession) {
        // TODO: Store clientSession reference locally
        
        // 1. Resolve user role clearance
        Role role = (Role) clientSession.getAttributes().get("role");
        
        // 2. Fetch the dynamic setup configuration payload (defaulting to GEMINI_3_1_LIVE)
        Map<String, Object> setupContext = sessionContextService.buildSetupContext(userId, role, null);
        
        log.info("Opening Gemini Live connection for user: {} (Model: {})", userId, setupContext.get("model"));
        
        // TODO: Establish the WSS connection to Google's Generative Service endpoint
        // TODO: Transmit the initial setup JSON frame built from setupContext
        // TODO: Spawn a listener to receive inbound events (transcripts, audio, tool calls) from Gemini
        //       - If audio frame: forward to clientSession.sendMessage()
        //       - If tool call: route to Parallel Agent Observers (e.g. Guardrails or Form layout agents)
    }

    @Override
    public void sendClientAudio(byte[] audioData) {
        // TODO: Base64-encode raw PCM audio chunks and send to Gemini Live as realtimeInput payload
    }

    @Override
    public void closeSession() {
        // TODO: Safely close outbound connection to Google and clean up resources
        log.info("Gemini Live session closed.");
    }
}
