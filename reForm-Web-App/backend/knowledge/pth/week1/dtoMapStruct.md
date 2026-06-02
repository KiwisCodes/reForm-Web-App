# Architecture & Mapping Specification: The DTO & MapStruct Layer
**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Philosophy of DTOs: Why Entities Must Never Escape

In an enterprise-grade system, **Database Entities must never be returned to or accepted from the API client**. We enforce this boundary using **DTOs (Data Transfer Objects)**.

```text
                                 THE API BOUNDARY
                                 
      CLIENT LAYER (Next.js)      │      APPLICATION CORE (Spring Boot)
   ┌───────────────────────────┐  │  ┌────────────────────────────────────┐
   │                           │  │  │                                    │
   │  UserRegisterRequest      │──┼─>│ [Controller]                       │
   │  UserUpdateRequest        │  │  │      │                             │
   │                           │  │  │      ▼ (Maps DTO to Entity)        │
   │                           │  │  │ [Service Layer]                    │
   │                           │  │  │      │                             │
   │  UserResponse             │<─┼──│      ▼                             │
   │  (No password hashes!)    │  │  │ [Database Entity (PostgreSQL)]     │
   │                           │  │  │                                    │
   └───────────────────────────┘  │  └────────────────────────────────────┘
                                  │
```

### Why we enforce this boundary:
1.  **Security (Preventing Data Leaks):** Your `User` entity contains a `passwordHash` and a database `version`. If you return the entity directly, you risk serializing and sending password hashes or private auditing keys over the network to the client.
2.  **Loose Coupling (API Stability):** If you decide to rename your database column from `password_hash` to `encrypted_password`, your database entity changes. If you do not use DTOs, this database change instantly breaks your React frontend. DTOs insulate your clients from database changes.
3.  **Over-Posting Protection:** If a controller accepts an entity directly during an update, a malicious user could send a JSON payload containing `"role": "ADMIN"`. If Hibernate saves that entity, the user successfully upgraded their own permissions. DTOs restrict inputs strictly to safe, validated fields.

---

## 2. What is MapStruct & How Does it Work?

**MapStruct** is a compile-time annotation processor that automatically generates safe, high-performance Java code for converting (mapping) one Java bean to another (e.g., `User` entity to `UserResponse` DTO).

### The Compilation Lifecycle
A common misconception is that MapStruct uses reflection at runtime (which is slow). MapStruct actually **writes plain Java code for you** during the compilation phase.

```text
                      MAPSTRUCT COMPILE-TIME PIPELINE
                      
  1. Developer Writes           2. Java Compiler runs          3. MapStruct Generates
  'UserMapper.java' (Interface)    Annotation Processor         'UserMapperImpl.class'
                                                                    (Plain Java Code)
                                                                            │
                                                                            ▼
  5. Super Fast Execution       <──  4. Production JAR compiled  <──  Exposes:
     Zero Reflection overhead         with standard, safe code         - toResponse()
                                                                       - toEntity()
```

#### How it eliminates manual mapping boilerplate:
*   Lombok eliminates getters/setters on your objects.
*   **MapStruct eliminates the *calls* to those getters/setters.**
*   Instead of you writing `dto.setEmail(user.getEmail())` fifty times for fifty fields, MapStruct reads the fields of both classes and generates those setter/getter calls automatically during compilation.

---

## 3. MapStruct vs. Manual Mapping: A Side-by-Side Case

Let's compare how we map a `User` entity to a `UserResponse` DTO.

### Case A: The Manual Way (Setters and Getters)
```java
public class UserService {
    public UserResponse mapToResponse(User user) {
        if (user == null) {
            return null;
        }
        
        // Manual, tedious, and highly prone to developer errors
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }
}
```
*   **The Problem:** If you add a new field (e.g., `profilePictureUrl`), you must remember to manually update this method. If you forget, the field remains `null` silently, introducing silent bugs into your API.

### Case B: The MapStruct Way
You write a simple, clean interface:
```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
```

During compilation, MapStruct automatically generates the implementation class (`UserMapperImpl.java`) inside your `target/generated-sources` folder:
```java
// Generated by MapStruct automatically at compile-time
@Component
public class UserMapperImpl implements UserMapper {
    @Override
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        
        // MapStruct compiled this safe, ultra-fast plain Java code for you
        UUID id = user.getId();
        String email = user.getEmail();
        String username = user.getUsername();
        String role = user.getRole() != null ? user.getRole().name() : null;
        LocalDateTime createdAt = user.getCreatedAt();

        return new UserResponse(id, email, username, role, createdAt);
    }
}
```

When you use **MapStruct**, you stop writing the "mapping logic" yourself. Instead, you just "call" the generated mapper.

Here is what your service layer looks like **before** vs **after** MapStruct.

### 1. The "Before" (Manual Way)
You would have to manually write `new UserResponse(...)` and manually call every getter. If your DTO changes (you add a `bio` field), you have to go back to this service method and update it. It's a maintenance nightmare.

### 2. The "After" (MapStruct Way)

First, remember your `UserMapper` interface from before:
```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
```

Now, in your `UserService`, you simply **inject** this mapper. You don't need to know how it works; you just ask it to convert the object.

```java
@Service
@RequiredArgsConstructor // Automatically generates constructor for injection
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper; // Inject the mapper

    @Override
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // YOU DON'T DO THE MANUAL MAPPING HERE!
        // You just hand the 'user' to the mapper and it gives you a DTO back.
        return userMapper.toResponse(user); 
    }
}
```

