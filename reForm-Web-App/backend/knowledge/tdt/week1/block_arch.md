# 📘 Documentation: Reform Block Architecture

**Module:** `form`  
**Pattern:** Polymorphic JSONB Storage

---

## 1. Architectural Overview

The "Block" system is designed to handle a wide variety of question types (Static, Conversational/AI, Media) without requiring a database schema change every time a new type is added. We treat the `Form` as the container and the `Blocks` as dynamic components stored in a PostgreSQL `JSONB` column.

---

## 2. File Breakdown & Purpose

### 📄 `IFormBlock.java` (The Contract)
* **Role:** Interface.
* **Why it exists:** It defines the mandatory behavior of any block. Any service (like Analytics or Rendering) can interact with a block through this interface without knowing if it's a "Static" or "AI" block.
* **Key Methods:** `getId()`, `getType()`, `getSortOrder()`.

### 📄 `AbstractBlock.java` (The Shared Blueprint)
* **Role:** Abstract Base Class.
* **Why it exists:** To prevent code duplication (**DRY** - *Don't Repeat Yourself*). All blocks share a `label`, `description`, `isRequired`, and `sortOrder`.
* **The "Magic":** It contains the Jackson Polymorphism annotations.
* **Inheritance:** Implements `IFormBlock`.

### 📄 `StaticBlock.java` (The Concrete Implementation)
* **Role:** Concrete Class.
* **Why it exists:** Represents standard, non-AI inputs like Text, Email, and Numbers.
* **Specific Logic:** Adds `placeholder` and `subType` (Enum) to differentiate between the types of static inputs.

---

## 3. Key Concepts & "The Secret Sauce"

### A. Jackson Polymorphism (`@JsonTypeInfo`)
This was the most critical piece of the puzzle.
* **The Problem:** When we store a `List` of blocks in `JSONB`, JSON is just text. Java doesn't naturally know how to turn `{"label": "Name"}` back into a `StaticBlock.class`.
* **The Solution:** `@JsonTypeInfo` tells Jackson to add a `"type": "STATIC"` field to the JSON string.
* **The Registry:** `@JsonSubTypes` acts as a lookup table. It says: *"If the type is 'STATIC', use the StaticBlock class."*

### B. Decoupled ID Management
We use `UUID` for Block IDs. This allows the frontend to generate unique IDs for blocks before they are even saved to the database, preventing conflicts in a distributed environment.

---

## 4. Lessons Learned & Error Log

### ❌ Error: The "Generic JSON" Trap
* **Initial Thought:** Why not just use a `Map<String, Object>` for blocks?
* **The Lesson:** While flexible, Maps are not type-safe. You lose the ability to use compiler checks, and your code becomes full of "string-key" bugs. Using a class hierarchy (`AbstractBlock` $\rightarrow$ `StaticBlock`) gives us flexibility and type safety.

### ❌ Error: Missing State in Child Classes
* **The Gotcha:** Defining an Enum inside a class is just a definition.
* **The Fix:** We realized we must also declare a private field of that Enum type (e.g., `private StaticBlockType subType`) so that the state is actually stored in the database.

### ❌ Error: Equality in Inheritance
* **The Gotcha:** By default, Lombok's `@EqualsAndHashCode` only looks at the fields in the current class. If two blocks have different labels (in the parent) but the same placeholder (in the child), Lombok might think they are equal.
* **The Fix:** Use `@EqualsAndHashCode(callSuper = true)`. This ensures the identity of the block includes its shared parent fields.

### ❌ Error: The Interface vs. Abstract Confusion
* **The Lesson:** * **Interface:** Is for other modules (The *"What"*).
    * **Abstract Class:** Is for internal implementation (The *"How"*).
* **Conclusion:** We need both to keep the system clean and extensible.

---

## 5. Future Scalability

In the future, adding an AI Voice Block will only require:
1. Creating `ConversationalBlock.java`.
2. Adding it to the `@JsonSubTypes` list in `AbstractBlock.java`.

**Result:** Zero database migrations required!    b 