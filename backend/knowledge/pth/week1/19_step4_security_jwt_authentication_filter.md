# Masterclass Blueprint: Step 4 — The Request Guard (The Filter)

This masterclass details the mechanics of **Step 4: The Request Guard**. It covers servlet interceptors, token parsing, dependency inversion, and how Spring Security manages threat isolation at the boundary.

---

## Part 1: The Core Interceptor Mechanics

Before writing the code, we must understand the lifecycle of servlet filters and how Spring Security isolates execution contexts.

### 1. What is `OncePerRequestFilter`?

#### The Problem: Double-Filter Execution
In standard Java web applications, a standard Servlet `Filter` can be executed multiple times during a single HTTP request. This happens if the request triggers an internal dispatch forwarding (e.g., forwarding to an error page) or an asynchronous background thread dispatch.

If our JWT validation logic runs multiple times per request:
1. We waste CPU cycles performing redundant cryptographic checks on the same token.
2. We risk corrupting the security context state if downstream forwarding dispatches interfere with our authentication thread.

#### The Solution: `OncePerRequestFilter`
`OncePerRequestFilter` is an abstract class provided by Spring Web. It implements the standard Servlet `Filter` interface but wraps the execution inside a safety check:
1. It checks if a specific internal request attribute (a "already filtered" flag) is present.
2. If the flag is not present, it sets the flag and executes your custom logic.
3. If the flag is present, it skips your custom logic and passes the request directly to the next filter.

This guarantees that **our custom JWT validation runs exactly once per incoming HTTP request.**

---

### 2. Why make a custom filter instead of using default ones?
Spring Security has no out-of-the-box filter designed specifically to parse and validate custom stateless JWT tokens from bearer headers.

Spring provides filters for standard form logins (`UsernamePasswordAuthenticationFilter`) and HTTP Basic credentials (`BasicAuthenticationFilter`). To validate stateless tokens, we must write a custom filter extending `OncePerRequestFilter` and insert it into Spring Security's filter pipeline.

---

### 3. Key Concepts: Contexts, Filters, and Holders

#### `filterChain` vs. `SecurityFilterChain`
*   **`filterChain` (Method Parameter):** This is the active, sequential list of filters managed by Tomcat. Inside your custom filter, you invoke `filterChain.doFilter(request, response)` to pass the request to the next guard in line.
*   **`SecurityFilterChain` (Spring Bean):** This is the global configuration bean we wrote in `SecurityConfig.java`. It declares *how* the `FilterChain` should behave (e.g., which routes are public, session policies, and where our custom filter should be inserted).

#### `SecurityContext` vs. `SecurityContextHolder`
*   **`SecurityContext`:** An object that stores the `Authentication` details of the currently verified principal.
*   **`SecurityContextHolder`:** A utility wrapper that manages where the `SecurityContext` is stored. By default, it uses a **`ThreadLocal`** strategy. This means it binds the security details strictly to the single CPU thread executing the current request, isolating users from each other.

```
       ┌────────────────────────────────────────────────────────┐
       │                 SecurityContextHolder                  │
       │  (ThreadLocal Cabinet - routes thread to its folder)   │
       └───────────────────────────┬────────────────────────────┘
                                   │
                                   ▼
       ┌────────────────────────────────────────────────────────┐
       │                    SecurityContext                     │
       │         (The folder containing the active token)       │
       └───────────────────────────┬────────────────────────────┘
                                   │
                                   ▼
       ┌────────────────────────────────────────────────────────┐
       │                     Authentication                     │
       │   (Principal details, authorities, verified status)    │
       └────────────────────────────────────────────────────────┘
```

---

## Part 2: The Fully Expanded 15-Filter Lifecycle Diagram

