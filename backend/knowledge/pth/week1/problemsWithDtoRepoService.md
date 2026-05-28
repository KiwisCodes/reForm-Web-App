# Architectural Specification: Workspace Domain, Query Optimization & API Contracts
**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Architectural Critiques & Security Boundaries

When designing API contracts (DTOs) and data access layers for multi-tenant collaborative applications, we must prevent security leaks and over-posting vulnerabilities at the boundaries.

```text
                              THE API SECURITY SHIELD
                              
       UNSECURED FLOW (Direct Entity Leak)          SECURED FLOW (DTO Isolation)
     ┌─────────────────────────────────────┐      ┌─────────────────────────────────────┐
     │  WorkspaceResponseDto               │      │  WorkspaceResponseDto               │
     │   - String name                     │      │   - String name                     │
     │   - User owner                      │      │   - UserResponseDto owner           │
     │     ├── String email                │      │     ├── String email                │
     │     └── String passwordHash 💣      │      │     └── [ NO PASSWORD HASH ]  🛡️     │
     └─────────────────────────────────────┘      └─────────────────────────────────────┘
```

### A. The Password Leak Trap
Accepting or returning raw JPA entities (`User`) inside child collections of other DTOs (like `WorkspaceResponseDto`) is a critical security vulnerability.
*   **The Risk:** When Jackson serializes the `WorkspaceResponseDto` into JSON, it traverses the nested `User` objects, serialize their fields, and send the `passwordHash` and entity `version` directly over the public network.
*   **The Mitigation:** Always map nested entity relationships to their respective clean, public-safe DTO representation (`UserResponseDto`).

### B. The Over-Posting Vulnerability
Including member collections (`Set<User>`) inside metadata update contracts (like `WorkspaceUpdateRequestDto`) introduces an elevation-of-privilege security loophole.
*   **The Risk:** If a workspace manager wishes to update only the workspace `name` or `description`, but the controller maps a DTO containing a list of members directly back to the database, a malicious user can intercept the HTTP payload, inject a new user ID into the JSON array, and add them to the workspace. This completely bypasses your invitation, validation, and subscription check logic.
*   **The Mitigation:** Separate metadata modification (Name/Description) from membership management (Adding/Kicking). They must be handled by separate, dedicated endpoints.

---

## 2. HTTP Contract Design: Request Bodies vs. Path Variables

Designing RESTful endpoints requires understanding where variables should be carried in the HTTP request: **Request Body (JSON)** vs. **Path Variables (URI)**.

```text
 1. Inviting a Collaborator:
    POST /api/workspaces/{id}/members
    JSON Body: { "emails": ["alex@reform.app"] }  <── Email is human-friendly
    
 2. Kicking a Collaborator:
    POST /api/workspaces/{id}/members/remove
    JSON Body: { "memberIds": ["uuid-1"] }         <── UUID is database-friendly
```

### The Request Body Rule
We only design **Request DTOs** for HTTP methods that carry a payload body (usually `POST`, `PUT`, and `PATCH`).
*   If we are performing a single-resource delete (e.g., kicking exactly one member), we do not need a DTO. We identify the targets directly inside the URI: `DELETE /api/workspaces/{workspaceId}/members/{memberId}`.
*   If we are performing **bulk operations** (adding or removing multiple members at once), we must pass a structured array inside a JSON payload body. This requires dedicated request DTOs.

### Why Invite by Email, but Kick by Database ID?
*   **During Invitation (Add):** The workspace manager does not know the internal, system-generated database `UUID` of the person they want to invite. They only know their email (e.g., `alex@reform.app`). Therefore, the entry-point contract must accept a human-readable **Email**.
*   **During Removal (Kick):** The workspace has already loaded its active member list onto the screen. The Next.js React client already has access to the metadata (including the unique UUID of each member). When the manager clicks "Kick," the frontend passes the exact database Primary Key (`UUID`) to the backend. This is highly performant, indexed, and immune to string-matching bugs (such as casing differences).

---

## 3. High-Performance Querying: Solving N+1 with `@EntityGraph`

A `Workspace` has two `LAZY` relationships: `owner` (User) and `users` (Members).

```text
                        THE N+1 MULTI-RELATION TRAP
                        
   1. The Query: Workspace workspace = repo.findById(id); [Query count: 1]
   
   2. Accessing Owner: workspace.getOwner().getEmail();
      --> SQL: SELECT * FROM users WHERE id = owner_id;   [Query count: 2]
      
   3. Accessing Members: workspace.getUsers().size();
      --> SQL: SELECT * FROM workspace_members WHERE...   [Query count: 3]
```

### The Solution: `@EntityGraph`
Instead of performing three separate database round-trips, `@EntityGraph` instructs the underlying Hibernate engine to execute a **Double Left Join** across four tables, fetching all relational metadata in a **single database query**.

```java
@EntityGraph(attributePaths = {"owner", "users"})
Optional<Workspace> findById(UUID id);
```

