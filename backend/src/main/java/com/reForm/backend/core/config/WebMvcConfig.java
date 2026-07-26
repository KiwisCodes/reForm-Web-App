package com.reForm.backend.core.config;

import com.reForm.backend.core.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WEB MVC CONFIGURATION
 * 
 * Exposes Spring MVC routing configurations. We implement WebMvcConfigurer 
 * to register our custom HTTP Interceptor (RateLimitInterceptor) with the 
 * application's interceptor chain registry.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * Registers interceptors to the Spring MVC routing framework.
     * 
     * @param registry InterceptorRegistry used to register the interceptor bean and define URL matchers.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register the rate limit interceptor and bind it to specific paths
        registry.addInterceptor(rateLimitInterceptor)
                // Protects Thien's public form APIs (link entries and form submissions)
                .addPathPatterns("/api/v1/public/**")
                // Protects WebSocket handshake upgrade routes
                .addPathPatterns("/ws/**");
    }
}

//rate-limit:userId