By default, Spring Security uses standard filters to guard endpoints. Here is exactly where our custom `JwtAuthenticationFilter` is placed in relation to `UsernamePasswordAuthenticationFilter` and `BasicAuthenticationFilter`:

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
        │ 8. [YOUR JwtAuthenticationFilter]         │ (Custom: Extracts, validates, and sets context)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 9. UsernamePasswordAuthenticationFilter   │ (Native Form Login - Bypassed in our app)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 10. DefaultLoginPageGeneratingFilter       │ (Generates default login form if active)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 11. BasicAuthenticationFilter             │ (Parses HTTP Basic credentials if present)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 12. SecurityContextHolderAwareRequestFilter│ (Wraps Request with standard Servlet helper methods)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 13. AnonymousAuthenticationFilter         │ (Sets default Anonymous user principal if empty)
        └─────────────────────┬─────────────────────┘
                              ▼
        ┌───────────────────────────────────────────┐
        │ 14. ExceptionTranslationFilter            │ (Catches security exceptions and delegates)
        │      ├──► CustomAuthenticationEntryPoint  │ (translates unauthenticated failures to JSON)
        │      └──► CustomAccessDeniedHandler       │ (translates unauthorized forbidden access)
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

## Part 3: Decoding the JWT Anatomy & Encoding Purpose

#### Are the Header and Payload never hashed?
**Correct.** The Header and Payload are **never hashed** when generating a JWT. They are simply Base64URL encoded JSON objects. Anyone can decode a JWT payload online and read the fields (such as user email or user ID).

#### If the payload is visible, why do we need Base64URL encoding at all?
We encode because raw binary representations of JSON bytes contain characters that are invalid in HTTP headers (e.g., control characters, spaces, or brackets). Base64URL maps any arbitrary binary sequence into a clean, safe set of 64 standard ASCII characters, ensuring safe network transmission.

#### Why is this secure?
The security comes entirely from the **Signature**.

The signature is a cryptographic hash of the encoded Header and encoded Payload combined with the server's private secret key. If an attacker modifies even a single character in the Payload (e.g., changing `role: "FORM_FILLER"` to `role: "ADMIN"`), **the signature becomes mathematically invalid**. When our backend parses the tampered token, the signature verification fails, and we reject the request.

---

## Part 4: The Interface Decoupling & Injection Mechanics

### 1. The Interface Injection Pattern (DIP)

Inside `JwtAuthenticationFilter`, we inject the standard interface:
```java
private final UserDetailsService userDetailsService;
```

#### Why do we inject the interface and not the concrete `CustomerUserDetailService`?
This is a core application of the **Dependency Inversion Principle (DIP)**:
1.  **Deconfigured Dependencies:** Our filter is a core security gatekeeper. It should not be tightly coupled to our database access strategy.
2.  **How Spring matches it:** During startup, Spring Boot scans the application context. It discovers that your custom class `CustomerUserDetailService` is annotated with `@Service` and implements the `UserDetailsService` interface. Since it is the only matching bean, Spring automatically injects your PostgreSQL adapter into the filter.
3.  **Future Proofing:** If you later migrate your database to an LDAP directory, you only need to write an `LdapUserDetailsService` implementing the `UserDetailsService` interface. The filter logic will compile and run without changing a single line of code.

---

### 2. Why load `UserDetails` instead of using `CustomerUserDetails` directly?

Inside the filter, we use the standard interface types:
```java
UserDetails userDetails = userDetailsService.loadUserByUsername(email);
```

While our custom implementation `CustomerUserDetailService` returns `CustomerUserDetail` (which implements the `UserDetails` interface), our filter remains clean and standard by interacting with the interface.

#### Who actually uses `CustomerUserDetails` then?
Our business controllers. In your controllers, you can extract the authenticated principal from the context and safely cast it back down to your custom wrapper class to fetch custom fields, such as your user's database UUID:
```java
@GetMapping("/me")
public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
    CustomerUserDetail customUser = (CustomerUserDetail) userDetails;
    UUID userId = customUser.getId(); // Exposes database UUID instantly!
}
```

---

### 3. The `null` credential inside the authentication token

When instantiating our authentication token, we pass `null` as the second argument:
```java
UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
    userDetails, 
    null,  // Why null?
    userDetails.getAuthorities()
);
```
The constructor parameters are: `(principal, credentials, authorities)`.

During initial login, the credentials field holds the user's raw password. However, once the signature is validated and the user is authenticated, **we must never keep sensitive raw passwords in memory**. Passing `null` tells Spring Security that the credential verification step is already complete, clearing the password field from memory to prevent memory heap dump vulnerabilities.

---

### 4. Why attach request metadata via `WebAuthenticationDetailsSource`?

