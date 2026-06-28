# Architectural Specification: JPA Fetching, Performance Optimization & Query Derivation
**Document Version:** 1.1  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. The Core Philosophy of `JpaRepository` (The Toolkit)

Many developers waste time writing custom SQL queries for operations that Spring Data JPA handles natively. Conversely, they get frustrated trying to make Spring do complex tasks that actually require custom queries.

Think of **`JpaRepository`** as a **pre-built, inherit-only database toolkit**. The moment you write `extends JpaRepository<Workspace, UUID>`, your interface instantly inherits a massive set of highly optimized, standard CRUD functions.

### The Out-of-the-Box Toolkit (What You Inherit Natively)

You do **not** need to declare or implement these methods in your repository interfaces; they are available out-of-the-box:

| Inherited Method | What It Does | Generates Under the Hood (SQL) |
| :--- | :--- | :--- |
| **`save(S entity)`** | Saves a new record **OR** updates an existing one (based on whether the ID is present). | `INSERT INTO workspaces...` / `UPDATE workspaces...` |
| **`findById(ID id)`** | Finds a record by its Primary Key (UUID). | `SELECT * FROM workspaces WHERE id = ?;` |
| **`existsById(ID id)`** | Checks if a record exists by its Primary Key. | `SELECT count(*) > 0 FROM workspaces WHERE id = ?;` |
| **`findAll()`** | Fetches every single row inside the table. | `SELECT * FROM workspaces;` |
| **`deleteById(ID id)`** | Deletes a record by its Primary Key. | `DELETE FROM workspaces WHERE id = ?;` |
| **`count()`** | Returns the total count of rows in the table. | `SELECT count(*) FROM workspaces;` |

---

## 2. Custom Query Derivation & Property Path Traversal

### A. The Limits of Inheritance
Spring is smart, but it has no native knowledge of your custom domain columns (such as `workspaceName` or `email`). You must declare custom method signatures inside your repository interface in **three specific scenarios**:

1.  **Derived Queries (Querying by Custom Columns):** When you need to search, count, or delete data based on columns other than the primary key (e.g., finding a user by their unique `email`).
2.  **Performance Overrides (Lazy-to-Eager Tuning):** When the built-in method works but is too slow because of lazy-loading relationships. You override the built-in method (like `findById`) and attach an `@EntityGraph` to fetch the relationship in a single query (solving the N+1 problem).
3.  **Complex Queries (Advanced Operations):** When the naming convention becomes too long, or when you need advanced database-tier operations. You write these using `@Query`.

---

### B. The Query Method Derivation Grammar
When writing derived queries, the Spring Data query parser reads your method names like a strict sentence. The method naming syntax follows a strict mathematical formula:

$$\text{[Action]} + \text{[Property Path]} + \text{[Operator (Optional)]}$$

```text
               THE SPRING DATA QUERY METHOD FORMAT
               
     [ 1. THE ACTION ]  ──>  [ 2. THE PROPERTY PATH ]  ──>  [ 3. THE OPERATOR ]
     - findBy                 - Email                        - Containing
     - existsBy               - OwnerId                      - IgnoreCase
     - countBy                - WorkspaceName                - Between
```

#### 1. The Action (What do you want to do?)
This must start with one of these reserved keywords:
*   `findBy` / `getBy` / `readBy`: Returns an `Optional<Entity>`, `List<Entity>`, or `Stream<Entity>`.
*   `existsBy`: Returns a primitive `boolean`.
*   `countBy`: Returns a primitive `long`.
*   `deleteBy` / `removeBy`: Deletes rows matching the criteria.

#### 2. The Property Path (Which variable are we searching?)
This must match your Java entity variable names **exactly** (capitalized).
*   If searching `email` inside `User`: Use `ByEmail`.
*   If searching `workspaceName` inside `Workspace`: Use `ByWorkspaceName`.
*   **Nested Properties (The Chain):** If searching the `id` of a `User` who is mapped inside a `Workspace` as `owner`: Use `ByOwnerId`.

#### 3. The Operators (How should we evaluate the value?)
If you don't write an operator, Spring assumes you mean **Equals (`=`)**. You can append these keywords to do complex SQL evaluations:
*   `Containing`: SQL `LIKE %value%` (Case-sensitive partial match).
*   `IgnoreCase`: Ignores upper/lower casing differences.
*   `GreaterThan`: SQL `>`.
*   `Between`: SQL `BETWEEN value1 AND value2`.

---

### C. Property Path Traversal (Under the Hood)

If you decide to make your modular monolith database **Unidirectional** (where `Workspace` points to `User`, but `User` has zero knowledge of Workspaces), you cannot call `user.getWorkspaces()`. Instead, you must ask the `WorkspaceRepository` to find the workspaces for us.

This is made possible by **Property Path Traversal**. Let's trace how Spring Data JPA reads the repository method name `findByOwnerId(UUID ownerId)` and maps it to your Java entities:

