### Recommended File Name:
Save this document as:
📂 `knowledge/10-api-controller-security-and-runtime-troubleshooting.md`

---

# Architectural Specification: API Controllers, Security Configuration & Runtime Troubleshooting
**Document Version:** 1.2  
**Target Platform:** Reform (Modular Monolith)  
**Author:** Senior Technical Lead

---

## 1. Introduction: The Milestone Overview

This specification documents the successful completion of the **Presentation Layer (Controllers)** and the systematic resolution of compile-time, environment, database-connectivity, and structural data mapping issues.

By resolving these issues, the core foundational loop of the **Reform** monolith is fully complete. The system now supports a highly secure, performant multi-tenant database schema: **1 User owns exactly 1 Workspace (1-to-1), but can be invited to collaborate as a member inside multiple other workspaces (Many-to-Many).**

```text
                     THE COMPLETED MVP RUNTIME FLOW
                     
  [Postman/React] ──(POST /register JSON)──> [ UserController ] (JSON & Validation)
                                                   │
                                                   ▼ (Injected via SecurityConfig)
                                             [ BCryptPasswordEncoder ] (Hashes password)
                                                   │
                                                   ▼ (Saves JPA Entity)
  [Postman/React] <──(Returns DTO)────────── [ Postgres (Docker Port 5432) ]
```

---

## 2. Chronological Troubleshooting & Resolution Log

This section details the actual technical errors encountered during the build phase of the controller and service layers, why they happened, and how they were resolved.

---

### Step 1: Resolving the DTO File Naming Typo

#### The Error:
```text
java: class WorkspaceAddMemberRequestDto is public, should be declared in a file named WorkspaceAddMemberReqestDto.java
```

#### The Diagnosis:
In Java, a public class or record must reside inside a physical file on disk that matches its name exactly (including spelling and capitalization).
*   The class name inside the code was spelled correctly as `WorkspaceAddMemberRequestDto` (with a **`u`**).
*   The physical file on disk was named `WorkspaceAddMemberReqestDto.java` (missing the **`u`**).

#### The Fix:
We utilized IntelliJ's **Rename Refactor (`Shift + F6`)** on the file to rename it to `WorkspaceAddMemberRequestDto.java`.

Using the IDE’s refactoring tool instead of the operating system's file explorer ensured that all import statements across the `IWorkspaceService` and `WorkspaceServiceImpl` were automatically updated, preventing broken reference errors.

---

### Step 2: Resolving the Lombok & JDK 26 Compiler Conflict

#### The Error:
```text
java: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN
Caused by: java.lang.NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN
    at lombok.permit.Permit.getField(Permit.java:144)
    at lombok.javac.Javac.<clinit>(Javac.java:187)
```

#### The Diagnosis:
Lombok is not a standard library; it is an **Annotation Processor compiler hack**. It modifies your code’s Abstract Syntax Tree (AST) in memory during compilation.

The developer's local Mac had **Java 26** installed as the global terminal JDK. However, the Lombok version managed by Spring Boot was older. Because the Java 26 compiler has modified internal class names, Lombok failed to find the required compiler fields, threw an `ExceptionInInitializerError`, and crashed the build.

```text
                  THE INTELLECTUAL COMPILER CONFLICT
                  
  [ Local Mac Environment ] ──> Runs Java 26 Compiler
  [ Lombok Annotation Processor ] ──> Searches for private JDK 21 compiler structures
                                                 │
                                                 ▼
                                     💣 COMPILER EXPLOSION!
                                     TypeTag :: UNKNOWN (Does not exist in Java 26)
```

#### The Fix:
We resolved this by locking both the **terminal compiler** and the **IntelliJ internal JPS compiler** to **Java 21 (LTS)**, while explicitly forcing Lombok to its latest version:

1.  **POM Overrides:** Forced Lombok `1.18.36` in the properties:
    ```xml
    <properties>
        <java.version>21</java.version>
        <lombok.version>1.18.36</lombok.version>
    </properties>
    ```
2.  **Forced Terminal JDK 21:** We set the local shell session to point to the stable Java 21:
    ```bash
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
    ```
3.  **Forced IntelliJ Compiler:** Set **Project SDK**, **Module SDK**, and **Java Compiler Bytecode Version** to `21` inside IntelliJ's settings, and ran the clean build using the wrapper:
    ```bash
    ./mvnw clean compile
    ```
    This resolved the compiler conflict and returned `BUILD SUCCESS`.

---

### Step 3: Resolving the Database Connection Port Mismatch

#### The Error:
```text
org.postgresql.util.PSQLException: Connection to localhost:5332 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

#### The Diagnosis:
Your Spring Boot application was configured to connect to PostgreSQL on Port `5332` inside `application.yml`. However, your `docker-compose.yml` had been modified to map PostgreSQL to Port `5432` on your local host.

```text
                       THE PORT BINDING MISMATCH
                       
  [ Spring Boot App ] ──(Knocks on Port: 5332) ──> [ Local Computer Host ]
                                                           │
                                                           ▼ (No database there!)
                                                    CONNECTION REFUSED!
                                              (Postgres is open on Port 5432)
