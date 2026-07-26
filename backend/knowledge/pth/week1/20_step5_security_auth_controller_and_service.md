# Masterclass Blueprint: Step 5 — The Public Gateway (Authentication API)

This blueprint provides a technical design and implementation guide for **Step 5: The Public Gateway**. It unifies all previously constructed security files, exposes standard authentication REST endpoints, and demystifies Spring Security's internal authentication provider ecosystem.

---

## Part 1: The Unified Security Ecosystem (Connecting the Dots)

Up to this point, you have written several isolated classes. It is common to feel disoriented by how these independent blocks compile and collaborate.

Here is the connection matrix showing exactly **who uses what and why** inside the security ecosystem:

| Class/Interface | Who Uses It? | Why Does It Exist? (Architectural Purpose) |
| :--- | :--- | :--- |
| **`User.java` (Entity)** | `UserRepository` | The core database representation of our user account state. |
| **`CustomerUserDetail`** | `CustomerUserDetailService` & `AuthServiceImpl` | The wrapper that translates our domain `User` entity into standard `UserDetails` so Spring Security can read it in memory. |
| **`CustomerUserDetailService`** | `AuthenticationManager` & `JwtAuthenticationFilter` | The database access adapter. It queries PostgreSQL via `UserRepository` and returns `CustomerUserDetail`. |
| **`JwtAuthenticationFilter`** | `SecurityConfig` | Intercepts protected requests to extract and validate JWT signatures on *subsequent* API calls. |
| **`SecurityConfig`** | Spring Security Engine | The central configuration blueprint. It disables CSRF, configures CORS, sets route protections, and registers our custom filter. |
| **`ITokenProvider`** | `AuthServiceImpl` & `JwtAuthenticationFilter` | The cryptographic port. It generates tokens during login and parses claims/verifies signatures during API requests. |

---

## Part 2: Authentication vs. Verification (The Lifecycle Split)

A key area of confusion is why validation logic appears to be present in both **`AuthServiceImpl`** and **`JwtAuthenticationFilter`**.

These two classes handle completely different stages of the user's security lifecycle:

```
[ STAGE 1: Authentication (Login) ]
User submits: email & plaintext password ──► AuthServiceImpl (Checks credentials, signs token)

[ STAGE 2: Verification (Subsequent Requests) ]
Client sends: Header with JWT ─────────────► JwtAuthenticationFilter (Checks signature, grants access)
```

### 1. The Authentication Phase (Login)
*   **Trigger:** The user submits a POST request to `/api/v1/auth/login` containing their raw email and plaintext password.
*   **The Operation:** We must verify their credentials for the first time. We invoke Spring's `AuthenticationManager` to compare the submitted plaintext password with the hashed password stored in PostgreSQL.
*   **The Output:** If verified, we generate a brand-new, signed, stateless JWT token and return it to the client.

### 2. The Verification Phase (Subsequent API Requests)
*   **Trigger:** The user is already logged in. They make an API request to a protected endpoint (e.g., `GET /api/v1/workspaces`), attaching the JWT in their headers.
*   **The Operation:** The user **does not send their password again**. Instead, `JwtAuthenticationFilter` intercepts the request, reads the JWT, verifies its signature using the server's private secret key, and populates the `SecurityContextHolder`.
*   **The Output:** The request is allowed to proceed to your business controllers.

---

## Part 3: Demystifying the `DaoAuthenticationProvider`

To handle password checking during login, Spring Security uses a system called the **`DaoAuthenticationProvider`**.

### 1. What does DAO stand for?
DAO stands for **Data Access Object**. In enterprise software patterns, a DAO is a class or component designed specifically to execute raw database read/write queries.

### 2. What is `DaoAuthenticationProvider`?
It is a built-in concrete class provided by Spring Security that implements the `AuthenticationProvider` interface. Its sole responsibility is to **authenticate credentials against an external database storage layer**.

### 3. How does it work under the hood?
When you call `authenticationManager.authenticate(token)` during login, Spring Security routes execution through the following pipeline:

