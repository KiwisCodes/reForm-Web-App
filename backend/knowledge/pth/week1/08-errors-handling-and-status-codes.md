# Architectural Specification: Exception Handling and Global Orchestration
**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Core Philosophy: Throw in Service, Catch in Global

In a production-grade enterprise application, we separate business logic from API delivery. This is achieved by **throwing specific exceptions inside the Service Layer and letting a centralized Global Exception Handler map them to HTTP responses.**

```text
               THE CLEAN ERROR FLOW (No try-catch blocks)
               
  [1. Service Layer] ──(Throws Exception)──> [2. Spring Boot Engine]
  "User not found!"                            Intercepts & routes the crash
                                                      │
                                                      ▼
  [4. Next.js Frontend] <──(Sends JSON)─── [3. GlobalExceptionHandler]
  Receives standardized RFC-7807 JSON         Maps Exception -> HttpStatus.NOT_FOUND
```

### Why we split this responsibility:
*   **The Service Layer** is purely business logic. It does not know anything about HTTP, JSON, or web ports. It simply executes rules: *"I searched the database, the user isn't here, so I will throw a `ResourceNotFoundException`."*
*   **The Global Exception Handler (`@RestControllerAdvice`)** is part of the API layer. It acts like a centralized catcher. It intercepts the exception as it tries to escape the application, wraps it in a standard JSON format, and assigns the correct HTTP status code.

---

## 2. Understanding `HttpStatus` (The Developer's Tool)

You do **not** have to remember the exact numeric codes (like `404`, `400`, or `500`). In Spring, `HttpStatus` is a Java **`enum` (Enumeration)**.

### What is an Enum?
An enum is a pre-defined list of constant values. Instead of remembering numbers, you use highly readable English constants:
*   `404` becomes `HttpStatus.NOT_FOUND`
*   `400` becomes `HttpStatus.BAD_REQUEST`
*   `409` becomes `HttpStatus.CONFLICT`
*   `500` becomes `HttpStatus.INTERNAL_SERVER_ERROR`

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

---

## 3. The Custom Exception Decision Matrix (When to Create One)

Do you need to create a custom exception class for every single error in your project? **No.**

If you create too many, your codebase becomes cluttered. If you create too few, your API errors become generic and unhelpful. Use this **3-Rule Guide** to decide when to create a custom exception:

### Rule 1: Does it map to a unique HTTP Status Code?
If a specific business error needs to return an HTTP status code different from a standard `400 Bad Request`, create a custom exception.
*   *Example:* User not found -> `ResourceNotFoundException` -> Maps to `404 NOT FOUND`.
*   *Example:* Email already exists -> `EmailAlreadyExistsException` -> Maps to `409 CONFLICT`.

### Rule 2: Does the Frontend need to take a specific action?
If your Next.js React app needs to show a specific popup or trigger a unique flow based on the error, give it a custom exception so the JSON payload has a unique error type.
*   *Example:* `InsufficientCreditsException` -> Frontend catches this and instantly opens the "Upgrade Plan" checkout modal (Maps to `402 PAYMENT_REQUIRED`).

### Rule 3: Is it readable as a business story?
Your code should read like plain English.
*   `throw new UserAccountBannedException()` is clean, descriptive, and self-documenting.

---

### 📄 Concrete Example: `ResourceNotFoundException.java`
Create this class inside `com.reform.app.core.exception`.

```java
package com.reform.app.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception representing a missing database resource (User, Form, etc.).
 * The @ResponseStatus ensures that even if this escapes our global handler,
 * the servlet container will still default to a 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

---

## 4. The Global Exception Handler (`@RestControllerAdvice`)

To catch these exceptions globally and format them into a clean, modern standard, we use **RFC-7807 Problem Details** (the official internet standard for HTTP API errors).

### 📄 `GlobalExceptionHandler.java`
Create this file inside `com.reform.app.core.exception`.

```java
package com.reform.app.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Catches 404 Resource Not Found exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://reform.app/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Catches generic business rule violations (like bad request params).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://reform.app/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Catches @Valid Bean Validation failures (DTO constraints like @NotBlank, @Email)
     * and maps them to a structured field-by-field error map for the frontend.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request fields");
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://reform.app/errors/validation-failed"));
        problem.setProperty("timestamp", Instant.now());

        // Extract and map each individual field failure
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        
        // Inject the structured map (e.g. "email" -> "Invalid email format") into the JSON
        problem.setProperty("errors", errors);

        return problem;
    }
}
```

---

## 5. Why This Architecture is Superior

1.  **Zero Controller Boilerplate:** Without a Global Handler, every single controller method needs a messy `try-catch` block. Your controllers would double in size with redundant error-wrapping code.
2.  **API Consistency:** No matter which module crashes (Billing, Forms, or Users), the Next.js frontend **always receives the exact same JSON format** for errors. This allows your frontend team to write a single, clean error-notification hook in React.
3.  **Decoupled Domain:** The core business logic remains completely independent of web-specific classes (like `HttpServletResponse` or `ResponseEntity`), making your code highly reusable and easily testable.

---

## 🏁 6. Socratic Challenge (Resolving the Bean Validation Exception)

Let's look at how we handle `MethodArgumentNotValidException` above:
*   When a user submits a form where the email is blank, Spring automatically throws this exception *before* it ever reaches your service.
*   The handler extracts the errors and returns a key-value map: `{"email": "Email is required"}`.

*   **Question:** What happens if a user submits a registration request with **three** invalid fields at the same time (blank email, short password, blank username)?
*   *Question:* How does our `GlobalExceptionHandler`'s use of a `Map<String, String>` ensure that **all three errors are returned in a single HTTP response**, allowing the React frontend to display all three error messages under their respective input boxes at the exact same time?

**Your exception handling layer is now fully modular, secure, and production-ready. Let me know when you are ready, and we will write the `UserController` to complete the entire pipeline!**