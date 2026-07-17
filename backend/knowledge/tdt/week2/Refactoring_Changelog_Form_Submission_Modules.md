# Refactoring Changelog & Technical Progression Report: Form and Submission Modules

This document tracks the engineering problems encountered, the troubleshooting process, the before-and-after code comparisons, and the technical concepts learned during the refactoring of the **Form (Designer Engine)** and **Submission (Execution Engine)** modules.

---

### Problem 1: Compilation Bug in Repository Imports
* **The Problem:** The `SubmissionRepository.java` interface could not compile due to a type mismatch in the `Page` querying methods.
* **Troubleshooting:** Review of the import headers revealed that the class was importing Java's desktop printing utilities instead of Spring Data's pagination domain classes.
* **Code Comparison:**
```java
// OLD CODE
import java.awt.print.Pageable; // Desktop print library

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Page<Submission> findByFormIdOrderByCreatedAtDesc(UUID uuid, Pageable pageable);
}
```
```java
// NEW CODE
import org.springframework.data.domain.Pageable; // Spring Data Commons library

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Page<Submission> findByFormIdOrderByCreatedAtDesc(UUID uuid, Pageable pageable);
}
```
* **Core Concepts:**
  * **Domain Types vs. Desktop AWT Libraries:** Java IDE auto-imports can select overlapping class names (like `Pageable`). Knowing the package namespaces (`org.springframework.data.domain`) prevents compile-time mismatches.

---

### Problem 2: Presentation Pollution inside persistent DB Entities
* **The Problem:** A presentation-level string (`message` to indicate successful execution) was initially added as a persistent column inside the physical `Submission` entity to resolve a MapStruct mapping mismatch.
* **Troubleshooting:** This violated the **Single Responsibility Principle (SRP)** by storing transient HTTP confirmation text in SQL tables. The solution was to handle this constant string purely inside MapStruct mapping configurations or localized DTO models.
* **Code Comparison:**
```java
// OLD CODE (Submission.java)
public class Submission extends BaseEntity {
    private UUID formId;
    private JsonNode answers;
    private String submitterIp;
    private String userAgent;
    private String message; // Redundant persistent DB column
}
```
```java
// NEW CODE (Submission.java - Reverted)
public class Submission extends BaseEntity {
    private UUID formId;
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answers;
    private String submitterIp;
    private String userAgent;
    // Removed persistent message property
}
```
* **Core Concepts:**
  * **Single Responsibility Principle (SRP):** Database models represent *persistent storage state*. Presentation details (such as API success messages or locale translations) belong strictly to the *Data Transfer (DTO)* layer.

---

### Problem 3: Tight Coupling and Bounded Context Violations
* **The Problem:** `SubmissionProcessorImpl` directly injected `FormRepository` and accessed the `Form` entity to validate schemas. This tightly coupled the `submission` domain to the `form` database structure.
* **Troubleshooting:** Introduced a query interface (Port) in the `form` module. This allowed the `submission` module to retrieve only required schema details through an abstraction rather than directly manipulating database tables.
* **Code Comparison:**
```java
// OLD CODE
@Service
@RequiredArgsConstructor
public class SubmissionProcessorImpl implements ISubmissionProcessor {
    private final SubmissionRepository repository;
    private final FormRepository formRepository; // Direct DB repository access from foreign module

    @Override
    public SubmissionResponseDto saveSubmission(UUID formId, JsonNode answers, String ipAddress, String userAgent) {
        Form form = formRepository.findById(formId).orElseThrow(); // Directly loading physical entity
        // ...
    }
}
```
```java
// NEW CODE
@Service
@RequiredArgsConstructor
public class SubmissionProcessorImpl implements ISubmissionProcessor {
    private final SubmissionRepository repository;
    private final IFormQueryPort queryService; // Decoupled interface port injection

    @Override
    public SubmissionResponseDto saveSubmission(UUID formId, JsonNode answers, String ipAddress, String userAgent) {
        FormSubmissionDto form = queryService.fetchForm(formId); // Accessing boundary-safe DTO
        // ...
    }
}
```
* **Core Concepts:**
  * **Hexagonal Ports & Adapters Pattern:** Isolating module internals by defining abstract ports.
  * **Bounded Context Boundaries:** Preventing databases or entities of Bounded Context $A$ from leaking directly into the application layer of Bounded Context $B$.

---

### Problem 4: JPA Entity Leakage in Contract DTOs
* **The Problem:** The first draft of `FormSubmissionDto` directly contained `List<AbstractBlock>`, meaning the `submission` module still had to import and process database entities from the `form` module.
* **Troubleshooting:** Created a lightweight validation record called `BlockValidationRule` containing only primitive/UUID fields. This completely severed the compile-time connection between the submission module and form entity definitions.
* **Code Comparison:**
```java
// OLD CODE (FormSubmissionDto.java)
import com.reForm.backend.form.entity.block.AbstractBlock; // Entity leakage

public record FormSubmissionDto(
        FormStatus status,
        List<AbstractBlock> blocks // Direct entity list exposure
) {}
```
```java
// NEW CODE (FormSubmissionDto.java)
import com.reForm.backend.form.entity.FormStatus;

public record FormSubmissionDto(
        FormStatus status,
        List<BlockValidationRule> rules // Decoupled validation rules
) {}
```
* **Core Concepts:**
  * **Encapsulation:** Keeping database class hierarchies internal to the module that owns them.
  * **Lightweight Projections:** Transferring only minimal data required to satisfy validation constraints instead of deep object graphs.