```
[ AuthServiceImpl ]
         │
         ▼ (Passes raw email & password)
[ AuthenticationManager ] (Managed by ProviderManager)
         │
         ▼ (Delegates to)
[ DaoAuthenticationProvider ]
         │
         ├───► 1. Calls your CustomUserDetailsService.loadUserByUsername(email)
         │        (Retrieves UserDetails containing the stored password hash)
         │
         └───► 2. Calls your PasswordEncoder.matches(plaintextPassword, passwordHash)
                  (Performs safe constant-time cryptographic hash comparison)
```

If both steps succeed, the provider returns a fully authenticated token back to the `AuthenticationManager`, which returns it to your service.

### 4. What other Providers exist?
Spring Security supports multiple authentications out-of-the-box by matching different providers:
*   **`LdapAuthenticationProvider`:** Authenticates credentials against an Active Directory or LDAP server.
*   **`JaasAuthenticationProvider`:** Connects to native Java Authentication and Authorization Services.
*   **`RememberMeAuthenticationProvider`:** Processes persistent "remember-me" browser cookies.

### 5. How does it get configured? (Removing the Magic)
You might wonder: *"Why didn't we have to write code to register `DaoAuthenticationProvider`?"*

Spring Boot uses **Autoconfiguration**. When Spring Boot starts up, its internal autoconfiguration engine scans your application and notices:
1.  You have a class in the context implementing `UserDetailsService` (`CustomerUserDetailService`).
2.  You have a bean exposing a `PasswordEncoder` (`BCryptPasswordEncoder` inside `PasswordEncoderConfig`).

Spring automatically infers your intent: *"Ah, this application stores credentials inside a database, hashes them using BCrypt, and uses this service to read them. I will automatically instantiate a `DaoAuthenticationProvider`, inject this encoder and service into it, and register it inside the central `AuthenticationManager`."*

---

## Part 4: The Complete End-to-End Visual Journey

This diagram maps out the exact flow of data across all components from registration to authenticated requests:

### 1. The Registration Flow
```
[Client] ───► AuthController ───► UserServiceImpl (registerUser) ───► UserRepository ───► [PostgreSQL]
                                       │
                                       ▼ (Encodes plaintext password)
                                PasswordEncoder
```

### 2. The Login Flow (Authentication)
```
[Client] ───► AuthController ───► AuthServiceImpl (login)
                                       │
                                       ▼ (Coordinates check)
                             AuthenticationManager
                                       │
                                       ▼ (Delegates)
                           DaoAuthenticationProvider
                                       │
                    ┌──────────────────┴──────────────────┐
                    ▼                                     ▼
       CustomerUserDetailService                   PasswordEncoder
        (Loads hash from DB)                    (Matches raw password)
                    │                                     │
                    └──────────────────┬──────────────────┘
                                       ▼ (If matched)
                                JwtTokenImpl ───► [Returns Signed JWT]
```

### 3. Subsequent Request Flow (Verification)
```
[Client with JWT] ───► JwtAuthenticationFilter ───► [SecurityContextHolder] ───► WorkspaceController
                            │                             ▲
                            ▼ (Verifies signature)        │
                      JwtTokenImpl ───────────────────────┘
```

---

## Part 5: Finalized, Fully Implemented Production Code

To maintain clean code separation:
*   **`IUserService` / `UserServiceImpl`** handles **User Identity and Domain actions** (such as registration and saving the default workspace).
*   **`IAuthService` / `AuthServiceImpl`** handles **Credential verification and Token Operations** (such as login and token refreshing).

### 1. The Authentication Service Port
*   **Location:** `src/main/java/com/reForm/backend/auth/port/IAuthService.java`

```java
package com.reForm.backend.auth.port;

import com.reForm.backend.auth.dto.AuthResponseDto;
import com.reForm.backend.auth.dto.LoginRequestDto;

public interface IAuthService {
    
    /**
     * Authenticates user credentials and generates a signed JWT token on success.
     */
    AuthResponseDto login(LoginRequestDto loginRequestDto);
}
```

