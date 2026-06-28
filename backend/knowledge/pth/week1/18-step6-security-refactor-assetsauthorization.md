## Part 1: The Global Architectural Impact (What Actually Changes?)

Integrating security into your existing modules (Workspaces, Forms, Submissions, Billing) requires shifting your architecture from **unverified parameter passing** to **secure context extraction**.

In your previous insecure implementation, every controller method required a `@RequestParam UUID requesterId`. The client could send *any* random UUID, and your services would trust it.

### Subsequent Request Lifecycle Flow

Now, the client no longer supplies a `requesterId`. The identity is derived from the verified cryptographic token:

```
[ Client Request ] ──► (Header: Bearer <token>)
                             │
                             ▼
               [ JwtAuthenticationFilter ]
                             │
                             ├─► 1. Verifies Token Signature
                             ├─► 2. Extracts Email
                             ├─► 3. Loads CustomerUserDetail (Wrapper)
                             └─► 4. Places Wrapper in SecurityContextHolder
                                             │
                                             ▼
                               [ SecurityFilterChain ]
                                             │ (Access Allowed)
                                             ▼
                                [ WorkspaceController ]
                             (Injects CustomerUserDetail via
                              @AuthenticationPrincipal)
                                             │
                                             ▼ (Passes Secure User ID)
                                [ WorkspaceServiceImpl ]
```

---

### Folder Structural Changes

