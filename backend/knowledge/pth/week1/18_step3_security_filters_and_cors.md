# Masterclass Blueprint: Step 3 — The Security Architecture & Gatekeeper

This document provides a technical design and implementation guide for **Step 3: The Security Architecture & Gatekeeper**. It covers the evolution of Java web servers, the lifecycle of HTTP request handling, security protocols (CORS & CSRF), and the inner workings of Spring Security's filter architectures.

---

## Part 1: The Evolutionary History of Java Web Architecture

To understand why we configure security filters inside Spring Boot, we must look at the historical evolution of Java web development.

### 1. The HTTP handling evolution in Java

```
[Raw TCP Sockets] ──► [JDK HttpServer] ──► [Servlet Containers (Tomcat)] ──► [DispatcherServlet] ──► [Declarative @Controllers]
```

#### Layer 1: Raw TCP Sockets (`ServerSocket`)
At the lowest level, an operating system knows nothing about HTTP. It only understands TCP connections on physical ports. To handle HTTP manually in raw Java, you would write:
```java
ServerSocket server = new ServerSocket(8080);
Socket client = server.accept();
InputStream input = client.getInputStream();
// Developer must read raw bytes, parse ASCII text lines manually, find HTTP headers, 
// match the path string, and stream back raw HTTP-formatted bytes as text.
```
*   **The Problem:** Writing parser logic for HTTP headers, body boundaries, file uploads, and session cookies from scratch is incredibly complex and highly vulnerable to security exploits.

#### Layer 2: JDK Native `HttpServer`
Java eventually introduced a basic `com.sun.net.httpserver.HttpServer` wrapper. It parsed headers and separated paths, but developers still had to write low-level code to handle query parameters, format JSON, and manage connection threads manually.

#### Layer 3: Servlet Containers (`HttpServlet` & Tomcat)
To standardize web applications, Java introduced the **Servlet API**. This layer defined a contract: **The Servlet Container** (like Apache Tomcat) manages the network socket, manages thread pools, parses raw bytes, and constructs structured Java objects: `HttpServletRequest` and `HttpServletResponse`.

The developer simply implements a servlet class and overrides lifecycle methods:
```java
public class WorkspaceServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        // Extract parameters, write raw HTML or JSON to resp.getWriter()
    }
}
```
*   **The Problem:** As applications grow, writing a new Java Servlet class for every single endpoint (and mapping them inside a large `web.xml` configuration file) results in highly bloated, unmaintainable codebases.

#### Layer 4: Spring's Centralized `DispatcherServlet`
Spring Web MVC resolved this servlet explosion by introducing the **Front Controller Pattern**. Instead of registering fifty different servlets, Spring registers **exactly one master Servlet**: the `DispatcherServlet` (mapped to catch all paths: `/`).

The `DispatcherServlet` acts as a central router:
1. It intercepts all incoming requests.
2. It inspects class annotations (like `@RestController` and `@GetMapping`).
3. It uses a handler mapping tool to find the corresponding Java method.
4. It parses request payloads (like JSON) into Java objects using Jackson.
5. It invokes your controller method and converts the returned object back into a JSON HTTP response body.

This architecture entirely eliminates the need for developers to write boilerplate servlets, `doGet()` methods, or manual stream parsing code.

---

### 2. Web Servers vs. Web Applications

These two components handle different aspects of the request lifecycle:

```
                  ┌──────────────────────────────────────────────┐
                  │                 WEB SERVER                   │ (e.g., Nginx, Apache)
                  │  - Handles static files (HTML, CSS, JS, Img) │
                  │  - Terminates SSL/TLS certificates           │
                  │  - Distributes traffic (Load Balancer)       │
                  └──────────────────────┬───────────────────────┘
                                         │ (Reverse Proxies raw HTTP requests)
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │              WEB APPLICATION                 │ (e.g., Tomcat, Jetty)
                  │  - Hosts the Java Virtual Machine (JVM)      │
                  │  - Executes compiled Java bytecode (.class)  │
                  │  - Manages Servlet Lifecycles & Filter Chains│
                  └──────────────────────────────────────────────┘
```

