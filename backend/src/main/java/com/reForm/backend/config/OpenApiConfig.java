package com.reForm.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI 3.0 & Swagger UI Configuration for the reForm backend platform.
 * Configures API metadata, server URLs, and JWT Bearer Security Scheme.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "reForm Enterprise Platform API",
                version = "1.0.0",
                description = "REST API & WebSocket infrastructure for reForm Omni-modal Voice/Visual Form Builder & Conversational AI Engine.",
                contact = @Contact(
                        name = "reForm Architecture Team",
                        email = "support@reform.ai"
                ),
                license = @License(
                        name = "Proprietary License",
                        url = "https://reform.ai/terms"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development Server")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT token obtained from POST /api/v1/auth/login or POST /api/v1/auth/register."
)
public class OpenApiConfig {
    // Configuration bean for SpringDoc OpenAPI 3.0 auto-discovery
}
