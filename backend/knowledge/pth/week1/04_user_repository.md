# Architecture & Data Access Specification: The Repository Layer
**Document Version:** 1.3  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Dynamic Proxying: How the Interface Works

When you write `UserRepository` as an interface, Spring Boot dynamically handles the data access implementation. Under the hood, Spring uses a design pattern called **Dynamic Proxying** (specifically JDK Dynamic Proxies, built directly into the Java Standard Library).

```text
       STARTUP LIFECYCLE: THE DYNAMIC PROXY PIPELINE
       
 1. Application Starts  ──>  2. Scanner Finds            ──>  3. JDK Dynamic Proxy
    (Spring Boot Engine)       'UserRepository.java'           Generates Runtime Class
                                                                       │
                                                                       ▼
 5. Developer Injects   <──  4. Class Registered as      <──  Exposes:
    @Autowired / constructor    Spring Bean: 'userRepository'     - save()
                                                                  - findById()
                                                                  - findByEmail()
```

### The Deep Mechanics
1.  **Scanning and Metadata Registration:** During the bootstrap phase of the Spring container, the framework scans your classpath. When it encounters an interface extending `JpaRepository` (or annotated with `@Repository`), it registers its metadata inside the `ApplicationContext` as a candidate for proxy generation.
2.  **JDK Reflection Proxy Generation:** Spring uses the `java.lang.reflect.Proxy` class to generate a concrete implementation class directly in-memory at runtime. This dynamically compiled proxy class implements your `UserRepository` interface.
3.  **Method Signature Parsing (Query Derivation):** Spring reads your interface method signatures. When it encounters a method like `findByEmail(String email)`, it parses the name using an internal compiler parser:
    *   `findBy`: Directs the parser to generate a `SELECT` query targeting the mapped entity (`User`).
    *   `Email`: Directs the parser to look for a property named `email` on the `User` entity, appending an SQL `WHERE email = ?` clause.
4.  **Runtime Interception & Execution:** When your application code calls `userRepository.findByEmail("alex@reform.app")`:
    *   The call is intercepted by the JDK Proxy’s `InvocationHandler`.
    *   The handler retrieves the pre-parsed query execution plan.
    *   It delegates the actual execution to the JPA `EntityManager` (managed by Hibernate).
    *   The `EntityManager` runs the generated SQL over the JDBC connection, maps the SQL `ResultSet` columns to your physical Java `User` entity attributes, wraps it in a Java `Optional`, and returns it safely to your service.

---

## 2. Database Indexing & Constraints

### A. Database Indexing (`idx_user_email`)

Without a proper database index, finding a user by email requires a **Full Table Scan**.

```text
               DATABASE SEARCH STRATEGIES
               
   FULL TABLE SCAN (No Index)          INDEXED SEARCH (B-Tree)
   
     Row 1: alex@reform.app             [ Root Node ]
     Row 2: bob@reform.app              /          \
     Row 3: charlie@reform.app    [ Leaf Node ]  [ Leaf Node ]
     ...                           /      \          /      \
     Row 100,000: target@ref...  Target  ...     ...     ...
     
   (Database scans 100,000 rows)     (Database finds target in 3 steps)
```

#### How it Works:
An index is a highly optimized auxiliary data structure managed by the database engine. Think of it as the index at the back of a thick reference book. Instead of flipping through all 1,000 pages to find the term "WebSockets," you jump to the index, find "WebSockets," and immediately turn to page 844.

#### The Data Structure:
PostgreSQL uses **B-Tree (Balanced Tree)** indexing by default. A B-Tree organizes keys in a sorted, hierarchical structure that keeps data balanced, allowing search, sequential access, insertion, and deletion operations in logarithmic time:

$$\mathcal{O}(\log n)$$

Without an index, searching is a sequential scan, which takes linear time:

$$\mathcal{O}(n)$$

If you have 1,000,000 users, an unindexed query must scan up to 1,000,000 records. An indexed query will find the record in approximately 20 binary steps.

