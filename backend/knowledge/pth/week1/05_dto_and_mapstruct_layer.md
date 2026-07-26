# Architectural Specification: The DTO Boundary & MapStruct Compiler Layer
**Document Version:** 1.2  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Philosophy of DTOs: Why Entities Must Never Escape

In an enterprise-grade modular monolith, **Database Entities must never be returned to or accepted from the API client**. We enforce this strict structural boundary using **DTOs (Data Transfer Objects)**.

```text
                                 THE API BOUNDARY
                                 
      CLIENT LAYER (Next.js)      │      APPLICATION CORE (Spring Boot)
   ┌───────────────────────────┐  │  ┌────────────────────────────────────┐
   │                           │  │  │                                    │
   │  WorkspaceCreateRequest   │──┼─>│ [WorkspaceController]              │
   │                           │  │  │      │                             │
   │                           │  │  │      ▼ (Maps DTO to Entity)        │
   │                           │  │  │ [WorkspaceServiceImpl]             │
   │                           │  │  │      │                             │
   │  WorkspaceResponseDto     │<─┼──│      ▼                             │
   │  (No password hashes!)    │  │  │ [Database Entity (PostgreSQL)]     │
   │                           │  │  │                                    │
   └───────────────────────────┘  │  └────────────────────────────────────┘
                                  │
```

### Why we enforce this boundary:
1.  **Security (Preventing Data Leaks):** Your `User` entity (which is nested inside the `Workspace` as the owner or member list) contains a `passwordHash` and a database optimistic locking `version`. Returning the entity directly risks serializing and sending password hashes or private database internals over the network.
2.  **Loose Coupling (API Stability):** If you decide to rename your database column from `workspace_name` to `title`, your database entity changes. If you do not use DTOs, this database change instantly breaks your React frontend. DTOs insulate your clients from database schema modifications.
3.  **Over-Posting Protection:** If a controller accepts an entity directly during an update, a malicious user can send a JSON payload containing `"role": "ADMIN"`. If Hibernate saves that entity, the user successfully upgrades their own permissions. DTOs restrict inputs strictly to safe, validated fields.

---

## 2. What is MapStruct & How Does it Work?

**MapStruct** is a compile-time annotation processor that automatically generates safe, high-performance Java code for converting (mapping) one Java bean to another (e.g., `Workspace` entity to `WorkspaceResponseDto`).

### The Compilation Lifecycle (The Staging Phases)

A common misconception is that mapping frameworks use reflection at runtime (which is slow). MapStruct actually **writes plain Java code for you** during your local compile phase.

```text
 ┌────────────────────────────────────────────────────────────────────────┐
 │ PHASE 1: THE COMPILE-TIME GENERATION (Inside your IDE / Maven Build)   │
 └────────────────────────────────────────────────────────────────────────┘
 
   [Step 1: Developer Writes the Interface]
   You write: 'public interface WorkspaceMapper { WorkspaceResponseDto toResponse(Workspace w); }'
                        │
                        ▼
   [Step 2: You Compile the App ('mvn compile' or Click Play in IntelliJ)]
   The Java Compiler (javac) starts. It triggers the MapStruct Annotation Processor.
                        │
                        ▼
   [Step 3: MapStruct Analyzes Your Classes]
   MapStruct compares the fields of Workspace.java and WorkspaceResponseDto.java.
                        │
                        ▼
   [Step 4: MapStruct Generates the Implementation Class]
   MapStruct writes a physical Java file named 'WorkspaceMapperImpl.java' 
   inside your '/target/generated-sources/annotations' folder containing raw, optimized Java code.
   
 ┌────────────────────────────────────────────────────────────────────────┐
 │ PHASE 2: THE RUNTIME EXECUTION (Inside the Spring Container)           │
 └────────────────────────────────────────────────────────────────────────┘
 
   [Step 5: Spring Boots Up]
   Spring scans and registers 'WorkspaceMapperImpl' as a managed Spring Bean 
   because we annotated the interface with (componentModel = "spring").
                        │
                        ▼
   [Step 6: Service Call Execution]
   Your WorkspaceServiceImpl executes: 'return workspaceMapper.toResponse(workspace);'
   Under the hood, it runs the compiled mapping class instantly with ZERO reflection.
```

---

## 3. Deep Dive: Demystifying `@Mapper` Parameters

When you define a mapper, you write annotations like `@Mapper(componentModel = "spring", uses = {...})`. Here is exactly what those parameters mean under the hood:

