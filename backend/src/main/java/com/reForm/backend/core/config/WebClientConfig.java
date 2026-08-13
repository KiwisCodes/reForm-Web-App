package com.reForm.backend.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WEBCLIENT CONFIGURATION
 * 
 * Configures the Spring WebClient bean with a 10MB memory buffer for high-throughput
 * non-blocking HTTP REST calls (e.g. Gemini 3.6 Flash REST service for Mode 2 & Mode 3).
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
                .build();
    }
}