---

### Why this is superior:

1.  **Cleaner Code:** Your `UserService` is now focused purely on **Business Logic** (fetching the user, throwing exceptions, checking status), not on **Data Transformation**.
2.  **Zero-Bug Maintenance:** If you add a new field to your `User` entity and your `UserResponse` DTO, you add the field to both, rebuild the project, and MapStruct **automatically** updates the mapping logic for you. You never have to touch this method again.
3.  **Readability:** Look at that code in `getProfile`. It’s only 3 lines long. That is "Production Grade."

---

### Answering your previous "Security" question:
*   **Why is it dangerous to use MapStruct to map passwords directly?**
    Because MapStruct is a "dumb" tool—it sees `password` in your DTO and `passwordHash` in your Entity and it will happily copy the **plain text** password directly into your database.

    **The Solution:** You **never** map the password field automatically. You explicitly exclude it from your MapStruct mapping, and in your `UserServiceImpl`, you manually take the `request.password()`, run it through a `PasswordEncoder`, and then set the hashed value onto the entity manually.

### Visualization of the Workflow:

1.  **Write Interface:** You define the interface `UserMapper`.
2.  **Compile:** You run your Maven build (`mvn clean compile`).
3.  **Generation:** MapStruct looks at your `User` (the source) and `UserResponse` (the target). It generates the `UserMapperImpl.java` file in your `target/generated-sources` folder.
4.  **Spring Dependency Injection:** Since you put `@Mapper(componentModel = "spring")`, Spring automatically finds that generated implementation and makes it available to be `@Autowired` into your Service.
5.  **Runtime:** Your service calls `userMapper.toResponse(user)`, which triggers the generated code. It runs just as fast as if you had written the getters and setters by hand.

---

### Does this feel like you're ready to implement this?
Do you have your `UserMapper` interface ready in `com.reform.app.user.mapper`, or do you want to see how to handle the "Exclude Password" mapping scenario before you write it?


---

## 4. Why We Choose MapStruct Over Alternatives

There are other libraries, like **ModelMapper** or Apache's **BeanUtils**. Here is why we strictly ban them in enterprise-grade microservices:

| Metric | **MapStruct** | **ModelMapper / BeanUtils** |
| :--- | :--- | :--- |
| **Mechanic** | Compile-time code generation | Runtime Java Reflection |
| **Performance** | **Instant** (Same as manual Java code) | **Slow** (Inspects classes at runtime) |
| **Type Safety** | **Compile-time check** (Fails build if field matches are wrong) | **Runtime check** (Crashes in production if a type mismatch occurs) |
| **Debugging** | **Easy** (You can open the generated Java file and place breakpoints) | **Impossible** (Complex internal library stack traces) |

---

## 5. Production Code: The User-Module DTOs

For our MVP and scalable SaaS platform, we will use **Java `record`** classes for our DTOs.

#### Why are Java `records` perfect for DTOs?
1.  **Immutability:** Records are immutable. Once created, their fields cannot be altered. This prevents accidental modifications as data moves between controller layers.
2.  **No Boilerplate:** Records automatically generate getters, constructor, `equals()`, `hashCode()`, and `toString()` at compile time.
3.  **No Lombok Needed:** Because records are built natively into modern Java, you do not need Lombok annotations on them.

---

### A. `UserRegisterRequest.java` (Incoming Registration payload)

```java
package com.reform.app.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    String password,

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username
) {}
```

---

### B. `UserLoginRequest.java` (Incoming Authentication payload)

```java
package com.reform.app.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}
```

---

### C. `UserUpdateRequest.java` (Incoming Profile Modifications payload)

```java
package com.reform.app.user.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username
) {}
```

---

### D. `UserResponse.java` (Outgoing Safe Payload)

```java
package com.reform.app.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String username,
    String role,
    LocalDateTime createdAt
) {}
```

---

## 6. The MapStruct Interface Implementation

Create this mapper interface inside `com.reform.app.user.mapper`.

```java
package com.reform.app.user.mapper;

import com.reform.app.user.dto.UserResponse;
import com.reform.app.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring") // Allows Spring to inject this Mapper using @Autowired
public interface UserMapper {

    /**
     * Converts a database User Entity to a safe UserResponse DTO.
     */
    @Mapping(target = "role", expression = "java(user.getRole() != null ? user.getRole().name() : null)")
    UserResponse toResponse(User user);
}
```

---

## 🏁 Socratic Review & Homework

Now that we have designed the secure data transfer boundaries:

1.  **Lombok vs. Records on MapStruct:** Why does MapStruct map fields from Lombok entities to Java `records` so cleanly, even though Java records do not use standard `get` prefixes for their accessor methods (e.g., a record uses `email()` instead of `getEmail()`)?
2.  **Mapping Password Fields:** Notice that we do **not** have a mapping method in `UserMapper` that takes a `UserRegisterRequest` and directly generates a `User` entity.
    *   *Question:* Why is it a dangerous security violation to let MapStruct map `password` directly from `UserRegisterRequest` to `User.passwordHash`? What crucial security step must happen inside the `UserServiceImpl` instead?

*Analyze these security and technical questions, and once you have formulated your answers, we will proceed to write the `IUserService` and `UserServiceImpl`!*