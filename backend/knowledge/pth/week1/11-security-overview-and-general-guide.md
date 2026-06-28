# Enterprise Architectural Blueprint: Stateless JWT Security System

This master planning document details the foundational concepts, directory structures, assembly sequence, and Socratic blueprints required to construct a production-ready, stateless JSON Web Token (JWT) security perimeter for the *reForm* backend application.

---

## Part 1: The Core Concepts Deep Dive

To build a secure enterprise boundary, we must peel back the layers of framework abstractions and examine the underlying mechanics of Java web containers, hashing mathematics, and network communication.

### 1. Servlets & Servlet Filters: The Gatekeepers of the JVM

#### What is a Servlet?
At its absolute baseline, a Java web application does not natively understand high-level REST annotations like `@GetMapping("/users")`. The underlying operating system only understands raw TCP network sockets receiving sequential streams of bytes.

A **Servlet** is a standardized Java class running inside a **Servlet Container** (such as Apache Tomcat, which Spring Boot embeds by default). The container's responsibility is to:
1. Listen on a designated network port (typically `8080`).
2. Accept the incoming stream of raw bytes.
3. Parse those bytes into a structured Java object called `HttpServletRequest`.
4. Pass that request object to a Servlet's `service()` method.
5. Serialize the resulting `HttpServletResponse` object back into raw network bytes to transmit over the internet.

In modern Spring Boot applications, you do not write raw Servlets. Spring provides a single, centralized master Servlet called the `DispatcherServlet`. Every single REST request destined for your application flows through this single servlet, which acts as an internal switchboard routing traffic to your controller classes.

#### What is a Servlet Filter?
Before an incoming request ever reaches the `DispatcherServlet` (and by extension, your controllers), it must pass through an sequential chain of interceptors called the **Servlet Filter Chain** (`FilterChain`).

```
[Incoming HTTP Bytes] 
         │
         ▼
 ┌──────────────┐
 │ Tomcat Port  │
 └──────┬───────┘
        │ (Parses into HttpServletRequest)
        ▼
 ┌──────────────┐
 │  Filter 1    │ (e.g., CorsFilter - Validates origin domain headers)
 └──────┬───────┘
        ▼
 ┌──────────────┐
 │  Filter 2    │ (e.g., JwtAuthenticationFilter - Inspects token presence)
 └──────┬───────┘
        ▼
 ┌──────────────┐
 │DispatcherSrvl│ (Spring's master Dispatcher Servlet)
 └──────┬───────┘
        ▼
 ┌──────────────┐
 │UserController│ (Your target resource controller)
 └──────────────┘
```

A Servlet Filter is an absolute gatekeeper. It has the programmatic power to:
*   **Inspect** the request (headers, path, URI parameters, payload streams).
*   **Modify** the request or response objects before they proceed down the chain.
*   **Halt** request execution immediately and write a response back to the client (e.g., returning an `HTTP 401 Unauthorized` block), meaning downstream filters and controllers are never invoked.
*   **Pass** the request forward to the next sequential filter in the chain.

---

### 2. Password Salting: Defeating the Rainbow Tables

#### The Problem: Identical Hashes
If two users, Alice and Bob, both choose the password `"password123"`, and we encrypt them using a basic hashing algorithm like SHA-256, both records will contain the exact same output hash in your database:
*   **Alice's Hash:** `ef92b778...`
*   **Bob's Hash:**   `ef92b778...`

If an attacker breaches the database, they do not need to perform expensive, slow brute-force calculations. They can use **Rainbow Tables**—precomputed lookup directories containing billions of common passwords mapped to their corresponding SHA-256 hashes. They simply match `ef92b778...` in their table and instantly discover it translates back to `"password123"`.

#### The Solution: Cryptographic Salt
A **Salt** is a highly secure, completely random string of bytes generated on-the-fly *every single time* a password is saved or updated.

Instead of hashing the password directly, the database combines the password with this unique salt:

$$\text{Hash}(\text{Password} + \text{Salt})$$