To support clean architecture boundaries, the only layers that undergo modification are the **Controllers** (to extract identity from Spring's Context) and the **Service Implementations** (to enforce instance-level authorization rules).

Your core JPA entities, database repositories, and ports remain unchanged:

```text
src/main/java/com/reform/app
├── 📁 auth
│   ├── 📁 config
│   │   └── 📄 SecurityConfig.java          # IMPACT: Added @EnableMethodSecurity to activate SpEL.
│   │
│   └── 📁 security                         # NEW DIRECTORY: Houses all instance-level secure guard beans.
│       ├── 📄 WorkspaceSecurity.java       # Checks if active principal is owner/member of a workspace.
│       ├── 📄 FormSecurity.java            # Checks if principal has designer rights to a form.
│       ├── 📄 SubmissionSecurity.java      # Enforces visibility constraints on submitted payloads.
│       └── 📄 BillingSecurity.java         # Checks credit balances before executing AI tasks.
│
├── 📁 user
│   ├── 📁 controller
│   │   ├── 📄 UserController.java          # IMPACT: Exposes /me routes using @AuthenticationPrincipal.
│   │   └── 📄 WorkspaceController.java     # IMPACT: Dropped insecure requesterId params; added @PreAuthorize.
│   │
│   └── 📁 service
│       └── 📄 WorkspaceServiceImpl.java    # IMPACT: Removed manual verifyOwner() validations; uses SpEL.
```

---

## Part 2: Security Architecture: Why Obfuscation is Not Authorization

### The "Alice, Bob, and UUID" Scenario
You suggested that because workspace IDs are long, random UUIDs (e.g., `7a2d-4f9e-bc91`), Alice cannot access Bob's workspace because she cannot guess his UUID. Therefore, we shouldn't need `verifyOwner()`.

**This is a critical security vulnerability known as Security through Obfuscation.**

While Bob's UUID is unguessable, it is not secret. It is exposed in multiple places:
*   Browser history and network logs.
*   Frontend router states (e.g., `https://reform.app/workspaces/7a2d-4f9e-bc91`).
*   Database dumps or leaked logs.
*   Accidental sharing (e.g., sending a screenshot of a URL).

If Alice obtains Bob’s workspace UUID and your backend does not execute an explicit ownership/membership check, **Alice can read and modify Bob's entire workspace**. The JWT filter only proves that Alice is Alice; it does not check if Alice has been granted access to Bob's resources.

---

### RBAC vs. CBAC (Resource-Level Authorization)

Global security frameworks use two distinct paradigms of access control:

| Access Model | Focus | Standard Spring Expression | Business Logic Example |
| :--- | :--- | :--- | :--- |
| **RBAC** (Role-Based) | Global permissions tied to user identity. | `@PreAuthorize("hasRole('ADMIN')")` | "Is the caller allowed to click the billing configuration page?" |
| **CBAC** (Context/Resource) | Instance-level relationships between user and data. | `@PreAuthorize("@workspaceSecurity.isMember(#workspaceId)")` | "Does this specific user have permission to delete this specific workspace?" |

Spring Security's default roles (`ROLE_ADMIN`, `ROLE_FORM_BUILDER`) are global. They cannot solve CBAC problems because they do not know about database relationships. To solve this without duplicating boilerplate database checks inside our services, we use **Spring Expression Language (SpEL)**.

---

## Part 3: SpEL and Custom Security Evaluators

### How SpEL Evaluates Security Under the Hood

When you annotate a service method:
```java
@PreAuthorize("@workspaceSecurity.isMember(#workspaceId)")
```
Spring executes the following validation pipeline:

```
[ Method getWorkspace(workspaceId) Invoked ]
                     │
                     ▼
  [ SpEL Parser parses expression ] ──► Extracts value of #workspaceId parameter
                     │
                     ▼
  [ Locates Spring Bean: "workspaceSecurity" ]
                     │
                     ▼
  [ Executes workspaceSecurity.isMember(workspaceId) ]
                     │
         ┌───────────┴───────────┐
         ▼ (Returns true)        ▼ (Returns false)
  [ Execute Method ]      [ Throw AccessDeniedException ]
                                 │
                                 ▼
                     [ Intercepted by Filter ]
                     [ Returns HTTP 403 Forbidden ]
```

---

### The Concurrency Mystery: How does the Bean know who is calling?

If your application has 10,000 users executing requests concurrently, how does a single Spring Bean (`@Component("workspaceSecurity")`) know which user is calling?

The answer lies in **ThreadLocal Isolation**. Inside your security evaluator bean, we read the user identity from Spring Security's static context:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
CustomerUserDetail currentUser = (CustomerUserDetail) auth.getPrincipal();
```
Because `SecurityContextHolder` utilizes `ThreadLocal` storage under the hood, the security context is isolated to the specific CPU thread executing that single network request. The bean executes inside this isolated thread context, making it concurrent and completely thread-safe.

---

### Performance Trade-offs: Database Filtering vs. Java Streams

When validating resource access (e.g., checking if a user is a member of a workspace), there are two ways to write the validation code:

#### Approach A: In-Memory Java Streams (`.filter()`)
```java
Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
boolean isMember = workspace.getMembers().stream()
    .anyMatch(member -> member.getId().equals(currentUserId));
```
*   **The Trap:** If a corporate workspace has 5,000 members, Hibernate will execute a join query and load all 5,000 complete `User` records into the JVM's L1 Cache and memory just to run a single check. This causes severe memory bloat and latency spikes.

#### Approach B: Database Index Scan (`existsByIdAndMembersId`)
```java
boolean isMember = workspaceRepository.existsByIdAndMembersId(workspaceId, currentUserId);
```
*   **The Advantage:** This executes an optimized SQL `EXISTS` query that checks database indexes. The database returns a single boolean (`true`/`false`) over the network, consuming virtually zero server memory or CPU cycles.

> **Rule of Thumb:**
> *   Use **Database Queries** (e.g., `existsBy...`) for security checks and resource filtering.
> *   Use **Java Streams and Predicates** only for in-memory business calculations when the dataset is already loaded and guaranteed to be small.

---

### Why Custom Dedicated Security Beans Win

Spring Security provides a built-in interface called `PermissionEvaluator` that allows you to write:
```java
@PreAuthorize("hasPermission(#workspaceId, 'Workspace', 'WRITE')")
```
However, we avoid this native approach in enterprise architectures:

*   **Loss of Type Safety:** Passing `"Workspace"` and `"WRITE"` as raw string literals prevents compile-time checks. If you rename your `Workspace` class, the IDE will not catch the broken string reference.
*   **No IDE Autocompletion:** You cannot ctrl+click on `hasPermission` to jump to the implementation, making large codebases difficult to navigate.
*   **Custom Dedicated Beans** (our approach) use standard Spring bean references. They are type-safe, checked at compile-time, support IDE navigation, and keep security checks highly readable.

---

## Part 4: The Security Evaluator Directory Structure

To keep security boundaries clean, we organize all our context-based authorization beans inside the new `auth/security` folder.

```text
com.reForm.backend.auth.security
├── 📄 WorkspaceSecurity.java     # Verifies workspace ownership and team membership.
├── 📄 FormSecurity.java          # Verifies form designers and block editors.
├── 📄 SubmissionSecurity.java    # Restricts submission reviews to authorized managers.
└── 📄 BillingSecurity.java       # Checks ledger balances before executing AI tasks.
```

---

## Part 5: Refactoring Blueprint (Annotated Code)

Let's refactor the **Workspace** module to use our new stateless security configuration.

### 1. Enabling Method Security in `SecurityConfig.java`
To activate the `@PreAuthorize` annotations across your application, add the `@EnableMethodSecurity` annotation to your central configuration class:

```java
// Denotes changes:
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // ADDED: Activates method-level security (SpEL evaluation)
@RequiredArgsConstructor
public class SecurityConfig { ... }
```

---

### 2. Implementing the Workspace Security Bean
*   **Location:** `src/main/java/com/reForm/backend/auth/security/WorkspaceSecurity.java`

```java
package com.reForm.backend.auth.security;

import com.reForm.backend.auth.service.CustomerUserDetail;
import com.reForm.backend.user.entity.Workspace;
import com.reForm.backend.user.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("workspaceSecurity") // Declares the custom bean name used inside @PreAuthorize expressions
@RequiredArgsConstructor
public class WorkspaceSecurity {

    private final WorkspaceRepository workspaceRepository;

    /**
     * Checks if the currently authenticated user is the owner of the workspace.
     */
    public boolean isOwner(UUID workspaceId) {
        UUID currentUserId = getCurrentUserId();
        
        // Use an optimized database check instead of loading the entire Workspace object
        return workspaceRepository.existsByIdAndOwnerId(workspaceId, currentUserId);
    }

    /**
     * Checks if the currently authenticated user is either the owner or a team member.
     */
    public boolean isMember(UUID workspaceId) {
        UUID currentUserId = getCurrentUserId();
        
        // Step 1: Check if the user is the owner
        boolean isOwner = workspaceRepository.existsByIdAndOwnerId(workspaceId, currentUserId);
        if (isOwner) {
            return true;
        }

        // Step 2: Check if the user is in the team membership table
        return workspaceRepository.existsByIdAndMembersId(workspaceId, currentUserId);
    }

    /**
     * Helper method to safely extract the current user ID from the ThreadLocal context.
     */
    private UUID getCurrentUserId() {
        CustomerUserDetail principal = (CustomerUserDetail) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return principal.getId();
    }
}
```

---

### 3. Refactoring `WorkspaceRepository.java`
To support our highly efficient index-based security queries, add these two methods:

```java
package com.reForm.backend.user.repository;

import com.reForm.backend.user.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findById(UUID id);
    Optional<Workspace> findByOwnerId(UUID id);

    // ADDED: Highly optimized index lookup to verify workspace ownership
    boolean existsByIdAndOwnerId(UUID workspaceId, UUID ownerId);

    // ADDED: Highly optimized index lookup to verify workspace team membership
    boolean existsByIdAndMembersId(UUID workspaceId, UUID memberId);
}
```

---

### 4. Refactoring `WorkspaceController.java`
*   **Location:** `src/main/java/com/reForm/backend/user/controller/WorkspaceController.java`

```java
package com.reForm.backend.user.controller;