```text
       REPOSITORY METHOD: findByOwnerId(UUID ownerId)
       
  1. findBy ─────>  Looks at: 'Workspace' class (The Repository target)
       │
       ▼
  2. Owner  ─────>  Looks inside Workspace.java for: 'private User owner;'
       │
       ▼
  3. Id     ─────>  Looks inside User.java (via BaseEntity) for: 'private UUID id;'
```

Because you have those exact attributes mapped in your Java entities, Spring compiles this path traversal into a clean, optimized SQL `INNER JOIN` in PostgreSQL:

```sql
SELECT w.* 
FROM workspaces w 
INNER JOIN users u 
    ON w.owner_id = u.id 
WHERE u.id = ?;
```

---

## 3. Fetching Strategies: `LAZY` vs. `EAGER`

To understand `FetchType.LAZY` vs. `FetchType.EAGER`, think of how you watch video content:

*   **`FetchType.LAZY` (Online Streaming / Netflix):** You only stream the specific episode you are watching **right now**. You do not download the entire 10 seasons of the show onto your hard drive.
*   **`FetchType.EAGER` (Old-school Downloads):** You click on the first episode, and your computer instantly tries to download every single season, every sequel, and the director's commentary all at once, crashing your hard drive.

In your database:
*   **`LAZY`** means: *"Only fetch the Workspace table columns. Do not load the parent User or the members list unless I explicitly ask for them in Java."*
*   **`EAGER`** means: *"Every time I look up a Workspace, automatically run SQL queries to load the Owner's full profile, and all 100 members' full profiles into RAM, even if I only need the workspace name."*

### Why we need `LAZY`? (Performance Shield)
Using `FetchType.LAZY` is your **primary shield against performance degradation**.

If you have a list of 100 workspaces, and you want to display their names on a dashboard, and you accidentally used `EAGER`:
1.  Hibernate loads the 100 workspaces.
2.  For **every single workspace**, Hibernate runs queries to fetch the Owner and the Members list.
3.  Your database executes **hundreds of queries** and loads thousands of objects into your server's RAM just to display a list of 100 text names. The server slows down to a crawl.

---

### When do we write it? (The Default Rules)

This is a **major database trap** in Java. JPA has different default settings depending on the relationship type.

```text
               JPA RELATIONSHIP DEFAULT FETCH TYPES
               
   [ TO-ONE RELATION_TYPES ]             [ TO-MANY RELATION_TYPES ]
   @ManyToOne, @OneToOne                 @OneToMany, @ManyToMany
   
   - DEFAULT: FetchType.EAGER (💣)        - DEFAULT: FetchType.LAZY (🛡️)
   - RULE: MUST manually override!       - RULE: Safe, leave as default.
```

1.  **For `@ManyToOne` and `@OneToOne` (To-One):**
    *   **The Danger:** The JPA default is **`EAGER`**.
    *   **The Rule:** You **must manually override** this by writing `(fetch = FetchType.LAZY)`.
2.  **For `@OneToMany` and `@ManyToMany` (To-Many):**
    *   **The Danger:** None. The JPA default is **`LAZY`**.
    *   **The Rule:** You can leave it blank, but it is considered best-practice to write `(fetch = FetchType.LAZY)` explicitly so other developers know your intent.

---

## 4. The N+1 Query Performance Trap (Detailed Deep-Dive)

The **N+1 Query Problem** is the absolute most common performance killer in applications using Object-Relational Mapping (ORM) frameworks.

### The Workspace/User Scenario
Imagine we have a dashboard that needs to display a list of **50 Workspaces**, along with the **username of the owner** of each workspace.

#### Step 1: The `@ManyToOne` Relationship is Unconfigured (Defaults to `EAGER`)
In your `Workspace.java` class, your relationship is declared like this:
```java
@ManyToOne // Dangerous: Defaults to EAGER!
private User owner;
```

#### Step 2: The Service queries the database
The developer calls:
```java
List<Workspace> workspaces = workspaceRepository.findAll();
```

#### Step 3: The SQL Execution (The Trap)
Hibernate is forced to load the owner for *every single workspace* immediately.

```text
               THE N+1 QUERY PERFORMANCE TRAP (EAGER)
               
   1. The Trigger: List<Workspace> workspaces = repository.findAll();
      --> SQL: SELECT * FROM workspaces; (Returns 50 workspaces) [Query count: 1]
      
   2. The Trap: For each of the 50 workspaces, Hibernate immediately fetches the owner:
      --> SQL: SELECT * FROM users WHERE id = workspace1_owner_id; [Query count: 2]
      --> SQL: SELECT * FROM users WHERE id = workspace2_owner_id; [Query count: 3]
      ...
      --> SQL: SELECT * FROM users WHERE id = workspace50_owner_id; [Query count: 51]
      
   TOTAL QUERIES FIRED: 1 (The initial query) + N (50 individual owner lookups) = 51!
```