#### Why Declare Indexes Globally in `@Table`?
```java
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email")
})
```
While `@Column(unique = true)` implicitly instructs PostgreSQL to generate a backing unique index, declaring it explicitly within the `@Table` block is a key enterprise practice:
1.  **Observability & Debugging:** It assigns a predictable, human-readable name (`idx_user_email`) to the database index. When database engines dump slow-query logs or deadlock traces, they refer directly to the index name.
2.  **Composite Indexes:** It allows you to declare index structures across multiple columns (e.g., matching query patterns that search by email *and* status together).

---

### B. Unique Composite Constraints

A single-column uniqueness check is often not enough to preserve data integrity across relational boundaries. You frequently need **multiple columns to be unique when evaluated together**.

```text
                  COMPOSITE UNIQUE CONSTRAINT
                  (Workspace Membership Example)
                  
     Insert User A into Workspace 1  ──>  SUCCESS (First entry)
     Insert User B into Workspace 1  ──>  SUCCESS (Different User)
     Insert User A into Workspace 2  ──>  SUCCESS (Different Workspace)
     Insert User A into Workspace 1  ──>  FAILURE (Violates Constraint)
```

#### The Architecture Case:
In a multi-tenant platform like Reform, a `User` can join multiple `Workspaces`. We need to track these memberships in a relational mapping table. A user must only join a specific workspace **once**. If we do not enforce this at the database engine level, double-clicks or concurrent network requests could insert duplicate membership rows, polluting our permission model.

#### Implementing Composite Uniqueness in JPA:
```java
@Table(
    name = "workspace_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uc_user_workspace", 
            columnNames = {"user_id", "workspace_id"}
        )
    }
)
```

#### The Engine Protection:
If an operation attempts to write a duplicate mapping, the database engine blocks the write, rolls back the transaction, and throws an SQL `ConstraintViolationException`. This guarantees data integrity even if your Java code's validation logic fails under a high-concurrency race condition.

---

## 3. The N+1 Query Problem

The **N+1 Query Problem** is the most common performance bottleneck in applications using Object-Relational Mapping (ORM) frameworks like Hibernate.

```text
               THE N+1 QUERY PERFORMANCE TRAP
               
   1. The Trigger: List<User> users = repository.findAll();
      --> SQL: SELECT * FROM users; (Returns 100 users) [Query count: 1]
      
   2. The Trap: For each user, get their workspace:
      for(User u : users) {
          System.out.println(u.getWorkspace().getName());
      }
      --> SQL: SELECT * FROM workspaces WHERE id = user1_workspace_id; [Query count: 2]
      --> SQL: SELECT * FROM workspaces WHERE id = user2_workspace_id; [Query count: 3]
      ...
      --> SQL: SELECT * FROM workspaces WHERE id = user100_workspace_id; [Query count: 101]
      
   TOTAL QUERIES FIRED: 1 (The initial query) + N (100 individual workspace lookups) = 101!
```

#### Why Does This Happen?
It is a direct consequence of **Lazy Loading (`FetchType.LAZY`)**. When you query the `User` entities, Hibernate fetches only the user records. The `workspace` field on your Java object is populated with a **Hibernate Dynamic Proxy** (a shell containing only the ID of the workspace).

The moment your code accesses a property on that proxy (e.g., `workspace.getName()`), Hibernate is forced to halt execution and execute an individual SQL query to fetch the missing workspace details for that specific row. If your list contains $N$ users, you execute 1 initial query to get the users, and then $N$ additional queries to load the workspace for each user.

---

### The Dynamic Solutions

#### Solution A: `JOIN FETCH` (Explicit JPQL)
You write an explicit query instructing Hibernate to execute an SQL `JOIN` at the database level, fetching both the parent and child records in a single database round-trip.

