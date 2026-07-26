# Domain Relationship Specification: Users & Workspaces
**Document Version:** 1.0  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Complete Production Code

Here is the finalized, fully-mapped code for the `User`, `Workspace`, and `Role` entities.

### 📄 `Role.java` (Enum)
```java
package com.reForm.backend.user.entity;

public enum Role {
    FORM_BUILDER, 
    FORM_FILLER, 
    ADMIN
}
```

### 📄 `User.java` (Entity)
```java
package com.reForm.backend.user.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "users", 
    indexes = {
        @Index(name = "idx_user_email", columnList = "email")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "username")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // Inverse side of the Many-to-Many relationship (Collaborator workspaces)
    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    private Set<Workspace> workspaces = new HashSet<>();
}
```

### 📄 `Workspace.java` (Entity)
```java
package com.reForm.backend.user.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "workspaces")
@Getter
@Setter
public class Workspace extends BaseEntity {

    @Column(name = "workspace_name", nullable = false)
    private String workspaceName;

    @Column(name = "workspace_description")
    private String workspaceDescription;

    // RELATIONSHIP 1: Single Owner (1-to-Many: 1 User owns many Workspaces)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // RELATIONSHIP 2: Collaboration Team (Many-to-Many: 1 Workspace has many Users)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "workspace_members", // The physical Join Table in PostgreSQL
        joinColumns = @JoinColumn(name = "workspace_id"), // Points to this Workspace ID
        inverseJoinColumns = @JoinColumn(name = "user_id") // Points to the User ID
    )
    private Set<User> members = new HashSet<>();
}
```

---

## 2. Relational Database Schema Visualization

In PostgreSQL, this architecture generates three physical tables. Note how **Ownership** and **Membership** are separated.

```text
      users Table                     workspace_members Table             workspaces Table
 ┌───────────────────┐               ┌───────────────────────┐           ┌─────────────────────────┐
 │ id (UUID) ◄───────┼───────────────┼─ user_id (FK)         │           │ id (UUID)               │
 │ email             │               │ workspace_id (FK) ────┼──────────►│ owner_id (FK to users)  │
 │ password_hash     │               └───────────────────────┘           │ workspace_name          │
 │ username          │               (Resolves the Many-to-Many          │ workspace_description   │
 │ role              │                Membership/Collaboration)          └─────────────────────────┘
 └───────────────────┘                                                   (Resolves the 1-to-Many
                                                                          Ownership Billing)
```

---

## 3. Annotation Reference Dictionary

| Annotation | Location | What It Does | Why We Configured It This Way |
| :--- | :--- | :--- | :--- |
| **`@Entity`** | Class | Marks the Java class as a JPA/Hibernate managed database entity. | Required for Spring Data JPA to map the class to a database. |
| **`@Table(name=...)`** | Class | Defines the exact name of the physical table in the SQL database. | **Best Practice:** Prevents database engines from generating casing/pluralization errors automatically. |
| **`@Index`** | Class | Instructs the database engine to build a B-Tree search index on specified columns. | **Performance:** Speeds up lookup operations (like searching users by email during login) from $\mathcal{O}(n)$ to $\mathcal{O}(\log n)$. |
| **`@ManyToOne`** | Property | Declares that many of *this* entity can belong to one of *that* entity. | **Workspace -> User:** Connects multiple workspaces to one billing owner. |
| **`@ManyToMany`** | Property | Declares a Many-to-Many relationship between two entities. | **Workspace <-> User:** Allows collaborative team memberships across multiple workspaces. |
| **`@JoinColumn`** | Property | Defines the physical Foreign Key column name in the database table. | **`owner_id`:** Creates the direct link column inside the `workspaces` table. |
| **`@JoinTable`** | Property | Defines the configuration for the auxiliary join mapping table. | **`workspace_members`:** Explicitly names the join table and foreign keys for database readability. |
| **`mappedBy`** | Property | Identifies the non-owning (inverse) side of a bidirectional relationship. | Tells Hibernate that the `Workspace` entity holds the master mapping, preventing Hibernate from creating duplicate join tables. |
| **`fetch = FetchType.LAZY`** | Property | Instructs Hibernate to load related data *only* when explicitly requested. | **Performance:** Prevents the database from loading heavy nested hierarchies into memory during simple select queries. |