---

### Problem 5: Circular Dependency Loops (Port Ownership)
* **The Problem:** The query interface (`ISubmissionValidation`) was initially created inside the `submission` module but implemented by the `form` module. Because `submission` also had to import the DTOs of `form`, it created a circular reference loop (`form <-> submission`).
* **Troubleshooting:** Relocated the interface (`IFormQueryPort`) to the `form` module. This allowed the `submission` module to import the `form` API components, ensuring the dependency flow remained one-way.
* **Code Comparison:**
```
// OLD CODE (Circular Loop)
submission package (ISubmissionValidation) <------- implemented by -------> form package (FormQueryService)
      |                                                                            ^
      +------------------------ imports FormSubmissionDto ------------------------+
```
```
// NEW CODE (One-Way Flow)
submission package (SubmissionProcessorImpl) -------- calls / imports --------> form package (IFormQueryPort)
```
* **Core Concepts:**
  * **One-Way Dependency Flow:** Dependencies should always point in a single direction to keep modular architectures clean and avoid compile loops.
  * **Interface Ownership:** In a modular monolith, ports exposing domain data are owned by the module that holds that data.

---

### Problem 6: Primary Key Overwrite (Database Collision)
* **The Problem:** The submission processor mistakenly called `submission.setId(formId)` instead of setting the foreign key field `setFormId(formId)`.
* **Troubleshooting:** Identified that setting the submission's own primary identifier to the parent form's ID would cause constraint violations, restricting the system to only one submission per form.
* **Code Comparison:**
```java
// OLD CODE
Submission submission = new Submission();
submission.setId(formId); // Overwriting the submission's primary key
```
```java
// NEW CODE
Submission submission = new Submission();
submission.setFormId(formId); // Correctly setting the reference foreign key
```
* **Core Concepts:**
  * **Primary Key vs. Foreign Reference Identity:** A primary key uniquely identifies a specific transaction or response record. A reference key establishes the parent relational association.

---

### Problem 7: Missing Spring Stereotype Annotation
* **The Problem:** The application threw a `NoSuchBeanDefinitionException` on startup because `FormQueryService` could not be wired into `SubmissionProcessorImpl`.
* **Troubleshooting:** Realized that `FormQueryService` was missing the `@Service` or `@Component` annotation, preventing Spring's classpath scanner from registering it.
* **Code Comparison:**
```java
// OLD CODE
@RequiredArgsConstructor
public class FormQueryService implements IFormQueryPort {
    // Missing Spring registration
}
```
```java
// NEW CODE
@RequiredArgsConstructor
@Service // Registered as a bean in the IoC container
public class FormQueryService implements IFormQueryPort {
    // ...
}
```
* **Core Concepts:**
  * **Dependency Injection & Classpath Scanning:** Spring Boot relies on stereotype annotations to manage, construct, and wire bean implementations to interfaces across modules automatically.

---

### Problem 8: Java Record Accessor Compilation Mismatch
* **The Problem:** A compiler error occurred because `SubmissionProcessorImpl` called `.rules()` on `FormSubmissionDto`, while the field was named `blocks` inside the record definition.
* **Troubleshooting:** Renamed the record component to `rules` inside `FormSubmissionDto` to match the business intent of validation rules and the processor loop call.
* **Code Comparison:**
```java
// OLD CODE (FormSubmissionDto.java)
public record FormSubmissionDto(
        FormStatus status,
        List<BlockValidationRule> blocks // Accessed incorrectly as rules()
) {}
```
```java
// NEW CODE (FormSubmissionDto.java)
public record FormSubmissionDto(
        FormStatus status,
        List<BlockValidationRule> rules // Accessor form.rules() compiles cleanly
) {}
```
* **Core Concepts:**
  * **Java Record Compiler-Generated Accessor Rules:** Java auto-generates getter accessors named exactly after the record component variables. Keeping field names and accessors aligned is required for successful compilation.

---

### Problem 9: NullPointerException Risk on Dynamic JSON Payloads
* **The Problem:** Evaluating the payload using `answers.get(key).asText()` directly would trigger a `NullPointerException` (NPE) if the requested key was missing from the client's submitted JSON.
* **Troubleshooting:** Integrated a null-safe validation pattern that checks for physical Java nulls, Jackson `NullNode` representations, and blank strings before calling accessor methods.
* **Code Comparison:**
```java
// OLD CODE
for (BlockValidationRule rule : form.rules()){
    String key = rule.id().toString();
    if (rule.isRequired()){
        // Will throw NullPointerException if key is missing
        if (answers.get(key).asText().trim().isEmpty()) {
            throw new RuntimeException("Field cannot be empty");
        }
    }
}
```
```java
// NEW CODE (Robust Implementation)
for (BlockValidationRule rule : form.rules()){
    String key = rule.id().toString();
    if (rule.isRequired()){
        JsonNode valueNode = answers.get(key);
        // Null-safe check handles missing keys, null nodes, and blank entries
        if (valueNode == null || valueNode.isNull() || valueNode.asText().trim().isEmpty()) {
            throw new RuntimeException("Field cannot be empty");
        }
    }
}
```
* **Core Concepts:**
  * **Defensive Programming:** Coding defensively against unpredictable user-submitted JSON shapes.
  * **Jackson Tree Model Node Navigation:** Recognizing that a missing key in a `JsonNode` returns a Java `null`, whereas a JSON key with an explicit `null` maps to a Jackson `NullNode`. Both cases must be checked before invoking methods to avoid system crashes.