```java
@Query("SELECT u FROM User u JOIN FETCH u.workspace WHERE u.email = :email")
Optional<User> findByEmailWithWorkspace(@Param("email") String email);
```
*   **Generated SQL:**
    ```sql
    SELECT u.*, w.* 
    FROM users u 
    INNER JOIN workspaces w ON u.workspace_id = w.id 
    WHERE u.email = ?;
    ```
*   **Query Count:** Exactly **1** query.

#### Solution B: `@EntityGraph` (Declarative JPA)
This is an elegant, Spring-specific declarative way to override lazy loading for a specific query without writing a manual JPQL query.

```java
@EntityGraph(attributePaths = {"workspace"})
Optional<User> findByEmail(String email);
```
*   **The Execution:** Spring parses this annotation and dynamically builds a `LEFT OUTER JOIN` query, pulling the related workspace data along with the user in one single database round-trip.

---

## 4. Visualizing SQL Joins

To understand how these queries execute inside the PostgreSQL engine, let us visualize the physical relationships using our `Users` and `Workspaces` datasets.

### The Sample Datasets

```text
       USERS (LEFT TABLE)                      WORKSPACES (RIGHT TABLE)
    ┌───────────────────────┐                  ┌────────────────────────┐
    │ ID  │ Name   │ WorkID │                  │ ID  │ Name             │
    ├─────┼────────┼────────┤                  ├─────┼──────────────────┤
    │ 1   │ Alex   │ 1      │                  │ 1   │ Marketing Team   │
    │ 2   │ Bob    │ 2      │                  │ 2   │ HR Team          │
    │ 3   │ Charlie│ NULL   │                  │ 3   │ Sales Team       │
    └─────┴────────┴────────┘                  └─────┴──────────────────┘
```

---

### A. INNER JOIN

An **Inner Join** evaluates both tables and returns a record **only if there is a matching key on both sides**. Any unmatched records are excluded from the result set.

*   **Business Logic:** "Fetch all users who have been assigned to a workspace, alongside their workspace details."
*   **SQL Query:**
    ```sql
    SELECT u.Name as User_Name, w.Name as Workspace_Name 
    FROM Users u 
    INNER JOIN Workspaces w ON u.WorkID = w.ID;
    ```

```text
                        INNER JOIN VISUALIZATION
                        
            Users (Left)                       Workspaces (Right)
       ┌─────────────────────┐               ┌─────────────────────┐
       │ 1. Alex  (WorkID: 1)│ ─── MATCH ───>│ 1. Marketing Team   │
       │ 2. Bob   (WorkID: 2)│ ─── MATCH ───>│ 2. HR Team          │
       │ 3. Charlie (ID: NULL)│ ── NO MATCH ──│ 3. Sales Team       │
       └─────────────────────┘               └─────────────────────┘
```

#### Query Result:
```text
    ┌───────────────────────────────────┐
    │ User_Name     │ Workspace_Name    │
    ├───────────────┼───────────────────┤
    │ Alex          │ Marketing Team    │
    │ Bob           │ HR Team           │
    └───────────────┴───────────────────┘
    * Note: Charlie is excluded because WorkID is NULL.
    * Note: "Sales Team" is excluded because no user is mapped to ID 3.
```

---

### B. LEFT OUTER JOIN

A **Left Outer Join** returns **every single record from the Left table (Users)**, regardless of whether a matching record exists on the Right side. If no match exists, the Right-side columns are populated with `NULL`.

*   **Business Logic:** "Fetch every user in the database. If they belong to a workspace, include it. If not, preserve the user record anyway."
*   **SQL Query:**
    ```sql
    SELECT u.Name as User_Name, w.Name as Workspace_Name 
    FROM Users u 
    LEFT JOIN Workspaces w ON u.WorkID = w.ID;
    ```