---

## 4. Deep Dive: `Set` vs. `List` (The Performance Trap)

One of the most critical JPA performance bottlenecks is choosing a `java.util.List` instead of a `java.util.Set` for `@ManyToMany` or `@OneToMany` collections.

### A. The Core Difference: Java Collection Semantics
*   **`java.util.List` (Bag Semantics):** Allows duplicate entries.
*   **`java.util.Set` (Set Semantics):** Mathematically guarantees uniqueness; duplicates are discarded.

---

### B. The List Trap (Wiping the Slate)

Imagine a Workspace has a `List<User> members` containing 1,000 users.

Because a `List` allows duplicate entries, you can have the same user in the list twice. When this maps to your database, the join table does **not** have a primary key, meaning there are no unique IDs per row:

```text
                        workspace_members Table (No Primary Key)
                        ┌──────────────┬─────────┐
                        │ workspace_id │ user_id │
                        ├──────────────┼─────────┤
                        │ 1            │ A       │  <-- Index 0 (Duplicate)
                        │ 1            │ B       │  <-- Index 1
                        │ 1            │ A       │  <-- Index 2 (Duplicate)
                        └──────────────┴─────────┘
```

If you call `members.remove(UserA)` in your Java code, you only want to remove *one* of them.

Because the database table has no primary key row identifier, **PostgreSQL cannot distinguish between Row 1 and Row 3**.

If Hibernate ran:
```sql
DELETE FROM workspace_members WHERE workspace_id = 1 AND user_id = 'A';
```
It would accidentally **delete both rows**, violating your Java application state.

#### Hibernate's Brute-Force Fallback:
To prevent accidental data corruption, Hibernate is forced to execute a "Wipe and Rebuild" transaction:
1.  **Delete all rows** matching the parent ID:
    ```sql
    DELETE FROM workspace_members WHERE workspace_id = 1;
    ```
2.  **Insert all remaining elements** back into the database one-by-one:
    ```sql
    INSERT INTO workspace_members (1, 'B');
    INSERT INTO workspace_members (1, 'A');
    ```
*   **Total Database Operations:** **1,000 deletions + 999 insertions = 1,999 operations to remove one single user.** Under high traffic, this crashes your database pool.

---

### C. The `Set` Solution (Precision Strike)

Because a `Set` guarantees uniqueness, Hibernate is mathematically certain that there are **no duplicate rows** in the database table matching the pair `(workspace_id = 1, user_id = 'A')`.

```text
                        workspace_members Table (Set Semantics)
                        ┌──────────────┬─────────┐
                        │ workspace_id │ user_id │
                        ├──────────────┼─────────┤
                        │ 1            │ A       │  <-- Unique
                        │ 1            │ B       │  <-- Unique
                        └──────────────┴─────────┘
```

Because uniqueness is guaranteed, Hibernate can bypass the "Wipe and Rebuild" strategy entirely and execute **exactly one SQL query**:

```sql
DELETE FROM workspace_members WHERE workspace_id = 1 AND user_id = 'A';
```

*   **Total Database Operations:** **1 single query.** Performance remains flat, fast, and scalable.

---

## 🏁 5. Next Steps

Your database relations are fully optimized and secure. You have avoided the most common pitfalls in enterprise database design.

Now, we are ready to move to the **API Boundary Contract (DTOs)**.

**Your Action Item:**  
Create your DTO records inside `com.reForm.backend.user.dto`:
1.  `UserResponseDto` (to return to the frontend).
2.  `UserRegisterRequestDto` (to accept signups).
3.  `UserUpdateRequestDto` (to accept profile updates).

*Write these records, and let me know when they are ready. We will then build the MapStruct mapper to link them to your entities!*