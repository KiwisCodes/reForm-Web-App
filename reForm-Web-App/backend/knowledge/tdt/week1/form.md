# 📘 Documentation: Form Entity & Specialized Persistence

**Module:** `form`  
**Pattern:** Aggregate Root with Type-Safe JSONB

---

## 1. The Aggregate Root: `Form.java`

The `Form` class is the "Boss" of this module. It manages the lifecycle of a form and all the blocks within it.

### Key Architectural Decisions:
* **`BaseEntity` Inheritance:** By extending `BaseEntity`, we automatically get a UUID primary key, `createdAt`, and `updatedAt`. This is vital for auditing—knowing exactly when a creator last modified their form.
* **Multi-tenancy (Workspace Isolation):** We used `private UUID workspaceId` instead of a hard link to a `Workspace` entity.
    * *Reasoning:* In a Decoupled Architecture, modules should stay independent. The `form` module doesn't need to know the internal details of the user or workspace module; it only needs to know "Who owns this form?" to prevent data leaks between different enterprise clients.
* **Status Management:** The `FormStatus` enum (`DRAFT`, `PUBLISHED`) allows the system to distinguish between a form being edited and one that is live and accepting submissions.

---

## 2. The Persistence Bridge: `AbstractBlockConverter.java`

This file is the "Specialized Translator" that enables our polymorphic block system.

### Why a new converter was mandatory:
* **Type Signature Matching:** JPA's `AttributeConverter` requires an exact match between the Java field type and the converter's generic parameters. Since our field was `List<AbstractBlock>`, a generic `JsonNode` converter would cause a type mismatch error.
* **Defeating Type Erasure:** Java "forgets" the contents of a `List` at runtime. By using Jackson’s `TypeReference<List<AbstractBlock>>() {}`, we "anchor" the type. This ensures that when the data comes back from Postgres, Jackson knows to look for the `"type": "STATIC"` discriminator to rebuild the `StaticBlock` object.
* **Encapsulation:** We moved the converter from the `core` package to the `form` package. This prevents the `core` module (the foundation) from needing to "know" about the `form` module, which keeps the architecture clean and prevents circular dependencies.

---

## 3. Knowledge Base: Concepts & Socratic Lessons

### A. Relational vs. Schema-less (The Hybrid Approach)
We chose a Relational structure for metadata (`title`, `status`, `workspaceId`) but a Schema-less structure (`JSONB`) for the blocks.
* *Why?* Searching for forms by `Title` or `Workspace` is very fast in SQL. However, the structure of a form (questions) changes constantly. Storing blocks in `JSONB` gives us the flexibility of a NoSQL database (like MongoDB) inside the reliable and structured environment of PostgreSQL.

### B. Initialization & Null Safety
We initialized the blocks list: `private List<AbstractBlock> blocks = new ArrayList<>();`.
* *The Lesson:* In high-scale systems, `NullPointerExceptions` are the #1 cause of downtime. By ensuring the list is never null (even if it's empty), we make the `Form` object much safer and predictable for other developers to use.

### C. JPA Annotations (`@Entity` & `@Table`)
* **`@Entity`:** Marks the class as a database-backed object.
* **`@Table`:** Specifically names the table. In enterprise apps, we never let Hibernate pick names automatically (it might name it `form_entity` or something messy); we explicitly name it to match our SQL migration scripts.

---

## 4. Error Log & "Gotchas"

| Error Encountered | The Architectural Root Cause | The Professional Fix |
| :--- | :--- | :--- |
| **`ClassCastException`** | Jackson turned JSON into a `Map` instead of `AbstractBlock` due to Java Type Erasure. | Used `TypeReference` in the specialized converter. |
| **Type Mismatch** | JPA couldn't link a `List` field to a raw `JsonNode` converter. | Created `AbstractBlockConverter` specifically for the `List<AbstractBlock>` type signature. |
| **Circular Dependency** | The `core` module trying to import blocks belonging to the `form` module. | Moved the Converter into the `form` module to strictly respect package boundaries. |

---

## 5. Summary of Workflow
[Next.js Client UI] ──>(Sends JSON Array)──> [Spring Boot Controller]
│
(Maps to Form.blocks)
│
▼
[PostgreSQL Table] <──(Saves JSONB String)── [JPA + AbstractBlockConverter]
│
(Jackson adds "type": "STATIC")


1. Next.js sends a JSON array of questions to the endpoint.
2. Spring Boot maps this incoming array payload to `Form.blocks`.
3. Saving the entity triggers the `AbstractBlockConverter` interceptor.
4. Jackson embeds the `"type": "STATIC"` discriminator flag directly into the serialized text string.
5. PostgreSQL commits the text block as a single row to a optimized `jsonb` data column.