```java
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class},
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
```

### A. `componentModel = "spring"`
By default, MapStruct generates a plain Java class. To use it in Spring, you would have to instantiate it manually: `WorkspaceMapper mapper = Mappers.getMapper(WorkspaceMapper.class);`.
*   Setting `componentModel = "spring"` tells MapStruct to add the **`@Component`** annotation to the generated implementation class (`WorkspaceMapperImpl`).
*   This allows Spring to find it during component scanning, letting you inject it cleanly into your services using constructor injection (`private final WorkspaceMapper workspaceMapper;`).

### B. `uses = {UserMapper.class}` (Nested Mapper Delegation)
What happens if your `Workspace` entity has a nested `User owner` field and a `Set<User> users` collection, but your `WorkspaceResponseDto` needs a nested `UserResponseDto` and `Set<UserResponseDto>`?
*   By adding `uses = {UserMapper.class}`, you tell MapStruct: *"If you encounter a `User` entity that needs to be mapped to a `UserResponseDto` while mapping a Workspace, do not try to generate new code. Instead, **inject the existing `UserMapper`** and delegate that specific conversion to it."*
*   This prevents duplicate mapping logic and ensures your nested domains remain cleanly separated.

### C. `unmappedTargetPolicy = ReportingPolicy.ERROR`
This is your **Safety Net**. If your DTO has a field `name`, but your Entity has no matching field, MapStruct needs to know how to handle this mismatch.
*   `ReportingPolicy.IGNORE` (Default): Silently leaves the target field as `null`. (Highly dangerous; leads to silent bugs).
*   `ReportingPolicy.WARN`: Prints a warning during compilation but compiles anyway.
*   `ReportingPolicy.ERROR` (Enterprise Standard): **Fails the Maven build** if any target field is unmapped. This forces you to explicitly configure a `@Mapping` rule or ignore the field, preventing unmapped fields from sneaking into production.

---

## 4. The Generated Code: What MapStruct Writes Under the Hood

To prove there is no "black magic," let's look at the generated code for our complex `Workspace` mapping.

### Your Interface Code (`WorkspaceMapper.java`)
```java
package com.reForm.backend.user.mapper;

import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.entity.Workspace;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring", 
    uses = {UserMapper.class}, 
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface WorkspaceMapper {

    @Mapping(source = "workspaceName", target = "name")
    @Mapping(source = "workspaceDescription", target = "description")
    @Mapping(source = "users", target = "members")
    WorkspaceResponseDto toResponse(Workspace workspace);
}
```

### The Generated Implementation Class (`WorkspaceMapperImpl.java`)
During compilation, MapStruct generates this actual Java class inside your **`target/generated-sources/annotations/com/reForm/backend/user/mapper`** folder.

Notice how cleanly it handles the nested mappings and collections using standard Java code:

```java
package com.reForm.backend.user.mapper;

import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.entity.Workspace;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component // Generated because of componentModel = "spring"
public class WorkspaceMapperImpl implements WorkspaceMapper {

    @Autowired // Generated and injected because of uses = {UserMapper.class}
    private UserMapper userMapper;

    @Override
    public WorkspaceResponseDto toResponse(Workspace workspace) {
        if (workspace == null) {
            return null;
        }

        // 1. Map Simple Fields (Resolves different names automatically)
        UUID id = workspace.getId();
        String name = workspace.getWorkspaceName();
        String description = workspace.getWorkspaceDescription();

        // 2. Map Single Nested Entity (Delegates to injected UserMapper)
        UserResponseDto owner = userMapper.toResponse(workspace.getOwner());

        // 3. Map Collection of Entities (Delegates to helper method)
        Set<UserResponseDto> members = userSetToUserResponseDtoSet(workspace.getUsers());

        // 4. Returns the immutable Record DTO
        return new WorkspaceResponseDto(id, name, description, owner, members);
    }

    // --- HELPER METHOD GENERATED AUTOMATICALLY BY MAPSTRUCT ---
    protected Set<UserResponseDto> userSetToUserResponseDtoSet(Set<User> set) {
        if (set == null) {
            return null;
        }

        Set<UserResponseDto> set1 = new HashSet<UserResponseDto>(Math.max((int) (set.size() / .75f) + 1, 16));
        for (User user : set) {
            // Uses your UserMapper to convert each individual list item safely!
            set1.add(userMapper.toResponse(user)); 
        }

        return set1;
    }
}
```