#### What is Apache Tomcat?
Tomcat is a **Servlet Container (Web Application Server)**. It does not compile your code; it runs compiled Java web classes. Tomcat listens on a port (like `8080`), accepts connections, assigns threads to process them, runs them through our security filters, and passes the parsed objects to Spring's `DispatcherServlet`.

#### Alternatives to Tomcat
*   **Jetty:** A lightweight, highly embeddable servlet container. Often preferred for high-performance, minimalist cloud architectures.
*   **Undertow:** A flexible, non-blocking web server developed by JBoss/RedHat, utilizing APIs from WildFly.

#### Tomcat vs. Nginx: Are they the same?
**No.**
*   **Nginx** is a native C-based **Web Server and Reverse Proxy**. It is incredibly fast at serving static assets (HTML/CSS/Images), handling SSL/TLS encryption handshakes, and routing traffic to downstream servers. **Nginx cannot execute Java code.**
*   **Tomcat** is a **Servlet Container**. Its sole purpose is to compile JSP files and run Java bytecode.
*   **Production Standard:** In enterprise systems, Nginx sits at the edge of the network to handle incoming secure SSL traffic, and then forwards (reverse-proxies) the plain HTTP traffic to Tomcat running inside a private virtual cloud network.

---

## Part 2: The Core Security Concepts (CORS & CSRF)

---

### 1. CORS (Cross-Origin Resource Sharing)

#### Who does CORS protect?
**CORS protects the user's browser, not the server.**

```
   [ User's Browser ] ─────── 1. Reads malicious JS from evil.com ──────► [ evil.com ]
           │
           ├──────── 2. Malicious JS fires fetch() to reform.app ────────► [ reform.app (API) ]
           │
           ◄── 3. Server responds. Browser notices CORS header mismatch. ──┤
                  Browser BLOCKS the response payload from JavaScript!
```

#### The SOP (Same-Origin Policy)
Browsers enforce a sandbox rule: JavaScript loaded from one origin (e.g., `https://evil.com`) is strictly blocked from reading responses loaded from another origin (e.g., `https://reform.app`). This prevents malicious scripts from stealing user data.

#### The "Evil.com" Scenario
Imagine you are logged into your bank at `https://bank.com`, which stores your session credentials in a browser cookie.

If you visit `https://evil.com` in another tab, and that site executes this JavaScript:
```javascript
fetch('https://bank.com/api/transfer?to=attacker', { method: 'POST' });
```
Without Same-Origin Policy, the browser would automatically attach your `bank.com` session cookies to this cross-site request, allowing hackers to execute unauthorized transfers.

#### How CORS Resolves This Safely
To allow legitimate cross-origin requests (such as your Next.js frontend at `https://reform.app` communicating with your Spring backend at `https://api.reform.app`), the browser uses the CORS protocol:

1.  **The Pre-flight Check (`OPTIONS`):** Before executing a cross-origin request, the browser sends an empty HTTP `OPTIONS` request to the target server (`https://api.reform.app`) asking: *"Is the origin `https://reform.app` allowed to execute this request?"*
2.  **The Server Response:** The server checks its CORS configuration and responds with headers:
    `Access-Control-Allow-Origin: https://reform.app`
3.  **The Browser Verification:** The browser checks these headers. If the initiating website's origin is authorized, it executes the real request. If unauthorized, **the browser blocks the response** from being read by JavaScript.

#### Clarifying a Common Misconception:
> "If `evil.com` configures its own CORS to allow requests from `evil.com`, can it bypass security?"

**No.** A website's CORS configuration only controls who is allowed to call *itself*. It has no control over other APIs. Only the target server (`https://api.reform.app`) can authorize `https://evil.com` to read its resources.

---

### 2. CSRF (Cross-Site Request Forgery)

#### How CSRF Works
CSRF exploits the browser's default behavior of automatically attaching stored cookies to *every* request sent to a domain, regardless of which website initiated the call.

#### Why Stateless JWT Architectures are Immune to CSRF
In our stateless API structure, we do not store session IDs inside browser cookies. Instead, our frontend stores our JWT inside JavaScript memory or `localStorage`.

