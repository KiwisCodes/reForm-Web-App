### Architecture & Design Specification: Core Base Entity
**Document Version:** 1.1  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

### 1. The Production-Ready Code Implementation

First, let's look at the correct, fully-formed implementation of the `BaseEntity` in `com.reform.app.core.domain`.

```java
package com.reform.app.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // --- Strict JPA Identity Contracts ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

---

### 2. Deep Dive: Why vs. Why Not (Annotation Breakdown)

Let us dissect every single annotation and property in this class to understand the architectural reasons behind their configurations.

#### A. `@MappedSuperclass`
*   **What it does:** Tells the JPA provider (Hibernate) that this class is not a database table itself. Instead, its fields are inherited and mapped directly to the database tables of any subclass (e.g., `User` or `Workspace`).
*   **Why use it:** It enables structural code reuse in Java without creating physical database table hierarchies.
*   **Why not use `@Inheritance` instead?** `@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)` or `JOINED` tells the database to physically link tables via foreign keys or complex `UNION` views. This drastically slows down performance on read operations. We want simple, decoupled, flat tables in PostgreSQL, but structured code reuse in Java.

#### B. `@EntityListeners(AuditingEntityListener.class)`
*   **What it does:** Registers Hibernate hooks that intercept entity lifecycle events (inserts and updates).
*   **Why use it:** This is the engine behind Spring's Auditing. Without this listener, `@CreatedDate` and `@LastModifiedDate` will remain `null`.

#### C. The ID Block (`UUID id = UUID.randomUUID()`)
*   **What it does:** Generates a 128-bit universally unique identifier in the JVM memory the moment the Java object is instantiated.
*   **Why Java-side generation instead of Database-side (`@GeneratedValue`)?**
    *   **Collection Safety:** If you use a database-generated ID (like an incremental `Long` or DB-side UUID), the ID is `null` until you physically call `repository.save(entity)`. If you place that unsaved entity into a `Set` (e.g., `Set<User> teamMembers`), its hashcode changes after it is saved, which breaks Java collection consistency.
    *   **No Roundtrips:** We do not have to wait for the database transaction to complete to know what the ID is. This is incredibly useful when creating multi-tenant hierarchies in a single transaction (e.g., creating a `User` and their default `Workspace` at the same time).
*   **Why `updatable = false` and `nullable = false`?** A primary key must be immutable. This prevents accidental updates to the ID field in SQL.

#### D. The Audit Fields (`@CreatedDate` & `@LastModifiedDate`)
*   **What it does:** Automatically captures the system time when the record is created and whenever it is updated.
*   **Configuration Detail:** `updatedAt` **must not** have `updatable = false`. If you set `updatable = false` on `updatedAt`, it will capture the creation time, but it will never change when the entity is updated! Only `createdAt` should have `updatable = false`.

#### E. Why `@Getter` & `@Setter` instead of `@Data`?
*   **The `@Data` Trap:** Lombok's `@Data` automatically generates `@ToString`, `@EqualsAndHashCode`, `@Getter`, and `@Setter`.
*   **Why this kills JPA:** `@EqualsAndHashCode` uses every field in the class. If your subclass has a relational link (e.g., a `User` has a list of `Workspace` objects, and a `Workspace` has a list of `User` objects), calling `hashCode()` will trigger an infinite recursion loop, resulting in a `StackOverflowError`.
*   **The Correct Way:** Override `equals()` and `hashCode()` manually using **only** the immutable database identifier (`id`), as shown in the code above.

---

### 3. Database Concurrency Control (Locking Mechanics)

In a high-scale environment with thousands of users, multiple processes will try to read and write to the same database rows at the same time. We manage this through **Locking**.

```text
                  CONCURRENCY CONTROL STRATEGIES
                               │
         ┌─────────────────────┴─────────────────────┐
         ▼                                           ▼
 OPTIMISTIC LOCKING                          PESSIMISTIC LOCKING
 (App-Level / Non-blocking)                  (DB-Level / Blocking)
  - Uses @Version column                      - Locks row via SQL
  - Fast, scalable                            - Safe, blocks others
  - Throws OptimisticLockException            - Slow, high deadlock risk
```

#### A. Optimistic Locking (The Default: `@Version`)
*   **How it works:** Spring Jpa adds a numeric column (like `version`) to your database table.
    1. User A reads a form row (version = 1).
    2. User B reads the same form row (version = 1).
    3. User A updates the form. Jpa executes: `UPDATE form SET title = 'New', version = 2 WHERE id = ? AND version = 1;` (Success!).
    4. User B tries to update the form. Jpa executes: `UPDATE form SET title = 'Old', version = 2 WHERE id = ? AND version = 1;`
    5. Because User A already bumped the version to 2, the `version = 1` condition fails. Hibernate updates 0 rows and throws an `OptimisticLockingFailureException`.
*   **When to use:** Use this for 95% of your SaaS application (such as form metadata and user settings). It is fast, handles conflicts gracefully, and does not block database connections.
*   **Why not `String`?** Your old code used `private String version`. This is incorrect. The version must be numeric (`Long` or `Integer`) because the JPA provider needs to perform mathematical increments (`version + 1`).

#### B. Pessimistic Locking
*   **How it works:** It physically locks the database row using SQL syntax (e.g., `SELECT ... FOR UPDATE`).
    1. User A reads the row with a Pessimistic Lock.
    2. If User B tries to read or write to that same row, **the database forces User B's thread to wait** until User A commits their transaction.
*   **When to use:** Use this when data consistency is absolute (such as financial transactions or credit ledger operations in your `billing` module). If two processes try to deduct credits at the exact same millisecond, you cannot afford to throw an exception; one *must* wait for the other to finish.
*   **Why not use it everywhere?** It is slow, wastes database connection pools, and introduces high risks of **deadlocks** (where Transaction A is waiting for Transaction B, and Transaction B is waiting for Transaction A).

---

### 4. Soft Deletion Handling (`deletedAt`)

Your initial draft included `private LocalDateTime deletedAt` directly inside the `BaseEntity`. This is considered a **Design Smell** for two reasons:

1.  **Not everything should be soft-deletable:** Do you need to soft-delete an API key? A credit ledger transaction? Or a role? No. Placing `deletedAt` in the global `BaseEntity` forces every single database table to carry an unused nullable timestamp column.
2.  **JPA Query Pollution:** If `deletedAt` is global, every select query in your repository must include `WHERE deleted_at IS NULL`. If you forget to write this in even one query, you will accidentally return "deleted" data to your users.

#### The Correct Approach: Dedicated Auditing Traits
If a specific entity (like `Form` or `User`) needs soft-delete capability, implement it at the subclass level using Hibernate annotations:

```java
@Entity
@Table(name = "forms")
@SQLDelete(sql = "UPDATE forms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL") // Auto-filters deleted records
@Getter
@Setter
public class Form extends BaseEntity {
    private String title;
    private LocalDateTime deletedAt; // Specific to this class
}
```

---

### 5. Socratic Challenge / Homework

Now that we have verified your core foundation is correct:

1.  **Refactor `User.java`**: Update your `User` entity to extend this correct `BaseEntity`. Eliminate the `id` field from your class entirely.
2.  **Integrate `Workspace.java`**: Design your `Workspace` entity extending `BaseEntity`.
3.  **The Relationship:** Since your architecture is designed to support agencies (multiple users working in isolated workspaces):
    *   *How will you link `User` and `Workspace` in JPA?*
    *   *If a workspace is deleted, should its forms be deleted too?* (Think about `CascadeType` settings).

*Draft these two entities together and show me their relational mappings.*