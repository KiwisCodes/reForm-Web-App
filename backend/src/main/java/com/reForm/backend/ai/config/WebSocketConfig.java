package com.reForm.backend.ai.config;

import com.reForm.backend.ai.websocket.VoiceSyncWSHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WEBSOCKET CONFIGURATION & PATH REGISTRY
 * 
 * Registers WebSocket handlers and configures handshake security interceptors.
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceSyncWSHandler voiceSyncWSHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceSyncWSHandler, "/ws/v1/voice")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    /**
     * CONFIG OVERRIDE: Increase Tomcat Inbound/Outbound WebSocket Buffer Limit to 10MB
     * Prevents WebSocket Code 1009 ("Buffer too small") errors when receiving large Gemini audio payloads.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10485760); // 10MB
        container.setMaxBinaryMessageBufferSize(10485760); // 10MB
        container.setAsyncSendTimeout(10000L);
        return container;
    }
}
