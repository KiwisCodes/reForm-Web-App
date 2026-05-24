In an enterprise-grade Spring Boot application, **we strictly throw exceptions in the Service layer and let a Global Catcher handle the HTTP mapping.**

This is a key architectural separation of concerns:
*   **The Service Layer** is pure business logic. It does not know anything about HTTP, JSON, or status codes. It simply says: *"I searched, the user isn't here, so I will throw a `ResourceNotFoundException`."*
*   **The Global Exception Handler (`@ControllerAdvice`)** is part of the API layer. It sits in your `core/exception/GlobalExceptionHandler.java` file. Its only job is to watch your app, catch those exceptions, and map them to HTTP status codes (like `404 Not Found` or `400 Bad Request`).

---

### 1. How the Flow Works (Visualized)

```text
 1. Service throws exception:
    "User not found!" (ResourceNotFoundException)
         │
         ▼ (Escapes the Service & Controller)
 2. GlobalExceptionHandler intercepts:
    "Ah, a ResourceNotFoundException! That maps to a 404."
         │
         ▼ (Formats a standardized JSON payload)
 3. Client receives clean, readable JSON:
    {
      "type": "https://reform.app/errors/not-found",
      "title": "Resource Not Found",
      "status": 404,
      "detail": "User not found with id: a1b2c3d4...",
      "timestamp": "2026-03-29T14:30:00"
    }
```

---

### 2. The Global Catcher (`GlobalExceptionHandler.java`)
This is how your `core/exception/GlobalExceptionHandler.java` is designed. It translates raw Java exceptions into standard **RFC-7807 Problem Details** (the modern web standard for API errors).

```java
package com.reform.app.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice // Catches exceptions from ALL controllers
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        // ProblemDetail is Spring 3's built-in RFC-7807 error format
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://reform.app/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://reform.app/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
```

---

### 🧠 3. Why is this superior to using `try/catch` in the Controller?

1.  **Zero Boilerplate:** If you don't have this, every single controller method will need a messy `try-catch` block. Your controllers would be twice as long and hard to read.
2.  **API Consistency:** No matter which module crashes (Billing, Forms, or Users), the frontend **always receives the exact same JSON format** for errors. This makes it incredibly easy for your Next.js team to build a single "Toast Notification" or error handler in React.
3.  **Decoupling HTTP:** If you ever wanted to reuse the `UserServiceImpl` for a console tool or a background Cron job, it would work perfectly because it doesn't rely on web-specific classes like `ResponseEntity` or `HttpServletResponse`.

---

### 🏁 4. Socratic Challenge

Think about this scenario:

*   During registration, the validation annotation on `UserRegisterRequest` catches a mistake (e.g., the email is missing `@` or the password is too short).
*   This validation check happens **before** your service code is even reached.
*   *Question:* Which exception does Spring throw when bean validation fails? How can we override it in our `GlobalExceptionHandler` to return a list of specific field validation errors (e.g., `"email": "Must be a valid email"`) to the frontend?

*Once you have contemplated this, we are ready to build the final layer of the User Module: the `UserController`!*