*   **The Defense:** To authenticate with our API, the frontend must manually attach the HTTP header:
    `Authorization: Bearer <token>`
*   **The Browser Lock:** Because of the Same-Origin Policy, a script running on `https://evil.com` has absolutely no way to access the local storage of `https://reform.app`.
*   Since `evil.com` cannot read your JWT, it cannot attach the `Authorization` header to its malicious cross-site requests. Thus, **our stateless API is naturally immune to CSRF**, allowing us to safely disable Spring's `CsrfFilter` inside our configuration.

---

## Part 3: The Servlet Filter Chain Deep-Dive

---

### 1. What is the `SecurityFilterChain`?

The `SecurityFilterChain` is not a collection of Servlets. **It is a collection of standard Servlet Filters** configured by Spring Security.

These filters are registered within a special container bean called `DelegatingFilterProxy`, which bridges Tomcat's servlet pipeline with Spring's application context.

```
[ Tomcat Socket Connection ]
             │
             ▼
    [ FilterChainProxy ]  <── Manages the active SecurityFilterChain
             │
             ▼
   [ 1. ChannelProcessingFilter ]
             │
             ▼
   [ 5. CorsFilter ]  <── Checks CORS headers before authentication runs
             │
             ▼
   [ 8. JwtAuthenticationFilter ]  <── Custom: Validates JWT, sets security context
             │
             ▼
   [ 14. AuthorizationFilter ]  <── Checks path permissions (permit vs lock)
             │
             ▼
    [ DispatcherServlet ]  <── Spring's REST Switchboard
```

---

### 2. The Standard 14-Filter Security Lifecycle

Spring Security applies these filters sequentially. Here is exactly what happens at each stage of a request:

```
[ Incoming HTTP Request ]
                              │
                              ▼
        ┌───────────────────────────────────────────┐
        │ 1. ChannelProcessingFilter                │ (Enforces HTTPS redirects)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 2. WebAsyncManagerIntegrationFilter       │ (Propagates SecurityContext to async threads)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 3. SecurityContextHolderFilter            │ (Loads existing SecurityContext if stateful)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 4. HeaderWriterFilter                     │ (Appends secure browser headers like X-Frame-Options)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 5. CorsFilter                             │ (Validates cross-origin access permissions)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 6. CsrfFilter                             │ (Checks CSRF tokens - Disabled in our API)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 7. LogoutFilter                           │ (Intercepts logout paths to invalidate sessions)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 8. [YOUR JwtAuthenticationFilter]         │ (Custom: Extracts, validates JWT, sets Context)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 9. UsernamePasswordAuthenticationFilter   │ (Handles traditional form-login credentials)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 10. DefaultLoginPageGeneratingFilter       │ (Generates default login form if active)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 11. BasicAuthenticationFilter             │ (Parses HTTP Basic credentials if active)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 12. SecurityContextHolderAwareRequestFilter│ (Wraps Request with standard Servlet API helpers)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 13. AnonymousAuthenticationFilter         │ (Sets default Anonymous user if unauthenticated)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 14. ExceptionTranslationFilter            │ (Catches security exceptions and delegates)
        │      └──► CustomAuthenticationEntryPoint  │ (translates security exceptions to clean JSON)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 15. AuthorizationFilter                   │ (Checks role authorizations for the target path)
        └─────────────────────┬─────────────────────┘
                              │
                              ▼ (Access Granted!)
                    [ DispatcherServlet ]
                              │
                              ▼
                    [ WorkspaceController ]
```

---

### 3. The `addFilterBefore` Mechanics

#### Why register our custom filter *before* `UsernamePasswordAuthenticationFilter`?
By default, Spring Security uses `UsernamePasswordAuthenticationFilter` to handle traditional form logins.

Our application is stateless and communicates via bearer tokens. We must intercept the request, extract the token, validate its signature, and populate the `SecurityContext` **before** Spring Security attempts standard authentication or authorization checks.

If we place our custom filter *after* these checks, Spring Security will assume the request is unauthenticated and reject it at the `AuthorizationFilter` stage, preventing the request from ever reaching your business logic.