*   **Alice's Salt:** `xK9!p` $\rightarrow$ Hashed: `ef92b778...`
*   **Bob's Salt:**   `qZ2@m` $\rightarrow$ Hashed: `a8f3b29c...`

Now, even though their raw passwords are identical, their database records are completely different. Rainbow Tables are rendered useless because they do not contain hashes mixed with those specific, dynamically generated random salts.

#### How BCrypt Handles Hashing Natively
Modern hashing algorithms like **BCrypt** (which we access via Spring Security's `BCryptPasswordEncoder`) handle this process inside a single interface call:
1. When you invoke `encoder.encode("password123")`, BCrypt automatically generates a cryptographically secure salt.
2. It hashes the password with that salt.
3. It outputs a formatted string containing the salt, the work factor (computational cost), and the resulting hash combined (e.g., `$2a$10$dXJ3Y1...`).
4. During authentication, `encoder.matches("password123", hashFromDb)` automatically parses the salt out of the stored database string, hashes the input attempt with that exact salt, and compares the resulting output.

---

### 3. HTTP vs. REST: Protocols and Architectures

HTTP and REST are fundamentally distinct concepts:
*   **HTTP (Hypertext Transfer Protocol)** is a concrete application-layer transport protocol. It defines the exact syntactic specification of how messages are structured and exchanged over TCP.
*   **REST (Representational State Transfer)** is an architectural design style. It is a set of design constraints and guidelines on *how* to use the HTTP protocol cleanly to manage data resources statefully or statelessly.

#### Inside an HTTP Request/Response (The Anatomy)

Consider this raw, underlying HTTP exchange when retrieving a secure workspace:

```http
GET /api/v1/workspaces/7a2d-4f9e-bc91 HTTP/1.1
Host: api.reform.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Accept: application/json
```

*   **Method:** `GET` (Specifies the action type).
*   **Path/URI:** `/api/v1/workspaces/7a2d-4f9e-bc91` (Identifies the unique resource path).
*   **Protocol Version:** `HTTP/1.1`.
*   **Headers:** Metadata key-value pairs (`Host`, `Authorization`, `Accept`).
*   **Request Body:** (Left empty for GET requests).

The server processes this request and responds over the network socket:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Date: Mon, 12 May 2025 12:00:00 GMT

{
  "id": "7a2d-4f9e-bc91",
  "name": "Design Team Workspace",
  "description": "Collaborative space"
}
```

*   **Status Line:** Protocol version + Status Code (`200 OK`).
*   **Response Headers:** Payload metadata details (`Content-Type`).
*   **Response Body:** The raw JSON payload representing the resource state.

#### Where does the JWT live?
*   **During Login/Registration (The Response):** The JWT is returned in the **HTTP Response Body** as a JSON string because the client must read it, extract it, and save it in client-side memory (such as `localStorage`).
*   **During Authenticated Requests (The Request):** The JWT is sent in the **HTTP Request Header** under `Authorization: Bearer <token_string>`. It does not live in the request body because many HTTP operations (like `GET` or `DELETE`) do not transmit request bodies. Standardizing it inside the headers ensures authorization works consistently across all HTTP verbs.

---

## Part 2: The Architectural Tree Structure

The following directory blueprint details the structure of the authentication system. Every component is mapped to its specific design motivation:

```
📁 com.reForm.backend
├── 📁 core
│   └── 📁 config
│       └── 📄 PasswordEncoderConfig.java  # PROBLEM: Services need to hash passwords, but injecting
│                                          # PasswordEncoder directly into SecurityConfig creates circular dependencies.
│                                          # SOLUTION: Isolate the bean creation here.
│
├── 📁 auth
│   ├── 📁 dto
│   │   ├── 📄 LoginRequestDto.java        # PROBLEM: Raw JSON text streams into the controller. We must validate structure.
│   │   │                                  # SOLUTION: Bind input bytes safely to a strongly typed record with constraints.
│   │   └── 📄 AuthResponseDto.java        # PROBLEM: Client needs standard, secure parameters back after login.
│   │                                      # SOLUTION: A standard output payload (Tokens + Metadata).
│   │
│   ├── 📁 port
│   │   ├── 📄 ITokenProvider.java         # PROBLEM: Tight coupling to a specific JWT library (e.g., jjwt).
│   │   │                                  # SOLUTION: Define structural contracts for signing/parsing tokens.
│   │   └── 📄 IAuthService.java           # PROBLEM: The Controller shouldn't know database or mapping logic.
│   │                                      # SOLUTION: Establish business interface boundaries for security actions.
│   │
│   ├── 📁 service
│   │   ├── 📄 JwtProviderImpl.java        # PROBLEM: Tokens must be safely generated, signed, and validated.
│   │   │                                  # SOLUTION: Concretely implement HMAC-SHA256 signature logic.
│   │   ├── 📄 CustomUserDetailsService.java# PROBLEM: Spring Security needs user database schemas to match its internal structures.
│   │   │                                  # SOLUTION: Adapt our custom User entity to Spring's UserDetails contract.
│   │   └── 📄 AuthServiceImpl.java         # PROBLEM: Verifying credentials and generating tokens is a multi-step orchestration.
│   │                                      # SOLUTION: Coordinates repository checks, password hashing, and token generation.
│   │
│   ├── 📁 filter
│   │   └── 📄 JwtAuthenticationFilter.java# PROBLEM: Unauthenticated raw HTTP requests hitting protected endpoints.
│   │                                      # SOLUTION: Intercept requests, extract token, validate, populate security context.
│   │
│   ├── 📁 config
│   │   └── 📄 SecurityConfig.java         # PROBLEM: The default setup blocks everything or allows everything.
│   │                                      # SOLUTION: Hook custom filters, define endpoints permissions, lock/unlock doors.
│   │
│   └── 📁 controller
│       └── 📄 AuthController.java         # PROBLEM: The client needs an exposed REST endpoint to send credentials.
│                                          # SOLUTION: Expose login/register routes to the public.
```

---

## Part 3: The Order of Construction (The Dependency Strategy)

To build a compiler-safe security system, you must construct it **from the bottom up**—starting with configurations, ports, and data transfer adapters before linking controllers and security chains together.

```
[ Step 1: Core Infra Beans ] (PasswordEncoderConfig)
            │
            ▼
[ Step 2: Input/Output Contracts ] (DTOs & Ports/Interfaces)
            │
            ▼
[ Step 3: Mechanical Providers ] (JwtProviderImpl, CustomUserDetailsService)
            │
            ▼
[ Step 4: Core Orchestrator ] (AuthServiceImpl)
            │
            ▼
[ Step 5: Traffic Control Filter ] (JwtAuthenticationFilter)
            │
            ▼
[ Step 6: System Integration Config ] (SecurityConfig)
            │
            ▼
[ Step 7: Presentation Delivery ] (AuthController & Controller Refactoring)
```

#### Why this specific order?
*   **Step 1 & 2** establish the foundational language of our authorization system.
*   **Step 3 & 4** build the mechanics of the system. You cannot write a functional authentication orchestrator (`AuthServiceImpl`) without first instantiating the tools to hash passwords (`PasswordEncoder`), query users (`CustomUserDetailsService`), and generate signed tokens (`JwtProviderImpl`).
*   **Step 5 & 6** establish the security perimeter. The HTTP filter depends directly on your token provider to parse headers, and the security configuration requires the filter to mount it inside the web chain.
*   **Step 7** exposes the completed, secured endpoints to the public.

---

## Part 4: The Step-by-Step Blueprint

This blueprint outlines the target folder locations, file names, and architectural purpose of each component in your security system.

### Step 1: The Domain Bridge (Data Translation)
We must first teach Spring Security how to read our PostgreSQL database tables. Currently, Spring Security has no idea what your `User` entity is.

*   **Create User Details Model**
    *   **Location:** `src/main/java/com/reForm/backend/auth/service/` (or package `auth.dto`)
    *   **File:** `CustomUserDetails.java`
    *   **Why now:** This class implements Spring's `UserDetails` interface. It wraps your custom `User` entity, bridging your database model with Spring's security system.
*   **Create Database Adapter Service**
    *   **Location:** `src/main/java/com/reForm/backend/auth/service/`
    *   **File:** `CustomUserDetailsService.java`
    *   **Why now:** Implements Spring's `UserDetailsService`. This class fetches your user from `UserRepository` by email and wraps it in the `CustomUserDetails` created in the previous step.

---

### Step 2: The Cryptographic Layer (The Token Engine)
Before we can intercept requests or log users in, we must build the mechanism that generates and parses the physical token.

*   **Create Token Interface (Port)**
    *   **Location:** `src/main/java/com/reForm/backend/auth/port/`
    *   **File:** `ITokenProvider.java`
    *   **Why now:** Decouples our JWT implementation details. If you ever switch from JWTs to session cookies or OAuth2, you only have to change the implementation, not your business services.
*   **Create Token Implementation (Adapter)**
    *   **Location:** `src/main/java/com/reForm/backend/auth/service/`
    *   **File:** `JwtProviderImpl.java`
    *   **Why now:** Implements `ITokenProvider`. This class uses secret keys to sign, parse, and validate cryptographic tokens.

---

### Step 3: The Security Architecture & Gatekeeper
Now we define the overall global rules of our application (who is allowed where) and configure how to handle errors safely.

*   **Create Exception Entry Point**
    *   **Location:** `src/main/java/com/reForm/backend/core/exception/`
    *   **File:** `CustomAuthenticationEntryPoint.java`
    *   **Why now:** Implements `AuthenticationEntryPoint`. This intercepts raw, unhandled security exceptions (like token validation failures) and formats them into a clean JSON API error message before Spring throws a container default error.
*   **Configure Security Chain**
    *   **Location:** `src/main/java/com/reForm/backend/auth/config/`
    *   **File:** `SecurityConfig.java`
    *   **Why now:** Defines which routes are completely public (e.g., `/api/v1/auth/login`, `/api/v1/auth/register`) and which require authorization. It registers your CORS configurations and establishes a stateless policy (no HTTP sessions).

---

### Step 4: The Request Guard (The Filter)
We now insert our custom logic directly into Spring Security's pre-request engine to inspect incoming traffic on every secured route.

*   **Create JWT Interceptor Filter**
    *   **Location:** `src/main/java/com/reForm/backend/auth/filter/`
    *   **File:** `JwtAuthenticationFilter.java`
    *   **Why now:** Extends `OncePerRequestFilter`. This runs once per request, extracts the `Authorization` header, validates the JWT via `ITokenProvider`, loads the user via `CustomUserDetailsService`, and sets the context. We register this inside our `SecurityConfig`.

---

### Step 5: The Public Gateway (Authentication API)
With the engine, guards, and configurations operational, we finally expose the entry points for the frontend to authenticate.

*   **Create Authentication Service Port**
    *   **Location:** `src/main/java/com/reForm/backend/auth/port/`
    *   **File:** `IAuthService.java`
*   **Create Authentication Service Implementation**
    *   **Location:** `src/main/java/com/reForm/backend/auth/service/`
    *   **File:** `AuthServiceImpl.java`
    *   **Why now:** Implements `IAuthService`. This service validates passwords using BCrypt, coordinates JWT generation, and returns login DTO responses.
*   **Expose Controller Endpoints**
    *   **Location:** `src/main/java/com/reForm/backend/auth/controller/`
    *   **File:** `AuthController.java`
    *   **Why now:** Exposes HTTP endpoints (`/login`, `/register`) to the client, completing the security loop.

---

## Part 5: The Socratic System Framework

### Phase 1 — The Problem Landscape
To safely transition to stateless token-based authorization:
1.  **The Core Autowire Loop Problem:** If you instantiate `PasswordEncoder` inside your main security configuration file and also inject your security configuration settings into user service components, Spring Boot will crash with a `BeanCurrentlyInCreationException` (Circular Dependency) on startup.
2.  **The Database N+1 Context Hazard:** Inside a stateless application, if your HTTP filter queries your PostgreSQL database on *every single request* to fetch the user record and roles from the token, you have reintroduced stateful latencies, severely limiting API scale under high load.
3.  **The Secret Leakage Failure Mode:** If your application stores the raw cryptographic signing key in public version control (e.g., inside `application.yml` pushed to GitHub), your security architecture is compromised from the start.

### Phase 2 — The Mental Model
The data path for an incoming API request must flow through clear architectural layers:

```
[ Incoming HTTP Header ]
         │ (Authorization: Bearer <token>)
         ▼
[ JwtAuthenticationFilter ] ──── (Invalid Token) ───> [ 401 Unauthorized Response ]
         │ (Valid Token parsed by JwtProviderImpl)
         ▼
[ Populate SecurityContext ]
         │ (Stores authenticated Principal UUID in ThreadLocal memory)
         ▼
[ Spring Controller Method ]
         │ (SecurityContextHolder.getContext().getAuthentication())
         ▼
[ Execute Domain Logic ]
```

### Phase 3 — The Foundational Prerequisites
Before writing code, make sure you understand these specific concepts:
1.  **ThreadLocal Memory isolation:** Spring Security's context uses `ThreadLocal` storage. This means the authentication state is strictly isolated to the specific thread processing that active HTTP request, and it is automatically cleaned up when the thread finishes executing.
2.  **Standard JWT Claims:** Understand how `sub` (Subject, user UUID), `iat` (Issued At), and `exp` (Expiration Time) are verified at the byte level by your cryptographical algorithms to prevent replay attacks.

### Phase 4 — Breaking It Down (OOP + Clean Code)

#### Data Structure Contracts (DTOs)
```java
// AuthResponseDto.java
public record AuthResponseDto(
    String accessToken,
    String refreshToken,
    UUID userId,
    String email
) {}

// LoginRequestDto.java
public record LoginRequestDto(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
```

#### Interface Contracts (Ports)
```java
// ITokenProvider.java
public interface ITokenProvider {
    String generateToken(User user);
    boolean validateToken(String token);
    UUID extractUserId(String token);
}

// IAuthService.java
public interface IAuthService {
    AuthResponseDto login(LoginRequestDto dto);
    AuthResponseDto register(UserRegisterRequestDto dto);
    AuthResponseDto refresh(String refreshToken);
}
```

---

### Phase 5 — The Socratic Question

We need to make a critical design decision about how identity is held in the token:

> **If a user changes their role or username in the database, but they still hold an active, signed JWT that doesn't expire for another 6 hours, how does our stateless architecture reconcile the stale claims in their token with the fresh data in our database? What are the tradeoffs between keeping the API purely stateless (relying only on the validated claims decoded from the JWT payload) versus hitting a cache or database on every single request to verify the user is still active and unmodified?**

---

### Phase 6 — The Call to Action

To begin building this security feature step-by-step:
1.  **Provide your analysis of the Socratic Question in Phase 5.**
2.  Confirm that you are ready to construct **Step 2: The Cryptographic Layer**, and I will provide the structural design steps for your JWT token provider interfaces and implementation adapters.

---

### Pitfall Radar

*   **Weak Secret Crash:** Modern security libraries will throw an immediate runtime crash on application startup if the configured secret key is less than 256 bits (32 characters).
*   **Filter Exception Silence:** If your `JwtAuthenticationFilter` throws an uncaught exception while parsing an invalid token, it bypasses the normal controller error handlers and will return a raw HTML stack trace or a generic HTTP 500 block. We must handle exceptions gracefully inside the filter.
*   **The Unsalted Plaintext Check:** Do not attempt to write custom matching logic like `storedPassword.equals(inputPassword)`. Use only `passwordEncoder.matches()` to avoid timing attacks and security vulnerabilities.