If your list contains $N$ workspaces, you execute 1 initial query to get the workspaces, and then $N$ additional queries to load the owners. **If you have 10,000 workspaces, you execute 10,001 database queries!** This will crash your database connection pool and lag your server.

### How to Fix It
We fix this in **two steps**:

#### Step 1: Make the Relationship LAZY (Inside `Workspace.java`)
Override the dangerous default:
```java
@ManyToOne(fetch = FetchType.LAZY) // Now it only fetches the owner when explicitly requested!
private User owner;
```

#### Step 2: Optimize the Specific List Query (Inside `WorkspaceRepository.java`)
If the dashboard *does* need to display the owner's username, you write a specific query using `@EntityGraph` to fetch them in a single join query:
```java
@EntityGraph(attributePaths = {"owner"})
List<Workspace> findAll(); // Fetches all 50 workspaces and their owners in 1 query!
```

---

## 5. The Solution: `@EntityGraph` & `LEFT JOIN`

### Do we always need `@EntityGraph` with `LAZY`?
**No. Absolutely not.** You **only** use `@EntityGraph` when you *know* for a fact that your next line of Java code is going to use the lazy-loaded relationships.
*   **Updating Metadata (No `@EntityGraph` needed):** If you only want to change a workspace's name, you don't care who the members or owners are. You call a standard repository `findById(id)` (which has no `@EntityGraph`). It runs a super fast query on a single table.
*   **Rendering the Dashboard (Yes, `@EntityGraph` needed):** If you want to render the full workspace details screen (showing the owner's name and email, and a list of all collaborators), you call your customized `findById` with `@EntityGraph`. It performs the optimized multi-join in one database round-trip.

---

### The Generated PostgreSQL Query
When you call `workspaceRepository.findById(id)` with `@EntityGraph(attributePaths = {"owner", "users"})`, Hibernate executes a **Double Left Join** across four tables in a single database round-trip:

*   **The Left Table (The Core):** Is `workspaces`. It is the starting point of your query.
*   **The Right Tables (The Extensions):** Are the `users` table (for the owner) and the `workspace_members` join table (for the members).

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

## 6. Complete Production Code Implementation

### 📄 `Workspace.java` (Entity)

```java
package com.reForm.backend.user.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "workspaces")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workspace extends BaseEntity {

    @Column(name = "workspace_name", nullable = false)
    private String workspaceName;

    @Column(name = "workspace_description")
    private String workspaceDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "workspace_members",
            joinColumns = @JoinColumn(name = "workspace_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> users = new HashSet<>();
}
```

### 📄 `WorkspaceRepository.java` (The Data Access Layer)

```java
package com.reForm.backend.user.repository;

import com.reForm.backend.user.entity.Workspace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /**
     * Overrides the default JpaRepository findById.
     * Attaches an EntityGraph to fetch both the single owner (ManyToOne)
     * and the collaborative members list (ManyToMany) in a single left join query.
     */
    @Override
    @EntityGraph(attributePaths = {"owner", "users"})
    Optional<Workspace> findById(UUID id);

    /**
     * Path Traversal: Finds the workspace owned by a specific user.
     * SQL: INNER JOIN users u ON w.owner_id = u.id WHERE u.id = ?
     */
    @EntityGraph(attributePaths = {"owner", "users"})
    Optional<Workspace> findByOwnerId(UUID ownerId);

    /**
     * Path Traversal: Finds all workspaces where a specific user is a collaborator.
     * SQL: INNER JOIN workspace_members wm ON w.id = wm.workspace_id WHERE wm.user_id = ?
     */
    @EntityGraph(attributePaths = {"owner", "users"})
    List<Workspace> findByUsersId(UUID userId);
}
```

---

## 🏁 7. Socratic Review & Architectural Verification

1.  **The Metadata Check:** If we want to check if a workspace exists before performing a deletion inside the `WorkspaceServiceImpl`, we should write:
    ```java
    if (!workspaceRepository.existsById(workspaceId)) { ... }
    ```
    *   *Question:* Why is calling `existsById` vastly faster than calling `findById`? What does the SQL look like for both? (Hint: Think about what the database does when evaluating a lightweight SQL `EXISTS` check vs loading all table columns and relationships via `LEFT JOIN`).
2.  **Renaming Variables:** Imagine you decide to rename the field inside `Workspace.java` from `private User owner;` to `private User creator;`.
    *   *Question:* If you change that field name in the entity, what must you rename your repository method to so it doesn't crash on startup?
3.  **The Case-Insensitive Search:** Write the exact repository method name required to search workspaces by description, matching partially and ignoring case.
    *   *Entity Field:* `private String workspaceDescription;`

*Study these questions carefully. Once you have finalized your conceptual understanding, we are ready to write the business logic inside `WorkspaceServiceImpl`!*