```text
                       LEFT OUTER JOIN VISUALIZATION
                       
            Users (Left)                       Workspaces (Right)
       ┌─────────────────────┐               ┌─────────────────────┐
       │ 1. Alex  (WorkID: 1)│ ─── MATCH ───>│ 1. Marketing Team   │
       │ 2. Bob   (WorkID: 2)│ ─── MATCH ───>│ 2. HR Team          │
       │ 3. Charlie (ID: NULL)│ ── NO MATCH ──│ [ NULL / Empty ]    │
       └─────────────────────┘               └─────────────────────┘
```

#### Query Result:
```text
    ┌───────────────────────────────────┐
    │ User_Name     │ Workspace_Name    │
    ├───────────────┼───────────────────┤
    │ Alex          │ Marketing Team    │
    │ Bob           │ HR Team           │
    │ Charlie       │ NULL              │  <-- Charlie is preserved!
    └───────────────┴───────────────────┘
```

---

### C. RIGHT OUTER JOIN

A **Right Outer Join** is the exact inverse of a Left Join. It returns **every single record from the Right table (Workspaces)**, regardless of whether any matching records exist on the Left side. If no match exists, the Left-side columns are populated with `NULL`.

*   **Business Logic:** "Fetch every workspace in the system. If users belong to them, list their names. If a workspace is empty, preserve it anyway."
*   **SQL Query:**
    ```sql
    SELECT u.Name as User_Name, w.Name as Workspace_Name 
    FROM Users u 
    RIGHT JOIN Workspaces w ON u.WorkID = w.ID;
    ```

```text
                      RIGHT OUTER JOIN VISUALIZATION
                      
            Users (Left)                       Workspaces (Right)
       ┌─────────────────────┐               ┌─────────────────────┐
       │ 1. Alex  (WorkID: 1)│ <─── MATCH ───│ 1. Marketing Team   │
       │ 2. Bob   (WorkID: 2)│ <─── MATCH ───│ 2. HR Team          │
       │ [ NULL / Empty ]    │ <── NO MATCH ──│ 3. Sales Team       │
       └─────────────────────┘               └─────────────────────┘
```

#### Query Result:
```text
    ┌───────────────────────────────────┐
    │ User_Name     │ Workspace_Name    │
    ├───────────────┼───────────────────┤
    │ Alex          │ Marketing Team    │
    │ Bob           │ HR Team           │
    │ NULL          │ Sales Team        │  <-- Sales Team is preserved!
    └───────────────────────────────────┘
```

---

### D. FULL OUTER JOIN

A **Full Outer Join** combines Left and Right joins. It returns **all records from both tables**, matching them where possible and inserting `NULL` on either side where no match exists.

*   **Business Logic:** "Fetch all users and all workspaces, maintaining matches where they exist, without losing any unmatched records from either table."
*   **SQL Query:**
    ```sql
    SELECT u.Name as User_Name, w.Name as Workspace_Name 
    FROM Users u 
    FULL OUTER JOIN Workspaces w ON u.WorkID = w.ID;
    ```

```text
                       FULL OUTER JOIN VISUALIZATION
                       
            Users (Left)                       Workspaces (Right)
       ┌─────────────────────┐               ┌─────────────────────┐
       │ 1. Alex  (WorkID: 1)│ ─── MATCH ───>│ 1. Marketing Team   │
       │ 2. Bob   (WorkID: 2)│ ─── MATCH ───>│ 2. HR Team          │
       │ 3. Charlie (ID: NULL)│ ── NO MATCH ──│ [ NULL / Empty ]    │
       │ [ NULL / Empty ]    │ ── NO MATCH ──│ 3. Sales Team       │
       └─────────────────────┘               └─────────────────────┘
```

#### Query Result:
```text
    ┌───────────────────────────────────┐
    │ User_Name     │ Workspace_Name    │
    ├───────────────┼───────────────────┤
    │ Alex          │ Marketing Team    │
    │ Bob           │ HR Team           │
    │ Charlie       │ NULL              │  <-- Charlie kept
    │ NULL          │ Sales Team        │  <-- Sales Team kept
    └───────────────────────────────────┘
```

---