---

### 2. The Authentication Service Implementation
*   **Location:** `src/main/java/com/reForm/backend/auth/service/AuthServiceImpl.java`

```java
package com.reForm.backend.auth.service;

import com.reForm.backend.auth.dto.AuthResponseDto;
import com.reForm.backend.auth.dto.LoginRequestDto;
import com.reForm.backend.auth.port.IAuthService;
import com.reForm.backend.auth.port.ITokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final ITokenProvider tokenProvider;

    @Override
    public AuthResponseDto login(LoginRequestDto loginRequestDto) {
        log.info("Attempting login verification for email: {}", loginRequestDto.email());

        // 1. Wrap raw credentials inside an unauthenticated Spring token
        UsernamePasswordAuthenticationToken unauthenticatedToken = new UsernamePasswordAuthenticationToken(
                loginRequestDto.email().toLowerCase(), // Normalize input
                loginRequestDto.password()
        );

        // 2. Delegate credential checking to the AuthenticationManager
        // This invokes DaoAuthenticationProvider, CustomUserDetailsService, and PasswordEncoder internally
        Authentication authenticatedResult = authenticationManager.authenticate(unauthenticatedToken);

        // 3. Extract the authenticated Principal details from the result
        CustomerUserDetail userDetails = (CustomerUserDetail) authenticatedResult.getPrincipal();
        log.info("Credentials matched. Generating secure token for user ID: {}", userDetails.getId());

        // 4. Generate the signed stateless JWT token
        String accessToken = tokenProvider.generateToken(userDetails);

        // 5. Extract role information for response metadata mapping
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .orElse("");

        return new AuthResponseDto(
                accessToken,
                userDetails.getId(),
                userDetails.getUsername(), // In our adapter, getUsername() returns email
                role
        );
    }
}
```

---

### 3. The Authentication Controller
*   **Location:** `src/main/java/com/reForm/backend/auth/controller/AuthController.java`

```java
package com.reForm.backend.auth.controller;

import com.reForm.backend.auth.dto.AuthResponseDto;
import com.reForm.backend.auth.dto.LoginRequestDto;
import com.reForm.backend.auth.port.IAuthService;
import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.port.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
public class AuthController {

    private final IAuthService authService;
    private final IUserService userService; // Exposes user registration business logic

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody @Valid LoginRequestDto loginRequestDto) {
        log.info("Received POST login request for: {}", loginRequestDto.email());
        AuthResponseDto responseDto = authService.login(loginRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(responseDto);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@RequestBody @Valid UserRegisterRequestDto registerRequestDto) {
        log.info("Received POST registration request for: {}", registerRequestDto.email());
        UserResponseDto responseDto = userService.registerUser(registerRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }
}
```

---

### Phase 5 — The Socratic Question

Now that Step 5 is complete, your authentication API is fully integrated and functional. As you review your work, consider this advanced architectural scenario:

> **During a user registration request (`/api/v1/auth/register`), `UserServiceImpl.registerUser()` saves the user and then immediately invokes the workspace service to build a default workspace:**
>
> ```java
> workspaceService.createWorkspace(savedUser.getId(), newWorkspaceCreateRequest);
> ```
>
> **Since `registerUser` is annotated with `@Transactional`, if the workspace database operation fails (e.g., throwing a database error), the entire user registration transaction rolls back.**
>
> **If our application scales and we integrate a third-party billing engine that must also execute immediately upon registration, should that step also live inside the transactional registration method, or should we decouple these downstream side-effects using Spring Application Events? What are the reliability and system latency tradeoffs of each approach?**

---

### Phase 6 — The Call to Action

To wrap up this milestone and move to advanced security patterns:
1.  **Formulate your analysis of the Socratic Question in Phase 5.**
2.  Provide any questions or clarifications you have about the relationship between `UserDetailsService`, `AuthenticationManager`, and `DaoAuthenticationProvider`.