import com.reForm.backend.auth.service.CustomerUserDetail;
import com.reForm.backend.user.dto.*;
import com.reForm.backend.user.port.IWorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
@Validated
public class WorkspaceController {
    private final IWorkspaceService workspaceService;

    @GetMapping("/{workspaceId}")
    // MODIFIED: Injected SpEL check. Rejects call before executing controller if not owner/member.
    @PreAuthorize("@workspaceSecurity.isMember(#workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(
            @PathVariable UUID workspaceId,
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
            @AuthenticationPrincipal CustomerUserDetail currentUser) {
        
        log.info("GET workspace by id: {}", workspaceId);
        // MODIFIED: Extracts the verified user ID from the principal object
        WorkspaceResponseDto workspaceResponseDto = workspaceService.getWorkspace(currentUser.getId(), workspaceId);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
            @AuthenticationPrincipal CustomerUserDetail currentUser,
            @RequestBody @Valid WorkspaceCreateRequestDto workspaceCreateRequestDto) {
        
        log.info("POST request create workspace: {}", workspaceCreateRequestDto);
        // MODIFIED: Extracts the verified user ID from the principal object
        WorkspaceResponseDto workspaceResponseDto = workspaceService.createWorkspace(currentUser.getId(), workspaceCreateRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceResponseDto);
    }