### 👑 The Architectural Decision: Why Left Join for `@EntityGraph`?

When Spring Data JPA parses `@EntityGraph(attributePaths = {"workspace"})`, it generates a **`LEFT OUTER JOIN`** under the hood.

**Why does it do this?**  
If JPA used an `INNER JOIN` by default, any newly registered `User` who has not yet been assigned to a `Workspace` would have a `null` workspace ID. An `INNER JOIN` query looking for that user's record during a login attempt would return **0 rows**, blocking them from logging in. Using a `LEFT OUTER JOIN` ensures we always fetch the user's core identity data even if they do not belong to a workspace yet.

---

## 5. Custom Query Strategies (JPQL vs. Native SQL)

When Spring Data's derived query methods (like `existsByEmail`) are not expressive enough, we can implement custom query definitions.

### Option 1: JPQL (Java Persistence Query Language)
*   **The Concept:** JPQL is an object-oriented query language. It does not target database tables; instead, it targets your declared **Java Entity Classes** and their attributes.
*   **Example:**
    ```java
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.createdAt > :date")
    List<User> findCreatorsCreatedAfter(@Param("role") Role role, @Param("date") LocalDateTime date);
    ```
*   **Why use JPQL:** It is database-agnostic. Since it is parsed by Hibernate, it compiles safely even if you change the database engine underneath (e.g., migrating from Local H2 for testing to Postgres for production).

### Option 2: Native SQL Query
*   **The Concept:** Native queries are executed directly as raw SQL strings on the database engine.
*   **Example:**
    ```java
    @Query(value = "SELECT * FROM users u WHERE u.email LIKE %:domain", nativeQuery = true)
    List<User> findUsersByEmailDomain(@Param("domain") String domain);
    ```
*   **Why use Native SQL:** It is required when utilizing database-specific dialects and advanced engine capabilities, such as PostgreSQL's native JSONB query operators, window functions, and recursive common table expressions (CTEs).

---

## 6. Complete Production Code Implementation

### User.java (Entity Layer)

```java
package com.reform.app.user.entity;

import com.reform.app.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "users", 
    indexes = {
        @Index(name = "idx_user_email", columnList = "email")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_user_email", columnNames = {"email"})
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "username")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;
}
```

### UserRepository.java (Data Access Layer)

```java
package com.reform.app.user.repository;

import com.reform.app.user.entity.Role;
import com.reform.app.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Optimized check: Exposes a database-level check that avoids 
     * loading the entire entity payload into server memory.
     */
    boolean existsByEmail(String email);

    /**
     * Solves N+1 problem dynamically using an SQL LEFT OUTER JOIN
     * to fetch both User and Workspace data in a single round-trip.
     */
    @EntityGraph(attributePaths = {"workspace"})
    Optional<User> findByEmail(String email);

    /**
     * Custom JPQL: Performs type-safe entity evaluation at runtime.
     */
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.createdAt >= :date")
    List<User> findActiveCreators(
        @Param("role") Role role, 
        @Param("date") LocalDateTime date
    );

    /**
     * Native SQL Query: Interacts directly with PostgreSQL engine.
     */
    @Query(value = "SELECT * FROM users WHERE email LIKE %:domain", nativeQuery = true)
    List<User> findByEmailDomain(@Param("domain") String domain);
}
```

---

## 🏁 Socratic Review & Homework

1.  **Composite Index vs. Single Index:** If we often query users by `email` **and** `role` together (e.g., `findByEmailAndRole`), is it better to have two separate indexes or one single **Composite Index** on both columns? Explain why.
2.  **`existsByEmail` vs. `findByEmail`:** If we only want to check if an email is taken during registration, why is calling `existsByEmail(email)` vastly more performant than calling `findByEmail(email).isPresent()`? What does the generated SQL look like for both? (Hint: Think about SQL `EXISTS` vs `SELECT *`).

*Analyze these questions, and once you have formulated your architectural answers, we will move on to the DTO layer.*