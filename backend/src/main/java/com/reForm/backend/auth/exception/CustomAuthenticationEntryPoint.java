// 1. Package declaration organizing this file within your custom exception architecture.
package com.reForm.backend.auth.exception;

// 2. Jackson library import (v3 architecture) used to programmatically convert Java objects into JSON strings.
import tools.jackson.databind.ObjectMapper;

// 3. Low-level Servlet imports required to manipulate the raw HTTP request and response streams.
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 4. Spring framework utility class to reference standard HTTP content-types (like application/json).
import org.springframework.http.MediaType;

// 5. Spring Security exception thrown specifically when a user fails to authenticate.
import org.springframework.security.core.AuthenticationException;

// 6. The core Spring Security interface used to handle anonymous user access denials.
import org.springframework.security.web.AuthenticationEntryPoint;

// 7. Spring archetype annotation to register this class as a managed bean in the Application Context.
import org.springframework.stereotype.Component;

// 8. Lombok logging annotation to allow clean, professional console/file logging statements.
import lombok.extern.slf4j.Slf4j;

// 9. Standard Java utilities for handling I/O operations, timestamps, and dynamic maps.
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * It translates Spring's default "401 Unauthenticated" HTML web page into a clean JSON object.
 * When someone triggers a security error by having no token or an expired token,
 * this file intercepts the failure and formats the bad news so your frontend (React/Mobile app)
 * can actually read it and handle it gracefully.
 */
@Component // Tells Spring to scan and create an instance of this class so it can be injected into your Security Filter Chain configuration.
@Slf4j // Injects a 'log' variable automatically into this class using Lombok.
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /*
     ====================================================================================
     THE OBJECTMAPPER GUIDE: WHY, WHAT, AND HOW
     ====================================================================================

     WHY WE NEED IT:
     Java and Front-end systems (like React, Angular, or Mobile Apps) speak completely
     different languages. Java speaks in "Objects" (instances of classes/maps), while the
     web speaks in "JSON Strings" (plain text formatted with curly braces {}).
     When an exception happens deep inside Spring Security filters, we are operating outside
     of Spring's normal Controller ecosystem. Spring won't automatically convert our data to
     JSON here. We have to manually bridge that gap.

     WHAT IT DOES:
     The ObjectMapper acts as a universal translator.
     1. Serialization (Writing): It takes a Java Object/Map and turns it into a JSON String.
     2. Deserialization (Reading): It takes a JSON String and turns it into a Java Object.

     HOW WE USE IT (EXAMPLES):

     Example 1: Serialization (Turning an object into text to send over the network)
        User user = new User("John", "john@email.com");
        String jsonText = objectMapper.writeValueAsString(user);
        // Result: "{"name":"John","email":"john@email.com"}"

     Example 2: Deserialization (Turning received network text back into a usable Java object)
        String incomingJson = "{\"name\":\"Alice\"}";
        User userObj = objectMapper.readValue(incomingJson, User.class);
        System.out.println(userObj.getName()); // Prints: Alice
    */

    // Final variable ensuring immutability of our translator instance.
    private final ObjectMapper objectMapper;

    // CONSTRUCTOR INJECTION: Spring retrieves its globally configured v3 ObjectMapper bean
    // (which honors all custom configurations from application.yml) and feeds it right here.
    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override // Overriding the core method from the AuthenticationEntryPoint interface.
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // Log the authentication failure details securely on the server side for debugging purposes.
        log.error("Authentication failure detected on path [{}]. Reason: {}",
                request.getRequestURI(), authException.getMessage());

        // Explicitly sets the HTTP response status header to 401 Unauthorized.
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Tells the client's browser/app to expect a JSON response instead of HTML or plain text.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Standardized RFC-7807 structured JSON error response
        // Creating a dynamic map to hold the key-value pairs that will soon become fields in our JSON object.
        Map<String, Object> errorPayload = new HashMap<>();

        // Captures the exact moment the failure happened and converts it to a clean ISO-8601 string.
        errorPayload.put("timestamp", LocalDateTime.now().toString());

        // Includes the numeric HTTP status (401) directly inside the JSON body for easier frontend parsing.
        errorPayload.put("status", HttpServletResponse.SC_UNAUTHORIZED);

        // Provides the standard HTTP textual definition for a 401 status code.
        errorPayload.put("error", "Unauthorized");

        // Sanitized, clean user-friendly message explaining that authentication is missing or bad.
        errorPayload.put("message", "Authentication Required: You must provide valid security credentials to access this resource.");

        // Dynamically tracks and echoes back the exact endpoint URI the client tried to access (e.g., "/api/v1/dashboard").
        errorPayload.put("path", request.getRequestURI());

        // Step 1: objectMapper.writeValueAsString converts our 'errorPayload' map into a raw JSON String.
        // Step 2: response.getWriter().write() takes that JSON string and pushes it into the raw HTTP response body output stream.
        response.getWriter().write(objectMapper.writeValueAsString(errorPayload));
    }
}