---

## Part 4: The Core Security Classes & Beans

### 1. `HttpSecurity`
`HttpSecurity` is a fluent builder class provided by Spring Security. It allows you to configure CORS, CSRF, session state behavior, exception endpoints, and route permissions in a single cohesive pipeline inside `SecurityConfig.java`.

### 2. `AuthenticationManager` & `AuthenticationConfiguration`
*   **`AuthenticationManager`:** The core interface responsible for authenticating a user's credentials. It delegates validation to configured `AuthenticationProviders`.
*   **`AuthenticationConfiguration`:** A helper configuration class provided by Spring. It exposes Spring's internal authentication manager so we can inject it into our REST services to authenticate credentials during login requests.

---

## Part 5: Finalized, Fully Implemented Production Code

The following sections contain the complete, production-grade implementations of your security gateway and configuration files.

---

### 1. The Exception Entry Point
*   **Location:** `src/main/java/com/reForm/backend/core/exception/CustomAuthenticationEntryPoint.java`

```java
package com.reForm.backend.core.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom entry point that intercepts unauthenticated security exceptions (HTTP 401).
 * Translates low-level JVM security exceptions into formatted JSON error payloads.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, 
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Standardized RFC-7807 structured JSON error response
        Map<String, Object> errorPayload = new HashMap<>();
        errorPayload.put("timestamp", LocalDateTime.now().toString());
        errorPayload.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorPayload.put("error", "Unauthorized");
        errorPayload.put("message", "Full authentication is required to access this resource: " + authException.getMessage());
        errorPayload.put("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(errorPayload));
    }
}
```

---

### 2. The Security Configuration Chain
*   **Location:** `src/main/java/com/reForm/backend/auth/config/SecurityConfig.java`

```java
package com.reForm.backend.auth.config;

import com.reForm.backend.auth.filter.JwtAuthenticationFilter;
import com.reForm.backend.core.exception.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central security gatekeeper configuration for the application.
 * Establishes stateless JWT session management, CORS mapping, and path permission controls.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF safely since our tokens are stored statelessly
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Register CORS configuration rules
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 3. Configure HTTP route permissions
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()                // Open login and authentication paths
                .requestMatchers("/api/v1/users/register").permitAll()          // Open user registration path
                .anyRequest().authenticated()                                   // Secure all other endpoints
            )
            
            // 4. Force Spring to keep sessions stateless (No HTTP Sessions created)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 5. Override default exception response entry point (JSON output)
            .exceptionHandling(exception -> exception.authenticationEntryPoint(customAuthenticationEntryPoint))
            
            // 6. Mount our custom JWT filter before Spring's native Authentication filters
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Define origins allowed to execute cross-origin requests targeting our API
        configuration.setAllowedOrigins(List.of("http://localhost:3000")); // Your frontend Next.js server origin
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        configuration.setAllowCredentials(true); // Exposes headers and cookie values safely across origins

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Map rules across all application routes
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        // Exposes Spring Security's native credential validation manager for REST services to use
        return configuration.getAuthenticationManager();
    }
}
```

---

### Phase 5 — The Socratic Question

Now that Step 3 is complete, let's look at **Step 4: The Request Guard (The Filter)**, where we will build the custom `JwtAuthenticationFilter` that runs on every request:

> **If a client sends an API request to a completely open public endpoint (like `/api/v1/auth/register`) but accidentally attaches an expired or malformed JWT token inside their `Authorization` header, should our custom `JwtAuthenticationFilter` reject the request immediately with an HTTP 401 response, or should it ignore the invalid token and let the request proceed to the registration controller?**
>
> **How does your choice affect user experience, and how should we configure the filter to handle this scenario securely?**

---

### Phase 6 — The Call to Action

You are ready to proceed!
1.  **Formulate your analysis of the Socratic Question in Phase 5.**
2.  Once you share your thoughts, let me know when you are ready to construct **Step 4: The Request Guard (The Filter)**, and I will guide you through writing `JwtAuthenticationFilter.java` safely.