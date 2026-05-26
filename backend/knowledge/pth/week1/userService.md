# Architectural Specification: The Service Layer (The Business Brain)

**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Why do we need a Service Layer?
The **Service Layer** is the "Brain" of your application. In a well-architected system, the **Controller** is just a "delivery driver"—it takes requests and gives responses—while the **Service** performs the actual "work" (the business logic).

### Why not put everything in the Controller?
If you put your registration logic inside the Controller, you create **"Fat Controllers."** This leads to:
*   **Zero Reusability:** If you add a CLI tool or an Admin panel later, you would have to copy-paste your registration logic.
*   **Testing Nightmares:** Testing logic inside a Controller requires mocking HTTP requests, headers, and cookies. Testing a Service method is just calling a standard Java method.
*   **Security Risk:** Your business rules (e.g., "Don't allow duplicate emails") get buried under HTTP-specific code.

---

## 2. The Power of `@Transactional` (ACID Properties)

The `@Transactional` annotation is your guarantee that your database will never be left in a broken state. It ensures your operations adhere to the **ACID** properties: **A**tomicity, **C**onsistency, **I**solation, and **D**urability.

### The "A" in ACID (Atomicity)
**Atomicity** means an operation is "All or Nothing."

#### Scenario: Registering a user and creating their workspace.
Without `@Transactional`:
1.  `userRepository.save(user)` -> **SUCCESS** (User is created in DB).
2.  `workspaceService.create(user)` -> **FAIL** (Database server crashes).
3.  **Result:** You have a "Zombified" user in your database who has no workspace. Your app crashes whenever you try to load their dashboard. **The system is now inconsistent.**

With `@Transactional`:
1.  `userRepository.save(user)` -> **SUCCESS** (Staged in RAM).
2.  `workspaceService.create(user)` -> **FAIL** (Exception thrown).
3.  **Result:** Spring Boot detects the crash. It sends a `ROLLBACK` command to PostgreSQL. The user you just created is **erased as if it never happened**. Your database remains perfectly clean.

---

## 3. Functional Programming: The `.map()` method

In modern Java, we avoid `if (user != null)` blocks because they lead to nested code. Instead, we use the `Optional.map()` method.

### The Comparison
#### The Old Way (Manual)
```java
Optional<User> userOpt = userRepository.findById(id);
if (userOpt.isPresent()) {
    User user = userOpt.get();
    return userMapper.toResponse(user);
} else {
    throw new ResourceNotFoundException();
}
```

#### The Professional Way (`.map()`)
```java
return userRepository.findById(id)
    .map(userMapper::toResponse) // "If present, transform User -> DTO"
    .orElseThrow(() -> new ResourceNotFoundException("Not found"));
```

**Why it's better:** It creates a "functional pipeline." You define *what* you want to do (find, transform, or fail) rather than *how* to manage the null-checks. It is declarative, readable, and prevents common null-reference bugs.

---

## 4. Summary Table: Service Implementation Guide

| Feature | Best Practice | Why? |
| :--- | :--- | :--- |
| **Logic Location** | Keep in Service, NOT Controller. | Keeps business rules reusable and testable. |
| **Persistence** | Use `@Transactional`. | Guarantees data integrity; prevents "partial saves." |
| **Performance** | Use `@Transactional(readOnly = true)`. | Disables Hibernate's expensive "dirty checking" for read-only lookups. |
| **Transformation** | Use MapStruct (`toResponse`). | Keeps code clean; removes manual setter/getter boilerplate. |
| **Error Handling** | Throw custom Exceptions (e.g., `ResourceNotFoundException`). | Keeps business logic clean; lets the Global Handler manage HTTP status codes. |

---

## 5. Socratic Challenge: The "Business Rule" Logic

Look at our current `registerUser` method:
```java
if (userRepository.existsByEmail(normalizedEmail)) {
    throw new IllegalArgumentException("Email already exists");
}
```

1.  **The Exception Strategy:** We are currently throwing `IllegalArgumentException`.
    *   *Question:* If we have 20 different business rules (e.g., "Password too weak", "Email taken", "Account banned"), how do you distinguish between these errors in the frontend? If the frontend always gets a `400 Bad Request`, how does the user know *which* input was wrong?
    *   *Refinement:* Should we return a specific Error Code in the exception, or should we use a standard `ProblemDetail` response as we defined in the `GlobalExceptionHandler`?

2.  **Mapping Performance:** MapStruct generates code at compile-time.
    *   *Question:* Since the code is pre-compiled, is there any reason *not* to use MapStruct for every single DTO in the project? Are there any scenarios where manual mapping is actually better?

**Once you have reflected on these, you have reached the level of a mid-to-senior backend engineer. You are now ready to build the final component: The `UserController`.**