```

The database was open, but Spring Boot was knocking on the wrong port.

#### The Fix:
We updated line 3 of `application.yml` to point to port `5432`, matching your active Docker port mapping configuration:
```yaml
url: jdbc:postgresql://localhost:5432/${POSTGRES_DB:reform_db}
```

---

### Step 4: Resolving the Missing `PasswordEncoder` Bean

#### The Error:
```text
UnsatisfiedDependencyException: Error creating bean with name 'userController'...
Parameter 1 of constructor in UserServiceImpl required a bean of type 'org.springframework.security.crypto.password.PasswordEncoder' that could not be found.
```

#### The Diagnosis:
Your `UserServiceImpl` uses constructor injection (`@RequiredArgsConstructor`) to request a `PasswordEncoder` bean to securely hash user passwords during registration.

However, although Spring Security was present in the `pom.xml`, the framework does not automatically instantiate a specific password encoder bean by default. The Spring context was missing the concrete implementation bean, resulting in an `UnsatisfiedDependencyException` on startup.

```text
                 UNSATISFIED DEPENDENCY INJECTION PIPELINE
                 
  [ UserServiceImpl ] ──(Requests Bean)──> [ Spring ApplicationContext ]
                                                      │
                                                      ▼ (No matching Bean found!)
                                            STARTUP ABORTED!
                                   (Add @Bean for PasswordEncoder)
```

#### The Fix:
We created a central security configuration class (`SecurityConfig.java`) annotated with `@Configuration`, and explicitly declared a `@Bean` returning a `new BCryptPasswordEncoder()`. This satisfied Spring's dependency injection container, letting the application boot successfully.

---

### Step 5: Resolving the Lombok `@Builder` Null Pointer Trap

#### The Error:
```text
java.lang.NullPointerException: Cannot invoke "java.util.Set.addAll(java.util.Collection)" because the return value of "com.reForm.backend.user.entity.Workspace.getMembers()" is null
```

#### The Diagnosis:
When you use Lombok's `@Builder` annotation on an entity, the builder completely ignores any inline default field initializations (like `private Set<User> members = new HashSet<>();`).

Instead, when the builder instantiates the object (such as inside `createWorkspace`), it sets the `members` collection field to **`null`**. When the `addMembers` service method later attempted to call `workspace.getMembers().addAll(newMembers)`, the application threw a `NullPointerException` (HTTP 500).

#### The Fix:
We annotated the collections inside both `User.java` and `Workspace.java` with **`@Builder.Default`**. This instructs Lombok's builder to preserve the `new HashSet<>()` initialization during instantiation instead of overwriting it with `null`.

---

### Step 6: Resolving the Stale Target Folder & SQL Column Mismatch

#### The Error:
```text
org.postgresql.util.PSQLException: ERROR: column t1.name does not exist
```

#### The Diagnosis:
We renamed the fields in `Workspace.java` from `workspaceName` and `workspaceDescription` to `name` and `description`.

However, because the local Postgres database volume had already been initialized with the old column names, PostgreSQL did not contain the columns `name` and `description`. Additionally, MapStruct was executing cached code from the `/target` folder built on the old variables, resulting in SQL exceptions and runtime crashes.

#### The Fix:
We cleared the target folder, forced a clean MapStruct regeneration, and completely wiped the old database volumes in Docker:
```bash
# 1. Force MapStruct to rebuild the mappers cleanly in IntelliJ
Double-click 'clean' then 'compile' in the right-side Maven panel.

# 2. Completely wipe the old database columns and volumes
docker-compose down -v
docker-compose up -d --force-recreate
```

---

## 3. Core Architectural Explanations (The "Whys")

### A. The Noun-First Controller Design
We explicitly rejected the technical method naming pattern (starting with `handle...` such as `handleGetUserRequest`) and URL structures containing action verbs (such as `/create` or `/update`).

*   **Why `handle...` is bad:** In modern RESTful architecture, we map endpoints directly to **Business Domain operations (verbs)** rather than technical request-handler wrappers. Naming a method `getUserProfile` reads like a business story, whereas `handleGetUserRequest` adds redundant framework noise.
*   **Why verbs in URLs are bad:** In REST, **the URL represents the Noun (the resource), and the HTTP Method represents the Verb (the action).**
    *   `POST /api/v1/workspaces` means "Create Workspace".
    *   `POST /api/v1/workspaces/create` is redundant (it means "Create Create Workspace").
*   **The Model Alignment:** Matching the controller method name (`getUserProfile`) exactly to the service port method name (`IUserService.getUserProfile`) lowers the cognitive load of tracing data paths in your IDE.

### B. In-Process Tenant Isolation (IDOR Protection)
To completely prevent **Insecure Direct Object Reference (IDOR)** attacks—where logged-in User A tries to edit or delete User B's workspace—we implemented strict identity verification in the service layer:

```java
private void verifyOwner(Workspace workspace, UUID requesterId) {
    if (!workspace.getOwner().getId().equals(requesterId)) {
        throw new AccessDeniedException("Only the workspace owner can perform this action");
    }
}
```
*   Every workspace retrieval, update, or membership modification requires the `requesterId` (which will eventually be extracted securely from the JWT).
*   The system loads the workspace by its primary key and verifies that the owner's ID matches the requester's ID before executing any operations. If they do not match, it throws an `AccessDeniedException` (HTTP 403), locking out malicious actors completely.

### C. The Security Danger of `CascadeType.ALL` on Many-to-Many
*   We explicitly removed `cascade = CascadeType.ALL` from our `@ManyToMany` mapping on `User.workspaces` and `Workspace.members`.
*   **The Risk:** If an owner decides to delete their `Workspace`, `CascadeType.ALL` would instruct Hibernate to automatically propagate that deletion, physically deleting the user profiles of **every single collaborator inside that workspace** from your `users` database table!

### D. Why We Need Spring's `@Transactional(readOnly = true)` for GET Queries
*   By default, `@Transactional` opens a heavy read-write transaction.
*   When Hibernate loads data inside a read-write transaction, it takes a copy of the entity and keeps it in memory so it can check if you modified any fields at the end of the method (Dirty Checking).
*   By writing `@Transactional(readOnly = true)` on `getUserProfile()` and `getWorkspace()`, you tell Hibernate: *"I am only reading this profile. Do not waste memory keeping a tracking copy."* This optimizes database memory and connection speeds under high load.
