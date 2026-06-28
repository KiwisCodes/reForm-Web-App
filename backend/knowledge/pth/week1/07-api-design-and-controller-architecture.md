# Architectural Specification: API Design, HTTP Protocols & Controller Architecture
**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Controller's Architectural Responsibility

In a layered architecture, the **Controller** acts as the **API Boundary** (the presentation layer). It is the entry point for all incoming network traffic.

```text
                                 THE API BOUNDARY
                                 
      CLIENT LAYER (Next.js)      │      APPLICATION CORE (Spring Boot)
   ┌───────────────────────────┐  │  ┌────────────────────────────────────┐
   │                           │  │  │                                    │
   │  Sends HTTP Request ──────┼──┼─>│ [UserController]                   │
   │  (JSON Body / URI Params) │  │  │   ├── 1. Intercepts & Validates    │
   │                           │  │  │   ├── 2. Extracts JWT Context      │
   │                           │  │  │   └── 3. Delegates to Port         │
   │                           │  │  │            │                       │
   │                           │  │  │            ▼                       │
   │  Receives JSON Response   │  │  │      [IUserService]                │
   │  (Safe DTO + Status Code) │<─┼──┼──── (Business Logic Engine)        │
   │                           │  │  │                                    │
   └───────────────────────────┘  │  └────────────────────────────────────┘
                                  │
```

### The Three Rules of Controller Design:
1.  **Zero Business Logic:** A controller must never make business decisions (such as password hashing or permission checks). It simply validates the incoming request payload, delegates the execution to the service port, and delivers the response.
2.  **Stateless Execution:** It must never store state in memory. Every request must carry its own authentication credentials (the JWT token).
3.  **Unified Data Contracts:** It must strictly consume Request DTOs and return `ResponseEntity<ResponseDto>` envelopes, ensuring database entities never escape the API boundary.

---

## 2. Deep Dive: Controller Annotations (The Switchboard)

Modern Spring Boot applications use specific metadata annotations to configure how web requests are routed, validated, and secured.

```text
                  THE CONTROLLER DECORATION PIPELINE
                  
  [ @RestController ] ─────> Combines @Controller + @ResponseBody (Always returns JSON)
  [ @RequestMapping ] ─────> Sets base path: "/api/v1/users"
  [ @CrossOrigin ]    ─────> Opens ports so Next.js (3000) can talk to Spring (8080)
  [ @Validated ]      ─────> Enables validation on Path Variables (e.g. UUID format)
```

### A. `@RestController`
*   **What it is:** A meta-annotation that combines `@Controller` and `@ResponseBody`.
*   **Why we use it:** Traditional MVC controllers return the name of a visual view template (such as an HTML file). Adding `@RestController` tells Spring: *"This controller is a REST API. Do not look for HTML files; automatically serialize all method return values directly into JSON and write them into the HTTP response body."*

### B. `@CrossOrigin` (The Port Bridge)
*   **What it is:** Configures Cross-Origin Resource Sharing (CORS) headers on the server.
*   **Why we need it:** Browsers enforce the **Same-Origin Policy (SOP)**. By default, a React client running on `http://localhost:3000` is blocked from reading responses from a Spring Boot server running on `http://localhost:8080`.

```text
                 THE CROSS-ORIGIN SECURITY BARRIER
                 
   [ Next.js Client ] (Port 3000) ──(Fetch Request)──> [ Spring Boot API ] (Port 8080)
                                                               │
   [ Browser blocks the response! ] <──(Rejects CORS)──────────┘
```

Adding `@CrossOrigin` instructs Spring to automatically append the **`Access-Control-Allow-Origin: http://localhost:3000`** header to all HTTP responses, allowing the browser to safely hand the JSON data to your React frontend.

### C. `@RequestMapping("/api/v1/users")`
*   **What it is:** Sets the base URI path mapping for all methods declared in the class.
*   **Why we use `/api/v1` (API Versioning):** In production systems, **Backward Compatibility** is absolute. If you push an API update that changes the JSON structure, any active mobile apps or external B2B clients using your old API will instantly crash. By prefixing routes with `/api/v1/` and `/api/v2/`, you can run **both versions of your code simultaneously** on the same server, ensuring existing clients stay safe while new clients migrate to the updated schemas.

### D. `@Validated` vs. `@Valid`
These annotations seem identical, but they operate at different stages of the execution pipeline:
*   **`@Valid` (Jakarta Validation):** Placed directly on method parameters. It tells Spring to trigger the validation checks (like `@NotBlank` or `@Email`) declared inside your incoming DTO records.
*   **`@Validated` (Spring Validation):** Placed on the **Class Level**. It enables Spring to perform validation checks on **Path Variables** and **Query Parameters** directly (e.g., verifying a UUID matches the correct 36-character hexadecimal format in the URL).

---

## 3. Request & Response Data Binding

### A. `@RequestBody`
*   **What it does:** Reads the raw JSON string from the incoming HTTP request body and deserializes (converts) it into your Java DTO record.
*   **When to use:** On `POST`, `PUT`, and `PATCH` requests where complex data payloads are sent by the client.

### B. `@PathVariable`
*   **What it does:** Extracts values directly from the URL path (e.g., extracting the ID from `/api/v1/users/{id}`).
*   **When to use:** To identify a specific resource for retrieval, update, or deletion.