#### The Generated PostgreSQL Query:
```sql
SELECT 
    w.id AS workspace_id,
    w.workspace_name,
    w.workspace_description,
    u_owner.id AS owner_id,
    u_owner.email AS owner_email,
    u_owner.username AS owner_username,
    wm.user_id AS member_id,
    u_member.id AS member_uuid,
    u_member.email AS member_email,
    u_member.username AS member_username
FROM workspaces w
LEFT OUTER JOIN users u_owner 
    ON w.owner_id = u_owner.id
LEFT OUTER JOIN workspace_members wm 
    ON w.id = wm.workspace_id
LEFT OUTER JOIN users u_member 
    ON wm.user_id = u_member.id
WHERE w.id = ?;
```

---

### Why `LEFT OUTER JOIN` instead of `INNER JOIN`? (The Ghost Workspace Bug)

An `INNER JOIN` requires a match on **all** evaluated tables. If a newly registered user creates a fresh workspace, that workspace has an owner but **0 members** in the join table yet.

```text
                        INNER JOIN vs. LEFT JOIN
                        
   [INNER JOIN PIPELINE]
   workspaces Table                  workspace_members Table
   ┌───────────────────┐            ┌───────────────────┐
   │ 1. Tech Team      │ ── JOIN ──>│ [ EMPTY / NULL ]  │  <── NO MATCH!
   └───────────────────┘            └───────────────────┘
   RESULT: 0 Rows returned. The workspace completely disappears from query results!
   
   [LEFT OUTER JOIN PIPELINE]
   workspaces Table                  workspace_members Table
   ┌───────────────────┐            ┌───────────────────┐
   │ 1. Tech Team      │ ── JOIN ──>│ [ EMPTY / NULL ]  │  <── NO MATCH!
   └───────────────────┘            └───────────────────┘
   RESULT: 1 Row returned. Workspace is preserved; members collection is empty.
```

If we used an `INNER JOIN` to fetch the relationship, the query would return **0 rows** because the join table has no matches yet. The workspace would become a "Ghost"—existing in the database, but completely invisible on the user's dashboard. Using `LEFT OUTER JOIN` guarantees the workspace is returned regardless of member counts.

---

## 4. Complete Production Code Implementation

### 📄 `WorkspaceRepository.java`
```java
package com.reForm.backend.user.repository;

import com.reForm.backend.user.entity.Workspace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /**
     * Solves the N+1 problem on both the Owner (ManyToOne) and Members (ManyToMany)
     * relationships in a single database round-trip.
     */
    @Override
    @EntityGraph(attributePaths = {"owner", "users"})
    Optional<Workspace> findById(UUID id);
}
```

### 📄 `WorkspaceCreateRequestDto.java`
```java
package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceCreateRequestDto(
        @NotBlank(message = "Workspace name is required")
        @Size(min = 3, max = 50, message = "Workspace name must be between 3 and 50 characters")
        String name,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description
) {}
```

### 📄 `WorkspaceUpdateRequestDto.java`
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

### 📄 `WorkspaceAddMembersRequestDto.java`
```java
package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record WorkspaceAddMembersRequestDto(
        @NotEmpty(message = "Email list cannot be empty")
        Set<String> emails
) {}
```

### 📄 `WorkspaceRemoveMembersRequestDto.java`
```java
package com.reForm.backend.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;
import java.util.UUID;

public record WorkspaceRemoveMembersRequestDto(
        @NotEmpty(message = "Member ID list cannot be empty")
        Set<UUID> memberIds
) {}
```

### 📄 `WorkspaceResponseDto.java`
```java
package com.reForm.backend.user.dto;

import java.util.Set;
import java.util.UUID;

public record WorkspaceResponseDto(
        UUID id,
        String name,
        String description,
        UserResponseDto owner,
        Set<UserResponseDto> members
) {}
```

### 📄 `IWorkspaceService.java`
```java
package com.reForm.backend.user.port;

import com.reForm.backend.user.dto.*;
import java.util.UUID;

public interface IWorkspaceService {

    WorkspaceResponseDto createWorkspace(UUID ownerId, WorkspaceCreateRequestDto request);

    WorkspaceResponseDto getWorkspace(UUID workspaceId);

    WorkspaceResponseDto updateWorkspace(UUID workspaceId, WorkspaceUpdateRequestDto request);

    WorkspaceResponseDto addMembers(UUID workspaceId, WorkspaceAddMembersRequestDto request);

    WorkspaceResponseDto removeMembers(UUID workspaceId, WorkspaceRemoveMembersRequestDto request);
}
```

---

## 🏁 5. Socratic Review & Architectural Verification

Before you implement the service layer, ensure you can answer these verification questions:

1.  **Checking Resource Existence:** We implemented an `@EntityGraph` on the `findById` repository query.
    *   *Question:* If we only need to verify if a workspace exists (e.g., inside our validation layer), should we call `workspaceRepository.existsById(id)` or `workspaceRepository.findById(id).isPresent()`? Why is the former significantly more performant? (Hint: Think about what the database does when evaluating SQL `EXISTS` vs joining three full tables via `LEFT JOIN`).
2.  **Bulk Updates in Memory:** When bulk-adding members, we receive a list of emails.
    *   *Question:* Since `Workspace` is a **Unidirectional** relationship in our updated design, do we need to write helper methods to update both sides of the relationship in Java memory, or can we simply use `workspace.getUsers().addAll(newUsers)`? Explain why.