---

## 5. Why We Choose MapStruct Over Alternatives

There are other libraries, like **ModelMapper** or Apache's **BeanUtils**. Here is why we strictly ban them in enterprise-grade microservices:

| Metric | **MapStruct** | **ModelMapper / BeanUtils** |
| :--- | :--- | :--- |
| **Mechanic** | Compile-time code generation | Runtime Java Reflection |
| **Performance** | **Instant** (Same speed as hand-written Java) | **Slow** (Inspects classes via reflection at runtime) |
| **Type Safety** | **Compile-time check** (Fails build if field matches are wrong) | **Runtime check** (Crashes in production if a type mismatch occurs) |
| **Debugging** | **Easy** (You can open the generated Java file and place breakpoints) | **Impossible** (Complex internal library reflection stack traces) |

---

## 6. Complete Production Code: The User & Workspace DTOs

We use **Java `record`** classes for our DTOs because they are **immutable**, **zero-boilerplate**, and supported natively in Java 21 without Lombok.

### A. `UserResponseDto.java`
```java
package com.reForm.backend.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(
    UUID id,
    String email,
    String username,
    String role,
    LocalDateTime createdAt
) {}
```

### B. `WorkspaceResponseDto.java`
```java
package com.reForm.backend.user.dto;

import java.util.Set;
import java.util.UUID;

public record WorkspaceResponseDto(
        UUID id,
        String name,
        String description,
        UserResponseDto owner, // Secure DTO
        Set<UserResponseDto> members // Secure DTO Set
) {}
```

### C. `WorkspaceCreateRequestDto.java`
```java
package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record WorkspaceCreateRequestDto(
        @NotNull(message = "Owner ID is required")
        UUID ownerId,

        @NotBlank(message = "Workspace name is required")
        @Size(min = 3, max = 50, message = "Workspace name must be between 3 and 50 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description
) {}
```

### D. `WorkspaceUpdateRequestDto.java`
```java
package com.reForm.backend.user.dto;

import jakarta.validation.constraints.Size;

public record WorkspaceUpdateRequestDto(
        @Size(min = 3, max = 50, message = "Workspace name must be between 3 and 50 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description
) {}
```

### E. `WorkspaceAddMemberReqestDto.java`
```java
package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record WorkspaceAddMemberReqestDto(
        @NotEmpty(message = "Must add at least 1 new member")
        Set<String> emails
) {}
```

---

## 7. The MapStruct Interface Declarations

### 📄 `UserMapper.java`
```java
package com.reForm.backend.user.mapper;

import com.reForm.backend.user.dto.UserResponseDto;
import com.reForm.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    @Mapping(target = "role", expression = "java(user.getRole() != null ? user.getRole().name() : null)")
    @Mapping(target = "workspaces", ignore = true) // Ignores bidirectional fields to prevent loops
    UserResponseDto toResponse(User user);
}
```

### 📄 `WorkspaceMapper.java`
```java
package com.reForm.backend.user.mapper;

import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.entity.Workspace;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring", 
    uses = {UserMapper.class}, 
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface WorkspaceMapper {

    @Mapping(source = "workspaceName", target = "name")
    @Mapping(source = "workspaceDescription", target = "description")
    @Mapping(source = "users", target = "members")
    WorkspaceResponseDto toResponse(Workspace workspace);
}
```

---

## 🏁 Socratic Review & Homework

1.  **The Nested Mapper Mechanism:** Look at how MapStruct automatically injects `UserMapper` into `WorkspaceMapperImpl` and uses it inside `toResponse`.
    *   *Question:* What would happen if we did **not** add `uses = {UserMapper.class}` inside `@Mapper` on `WorkspaceMapper`? How would MapStruct try to map `Set<User>` to `Set<UserResponseDto>`? (Hint: Since it doesn't know about `UserMapper`, it would fail your compilation immediately because of `ReportingPolicy.ERROR`).
2.  **Circular References:** In `UserMapper`, we explicitly wrote `@Mapping(target = "workspaces", ignore = true)`.
    *   *Question:* Why is ignoring the inverse bidirectional collection (`workspaces` inside `User`) critical to preventing infinite JSON rendering loops when serializing these objects?

*Verify your configuration. Once your project compiles cleanly, we are ready to write the business operations inside `WorkspaceServiceImpl`!*