---

## 4. HTTP Methods: PUT vs. PATCH

While both are used to update data, their database execution mechanics are fundamentally different.

```text
               PUT vs. PATCH (Database Operations)
               
   [PUT (Full Replacement)]            [PATCH (Partial Modification)]
   Input: { "username": "Hung" }       Input: { "username": "Hung" }
   
   Database row becomes:                Database row becomes:
   - Username: "Hung"                   - Username: "Hung"
   - Email: NULL 💣                     - Email: "alex@reform.app" 🛡️
   - PasswordHash: NULL 💣              - PasswordHash: "hashed_pwd_xyz" 🛡️
```

*   **`PUT` (Idempotent Replacement):** Overwrites the **entire** resource. If the client sends a `PUT` request with only `"username": "Hung"`, Hibernate will replace the entire row, setting any omitted fields (like `email` and `passwordHash`) to `null`.
*   **`PATCH` (Partial Update):** Only modifies the specific fields provided in the request payload, leaving the rest of the database row completely untouched. Because users typically only edit one or two fields at a time, we use **`PATCH`** for profile and settings updates.

---

## 5. `ResponseEntity<T>` & HTTP Status Codes

`ResponseEntity<T>` is a generic wrapper class provided by Spring. It represents the **complete HTTP response**, allowing you to control the **Status Code**, **Headers**, and **Body** of the network packet.

### The 3 Categories of HTTP Status Codes You Need to Know:

```text
 ┌──────────────────────┬──────────────────────┬──────────────────────┐
 │     2xx SUCCESS      │   4xx CLIENT ERROR   │   5xx SERVER ERROR   │
 ├──────────────────────┼──────────────────────┼──────────────────────┤
 │ HttpStatus.OK (200)  │ HttpStatus.BAD_R...  │ HttpStatus.INTERN... │
 │ HttpStatus.CREA...   │ HttpStatus.NOT_FO... │ (My code crashed,    │
 │ (Everything is good) │ (User made a mistake)│  fix the database)   │
 └──────────────────────┴──────────────────────┴──────────────────────┘
```

#### How to use `ResponseEntity` in your Controller:
```java
// 200 OK: Used for successful retrievals and updates
return ResponseEntity.ok(userResponseDto);

// 201 Created: Used for successful registrations and resource creations
return ResponseEntity.status(HttpStatus.CREATED).body(userResponseDto);

// 204 No Content: Used for successful deletions (returns 0-byte body)
return ResponseEntity.noContent().build();
```

---

## 6. The User Identity Extraction Roadmap

*   **For the MVP (Now):** Since we do not have Spring Security or JWT filters configured yet, we temporarily pass the `userId` in the URL: `GET /api/v1/users/{id}`. This allows you to test easily using **Postman** by copy-pasting UUIDs.
*   **For Production (Next Week):** We will remove `{id}` from the URL completely. The controller will automatically pull the ID from the **Security Context** using:
    ```java
    UUID loggedInUserId = SecurityUtils.getCurrentUserId();
    ```

---

## 7. The Complete Production Code

### 📄 `UserController.java`
Create this file inside `com.reForm.backend.user.controller`.

```java
package com.reForm.backend.user.controller;

import com.reForm.backend.user.dto.UserRegisterRequestDto;
import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.UserUpdateRequestDto;
import com.reForm.backend.user.port.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users") // Standard versioned base path
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000") // Connects Next.js securely
@Validated // Enables validation on Path Variables (like UUID formats)
@Slf4j
public class UserController {

    private final IUserService userService;

    /**
     * Public Registration Endpoint.
     * Status: 201 Created on success.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> registerUser(
            @Valid @RequestBody UserRegisterRequestDto request
    ) {
        log.info("REST request to register new user: {}", request.email());
        UserResponseDto response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a user profile by ID.
     * Note: UUID is validated automatically at the path level by @Validated.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserProfile(
            @PathVariable UUID id
    ) {
        log.info("REST request to fetch user profile for ID: {}", id);
        UserResponseDto response = userService.getUserProfile(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing user's profile metadata.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUserProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequestDto request
    ) {
        log.info("REST request to patch user profile for ID: {}", id);
        UserResponseDto response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deactivates/Deletes a user account.
     * Status: 204 No Content on success (no body returned).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id
    ) {
        log.info("REST request to delete user account for ID: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 🏁 Socratic Review & Homework (Answers Resolved)

1.  **What happens if you forget to write `@Valid`?**
    *   **The Outcome:** Spring will completely **ignore** the validation annotations (like `@Email` or `@Size`) inside your DTO. It will compile and run, but a user could send an empty email or a 1-character password, and Spring will pass it straight to your service, causing database constraint crashes or security vulnerabilities. **Always write `@Valid` in your controller arguments.**
2.  **How does `@Validated` on the Controller protect the database?**
    *   **The Outcome:** If a user passes `"invalid-uuid-format"` into the URL: `/api/v1/users/invalid-uuid-format`.
    *   Without `@Validated` + `UUID id`, Spring might pass that raw string to the database, causing a heavy SQL execution crash (`QuerySyntaxException`).
    *   With `@Validated`, Spring's validation engine intercepts the invalid string **before the database is ever queried** and throws a clean validation exception, protecting your database from processing junk queries.