// 1. Package declaration organizing this file within your custom exception architecture.
package com.reForm.backend.auth.exception;

// 2. Jackson library import (v3 architecture) used to programmatically convert Java objects into JSON strings.
import tools.jackson.databind.ObjectMapper;

// 3. Low-level Servlet imports required to manipulate the raw HTTP request and response streams.
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 4. Lombok logging annotation to allow clean, professional console/file logging statements.
import lombok.extern.slf4j.Slf4j;

// 5. Spring framework utility class to reference standard HTTP content-types (like application/json).
import org.springframework.http.MediaType;

// 6. Spring Security exception thrown specifically when an AUTHENTICATED user lacks a required Role/Authority.
import org.springframework.security.access.AccessDeniedException;

// 7. The core Spring Security interface used to handle permission restrictions (HTTP 403).
import org.springframework.security.web.access.AccessDeniedHandler;

// 8. Spring archetype annotation to register this class as a managed bean in the Application Context.
import org.springframework.stereotype.Component;

// 9. Standard Java utilities for handling I/O operations, timestamps, and dynamic maps.
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * It translates Spring's default "403 Forbidden" HTML web page into a clean JSON object.
 * When someone triggers a authorization error by being logged in but trying to access an endpoint
 * they lack the correct permissions/roles to view (e.g., a Customer hitting an Admin panel),
 * this file intercepts the failure and formats the bad news so your frontend (React/Mobile app)
 * can actually read it and handle it gracefully.
 */
@Component // Tells Spring to scan and instantiate this bean so it can be added to your Security Filter Chain configuration.
@Slf4j // Injects a 'log' variable automatically into this class using Lombok.
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    // Final variable ensuring immutability of our translator instance.
    private final ObjectMapper objectMapper;

    // CONSTRUCTOR INJECTION: Spring retrieves its globally configured v3 ObjectMapper bean
    // (which honors all custom configurations from application.yml) and feeds it right here.
    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override // Overriding the core method from the AccessDeniedHandler interface.
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        // Log the failure to the server console for background administration and tracking.
        log.warn("Authorization failure: User attempted to access protected path [{}] without adequate privileges. Reason: {}",
                request.getRequestURI(), accessDeniedException.getMessage());

        // Explicitly sets the HTTP response status header to 403 Forbidden.
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // Tells the client application to expect a JSON structure instead of text or HTML layouts.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Standardized RFC-7807 structured JSON error response
        // Creating a dynamic map to hold the key-value pairs that will soon become fields in our JSON object.
        Map<String, Object> errorPayload = new HashMap<>();

        // Captures the exact moment the failure happened and converts it to a clean ISO-8601 string.
        errorPayload.put("timestamp", LocalDateTime.now().toString());

        // Includes the numeric HTTP status (403) directly inside the JSON body for easier frontend parsing.
        errorPayload.put("status", HttpServletResponse.SC_FORBIDDEN);

        // Provides the standard HTTP textual definition for a 403 status code.
        errorPayload.put("error", "Forbidden");

        // Returns a safe, clean message explaining that their credentials lack the required permission levels.
        errorPayload.put("message", "Access Denied: You do not have the required permissions or roles to access this resource.");

        // Dynamically tracks and echoes back the exact endpoint URI the client tried to access (e.g., "/api/v1/admin/delete").
        errorPayload.put("path", request.getRequestURI());

        // Step 1: objectMapper.writeValueAsString converts our 'errorPayload' map into a raw JSON String.
        // Step 2: response.getWriter().write() takes that JSON string and pushes it into the raw HTTP response body output stream.
        response.getWriter().write(objectMapper.writeValueAsString(errorPayload));
    }
}