```java
authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
```
This line extracts low-level network details from the HTTP request (such as the client's IP address and session ID) and binds them directly to the authentication token.

#### Why we do this:
We do not use this for session tracking, but for **security auditing and logging**. If a malicious client attempts to flood your API or execute actions from a blocked country, having their IP address bound directly to the active security context allows your analytics and auditing layers to detect and block threats instantly.

---

### 5. Access Denied Handler vs. Authentication Entry Point

These two boundary components handle different exceptions thrown by the security filter chain:

*   **`CustomAuthenticationEntryPoint` (HTTP 401):** Triggered when an **unauthenticated** user tries to access a protected resource. This happens if the user provides no token, an expired token, or a tampered token.
*   **`CustomAccessDeniedHandler` (HTTP 403):** Triggered when an **authenticated** user tries to access a resource they do not have authorization to view. This happens if a user with the role `FORM_FILLER` tries to modify a workspace that requires `ROLE_FORM_BUILDER`.

---

## Part 5: Finalized, Fully Implemented Production Code

The following section contains the complete, production-grade implementation of your Request Guard.

*   **Location:** `src/main/java/com/reForm/backend/auth/filter/JwtAuthenticationFilter.java`

```java
package com.reForm.backend.auth.filter;

import com.reForm.backend.auth.port.ITokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Custom gatekeeper filter running once per incoming HTTP request.
 * Intercepts protected requests, extracts bearer tokens, validates signatures, 
 * and populates Spring's ThreadLocal SecurityContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final ITokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. Extract the raw token from the authorization header
            String jwt = extractTokenFromRequest(request);

            // 2. If the token is present and its cryptographic signature is valid
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                
                // 3. Extract the identifier (email) from the token payload
                String email = tokenProvider.extractEmail(jwt);

                // 4. Load the user authorities using our database adapter interface
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // 5. Instantiate a validated authentication token
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null, // Clear credential memory
                        userDetails.getAuthorities()
                );

                // 6. Bind request-level network metadata (IP address) to the token for auditing
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 7. Store the verified authentication inside the static ThreadLocal context
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Successfully authenticated user: {} inside SecurityContext", email);
            }
        } catch (Exception e) {
            log.error("Failed to set user authentication inside security context: {}", e.getMessage());
        }

        // 8. Pass the request downstream to the next sequential filter in the chain
        filterChain.doFilter(request, response);
    }

    /**
     * Clean helper method that extracts the Bearer token from the HTTP Authorization header.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Remove the "Bearer " prefix to isolate the raw token
        }
        return null;
    }
}
```

---

## Part 6: Updating Your Token Classes

To support our new filter workflow, we must add the **`extractEmail`** contract to our token engine classes so we can parse emails directly from token payloads.

### 1. Update the Port (`ITokenProvider.java`)
Open `src/main/java/com/reForm/backend/auth/port/ITokenProvider.java` and add this contract method:
```java
/**
 * Safely extracts the user email claim from the token payload.
 */
String extractEmail(String token);
```

### 2. Update the Adapter (`JwtTokenImpl.java`)
Open `src/main/java/com/reForm/backend/auth/service/JwtTokenImpl.java` and implement the new method:
```java
@Override
public String extractEmail(String token) {
    Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

    return claims.get("email", String.class);
}
```

---

### Phase 5 — The Socratic Question

Now that Step 4 is complete, we are ready to move to **Step 5: The Public Gateway (Authentication API)**, where we will build our login and registration endpoints.

Let's think through a crucial security and clean code choice before designing the API controllers:

> **When a user successfully registers via our register endpoint (`/api/v1/users/register`), should the controller immediately generate and return a signed JWT token in the response so they are instantly logged in, or should it force them to execute a separate, explicit login request (`/api/v1/auth/login`) with their newly created credentials?**
>
> **What are the UX and security implications of each option?**

---

### Phase 6 — The Call to Action

You are ready to proceed!
1.  **Formulate your analysis of the Socratic Question in Phase 5.**
2.  Let me know when you are ready to construct **Step 5: The Public Gateway (Authentication API)**, and I will guide you through writing `IAuthService.java`, `AuthServiceImpl.java`, and `AuthController.java` step-by-step.