    @PatchMapping("/{workspaceId}")
    // MODIFIED: Evaluates ownership check via SpEL. Rejects call if user is not the owner.
    @PreAuthorize("@workspaceSecurity.isOwner(#workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> updateWorkspace(
            @PathVariable UUID workspaceId,
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
            @AuthenticationPrincipal CustomerUserDetail currentUser,
            @RequestBody @Valid WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        
        log.info("PATCH request update workspace: {}", workspaceUpdateRequestDto);
        // MODIFIED: Extracts the verified user ID from the principal object
        WorkspaceResponseDto workspaceResponseDto = workspaceService.updateWorkspace(currentUser.getId(), workspaceId, workspaceUpdateRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @PostMapping("/{workspaceId}/members")
    // MODIFIED: Evaluates ownership check via SpEL. Only owners can invite team members.
    @PreAuthorize("@workspaceSecurity.isOwner(#workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> addMember(
            @PathVariable UUID workspaceId,
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
            @AuthenticationPrincipal CustomerUserDetail currentUser,
            @RequestBody @Valid WorkspaceAddMemberRequestDto workspaceAddMemberRequestDto) {
        
        log.info("PATCH request add member workspace: {}", workspaceAddMemberRequestDto);
        // MODIFIED: Extracts the verified user ID from the principal object
        WorkspaceResponseDto workspaceResponseDto = workspaceService.addMembers(currentUser.getId(), workspaceId, workspaceAddMemberRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @DeleteMapping("/{workspaceId}/members")
    // MODIFIED: Evaluates ownership check via SpEL. Only owners can remove team members.
    @PreAuthorize("@workspaceSecurity.isOwner(#workspaceId)")
    public ResponseEntity<WorkspaceResponseDto> deleteMember(
            @PathVariable UUID workspaceId,
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
            @AuthenticationPrincipal CustomerUserDetail currentUser,
            @RequestBody @Valid WorkspaceDeleteMemberRequestDto workspaceDeleteMemberRequestDto) {
        
        log.info("PATCH request delete member workspace: {}", workspaceDeleteMemberRequestDto);
        // MODIFIED: Extracts the verified user ID from the principal object
        WorkspaceResponseDto workspaceResponseDto = workspaceService.deleteMembers(currentUser.getId(), workspaceId, workspaceDeleteMemberRequestDto);
        return ResponseEntity.status(HttpStatus.OK).body(workspaceResponseDto);
    }

    @DeleteMapping("/{workspaceId}")
    // MODIFIED: Evaluates ownership check via SpEL. Only owners can delete workspaces.
    @PreAuthorize("@workspaceSecurity.isOwner(#workspaceId)")
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable UUID workspaceId,
            // MODIFIED: Dropped requesterId query param. Securely injects authenticated wrapper instead.
            @AuthenticationPrincipal CustomerUserDetail currentUser) {
        
        log.info("DELETE request delete workspace: {}", workspaceId);
        // MODIFIED: Extracts the verified user ID from the principal object
        workspaceService.deleteWorkspace(currentUser.getId(), workspaceId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
```

---

### 5. Refactoring `WorkspaceServiceImpl.java`
*   **Location:** `src/main/java/com/reForm/backend/user/service/WorkspaceServiceImpl.java`

```java
package com.reForm.backend.user.service;

import com.reForm.backend.core.exception.ResourceNotFoundException;
import com.reForm.backend.user.dto.*;
import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.entity.Workspace;
import com.reForm.backend.user.mapper.WorkspaceMapper;
import com.reForm.backend.user.port.IWorkspaceService;
import com.reForm.backend.user.repository.UserRepository;
import com.reForm.backend.user.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements IWorkspaceService {
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;

    @Override
    @Transactional
    public WorkspaceResponseDto createWorkspace(
            UUID requesterId,
            WorkspaceCreateRequestDto workspaceCreateRequestDto) {
        log.info("Create new workspace");
        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + requesterId));

        Workspace workspace = Workspace.builder()
                .owner(user)
                .name(workspaceCreateRequestDto.name())
                .description(workspaceCreateRequestDto.description())
                .build();
        log.info("Workspace created");

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto updateWorkspace(
            UUID requesterId,
            UUID workspaceId,
            WorkspaceUpdateRequestDto workspaceUpdateRequestDto) {
        log.info("Update workspace");
        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));

        // REMOVED: verifyOwner(workspace, requesterId);
        // This is now enforced securely via @PreAuthorize in the Controller before this method runs.

        if(workspaceUpdateRequestDto.name() != null && !workspaceUpdateRequestDto.name().isBlank()) {
            workspace.setName(workspaceUpdateRequestDto.name());
        }
        if(workspaceUpdateRequestDto.description() != null && !workspaceUpdateRequestDto.description().isBlank()) {
            workspace.setDescription(workspaceUpdateRequestDto.description());
        }
        log.info("Workspace updated");
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public void deleteWorkspace(
            UUID requesterId,
            UUID workspaceId) {
        log.info("Delete workspace");
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        
        // REMOVED: verifyOwner(workspace, requesterId);
        // Enforced securely via @PreAuthorize in the Controller.
        
        workspaceRepository.delete(workspace);
        log.info("Workspace deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponseDto getWorkspace(
            UUID requesterId,
            UUID workspaceId){
        log.info("Get workspace by id");
        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        
        // REMOVED: verifyOwner(workspace, requesterId);
        // Enforced securely via @PreAuthorize in the Controller.

        return workspaceMapper.toWorkspaceResponseDto(workspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto addMembers(
            UUID requesterId,
            UUID workspaceId,
            WorkspaceAddMemberRequestDto workspaceAddMemberRequestDto) {
        Set<String> emails = workspaceAddMemberRequestDto.emails();
        Workspace workspace =  workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        
        // REMOVED: verifyOwner(workspace, requesterId);
        // Enforced securely via @PreAuthorize in the Controller.

        Set<User> newMembers = userRepository.findAllByEmailIn(emails);
        workspace.getMembers().addAll(newMembers);
        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    @Override
    @Transactional
    public WorkspaceResponseDto deleteMembers(
            UUID requesterId,
            UUID workspaceId,
            WorkspaceDeleteMemberRequestDto workspaceDeleteMemberRequestDto) {
        Set<String> emails = workspaceDeleteMemberRequestDto.emails();
        log.info("Remove members from workspace");
        Workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id " + workspaceId));
        
        // REMOVED: verifyOwner(workspace, requesterId);
        // Enforced securely via @PreAuthorize in the Controller.

        Set<User> removeMembers = userRepository.findAllByEmailIn(emails);
        workspace.getMembers().removeAll(removeMembers);

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        log.info("Members removed");
        return workspaceMapper.toWorkspaceResponseDto(savedWorkspace);
    }

    // REMOVED: verifyOwner(Workspace workspace, UUID requesterId) helper method entirely!
    // Decoupling authorization logic from business logic is complete.
}
```

---

## Part 6: The Definitive Security Annotation Glossary

These core annotations manage security, context mapping, and method validation inside Spring Security applications:

| Annotation | Placement | Meaning & Purpose |
| :--- | :--- | :--- |
| **`@EnableWebSecurity`** | Configuration Class | Activates Spring Security’s web filtering filters across your HTTP pathways. |
| **`@EnableMethodSecurity`** | Configuration Class | Enables method-level security, allowing the evaluation of SpEL expressions like `@PreAuthorize`. |
| **`@PreAuthorize`** | Controller / Service Method | Evaluates a boolean expression *before* invoking the annotated method. If the expression is false, throws `AccessDeniedException`. |
| **`@PostAuthorize`** | Controller / Service Method | Evaluates a boolean expression *after* the method runs, allowing you to validate security against the returned object. |
| **`@AuthenticationPrincipal`** | Controller Method Parameter | Extracts the authenticated principal directly from Spring's `SecurityContextHolder` and injects it into the annotated parameter. |

---

### Phase 5 — The Socratic Question

Now that your security architecture supports secure context propagation and context-based access control (CBAC):

> **If a user modifies a form inside a workspace, how should we secure the Form controller endpoints?**
>
> **Should our `FormSecurity` bean execute independent database checks to verify the user belongs to the form's workspace, or should the `FormSecurity` bean delegate its verification to the existing `WorkspaceSecurity` bean by resolving the form's workspace relation first? What are the codebase maintenance and database querying tradeoffs of each approach?**

---

### Phase 6 — The Call to Action

To complete this refactoring step:
1.  **Formulate your analysis of the Socratic Question in Phase 5.**
2.  Review the refactored code for `WorkspaceController` and `WorkspaceServiceImpl`. Let me know if you are ready to apply these same patterns to the **Form** or **Submission** modules!