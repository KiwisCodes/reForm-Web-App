### Phase 1 — The Problem Landscape

To build **Step 1: Core Infra Beans (`PasswordEncoderConfig`)**, we must first understand the security and lifecycle problems it solves:

1.  **The Cleartext Hazard (Registration):**
    When a user registers, their raw, human-readable password must never reach the database. The `UserServiceImpl` needs a tool to safely transform this raw password into a cryptographically secure hash.
2.  **The Matching Puzzle (Login):**
    During login, we cannot decrypt the stored hash. We must calculate the hash of the user's incoming password attempt and compare it to the stored hash. The authentication service needs a matching tool to execute this validation.
3.  **The Circular Bootstrapping Block (Spring Context):**
    If you define the `PasswordEncoder` bean directly inside your main security configuration class (`SecurityConfig`), you create a circular dependency. Spring will fail to start.

---

### Phase 2 — The Mental Model

#### 1. Visualizing the BCrypt Hash (Self-Contained Structure)

When you encode the raw string `"password123"` with BCrypt, you do not get a raw output. You get an encoded, structured, 60-character ASCII string.

BCrypt is **self-contained** because the metadata required to verify a password (except the secret key itself) is packed directly inside the string that you store in your `password_hash` column.

Here is the precise anatomical breakdown of a typical BCrypt output:

```
 $2a$10$N9qo8uLOqpGCgHiXN657uec66.v88RbeK6t5U2rDdH.R9tG/0Tfde
 ├──┘ ├┘ ├────────────────────┫├───────────────────────────┫
  │   │            │                        │
  │   │        2. Salt                  3. Hash
  │   │    (22 Characters)          (31 Characters)
  │   │
  │  1b. Cost Factor (log2)
  │      10 = 2^10 = 1024 rounds
  │
 1a. Algorithm Identifier
     $2a$ = BCrypt
```

#### 2. The Login Workflow: How Verification Works with a Random Salt

If every user has a completely random salt, how does the system verify a login attempt? The system does **not** need a separate `salt` database column.

```
[User Login Request] ─── (Inputs: "password123") 
                                │
                                v
[Database Fetch] ───────> Retrieve Stored Hash: 
                          "$2a$10$N9qo8uLOqpGCgHiXN657ue..."
                                │
                                ├─ 1. BCrypt Parser reads the first 29 characters:
                                │    Algorithm: $2a$
                                │    Cost Factor: 10
                                │    Salt: "N9qo8uLOqpGCgHiXN657ue"
                                │
                                ├─ 2. BCrypt hashes the incoming input "password123"
                                │    using the extracted salt "N9qo8uLOqpGCgHiXN657ue"
                                │    and the exact same cost factor (10).
                                │
                                v
[Resulting Hash] ───────> "$2a$10$N9qo8uLOqpGCgHiXN657uec66.v88RbeK6t5U2r..."
                                │
                                v
[Comparison] ───────────> Compare the new resulting string with the stored database string.
                          If identical ──> Access Granted.
```

---

### Phase 3 — The Foundational Prerequisites

#### 1. Understanding Circular Dependency (The Bootstrapping Failure)

A circular dependency occurs when Class A needs Class B to initialize, but Class B needs Class A to initialize. Spring Boot cannot resolve this chicken-and-egg problem and crashes on startup.

Here is how this happens if we don't isolate our configurations:

```
 ┌────────────────────────────────────────────────────────┐
 │                   SecurityConfig                       │
 │                                                        │
 │  Needs: UserDetailsServiceImpl to authorize requests.  │
 └─────────────────────────┬──────────────────────────────┘
                           │
                           ▼ (Injects UserDetailsService)
 ┌────────────────────────────────────────────────────────┐
 │              UserDetailsServiceImpl / UserService      │
 │                                                        │
 │  Needs: PasswordEncoder to verify login attempts.      │
 └─────────────────────────┬──────────────────────────────┘
                           │
                           ▼ (Injects PasswordEncoder)
 ┌────────────────────────────────────────────────────────┐
 │             PasswordEncoder Bean                       │
 │  (Declared inside SecurityConfig as a @Bean)           │
 └─────────────────────────┬──────────────────────────────┘
                           │
                           ▼ (Loop back to Owner)
                  [ SecurityConfig ]
```

**The Solution:** We extract the `PasswordEncoder` definition out of `SecurityConfig` completely and place it in a standalone `PasswordEncoderConfig` class. This class has zero dependencies and can initialize immediately, breaking the loop.

#### 2. The Cost Factor (Adaptive Hashing)

BCrypt is an *adaptive* hashing algorithm designed to be slow. As computers get faster, we can increase the work factor to keep brute-force attacks difficult.

*   The cost factor is logarithmic: a value of $N$ means the algorithm runs $2^N$ iterations.
*   If $10$ takes $\approx 80\text{ms}$ on your server, $11$ will take $\approx 160\text{ms}$, and $12$ will take $\approx 320\text{ms}$.
*   **The Recommended Value:** For modern production environments, a cost factor of **10 to 12** is standard. 10 strikes a balance between secure processing limits and fast response times, preventing bad actors from executing Denial of Service (DoS) attacks by flooding your login endpoints with high-overhead CPU requests.

---

### Phase 4 — Breaking It Down (OOP + Clean Code)

We will isolate this infrastructure requirement into its own class following the Single Responsibility Principle:

```
📁 core/config/
└── 📄 PasswordEncoderConfig.java  # Declares the BCryptPasswordEncoder Bean with custom cost parameters.
```

By keeping this configuration isolated, your application core remains decoupled from your network routing logic.

---

### Phase 5 — The Socratic Question

We can set the cost factor when we initialize the `BCryptPasswordEncoder` bean.

*If we increase our cost factor from 10 to 14 to make password cracking computationally harder for attackers, we also increase the server's CPU workload for every login attempt. How might a high cost factor impact your backend during a heavy spike in user traffic (or a coordinated brute-force attempt), and how would you design your API rate-limiting rules to mitigate this risk?*

---

### Phase 6 — The Call to Action

Now let's build the foundation. Please:
1. Share your thoughts on the **Phase 5 Socratic Question**.
2. Write the structural implementation (pseudocode or actual Java) for the `PasswordEncoderConfig` configuration class, ensuring it exposes the `PasswordEncoder` bean cleanly.

---

### Pitfall Radar

*   **Low Cost Factor Vulnerability:** Setting the cost factor below 10 (e.g., 4 or 5) speeds up developer unit tests, but makes the database vulnerable to rapid GPU-accelerated cracking if database files are compromised.
*   **Spring Boot Autowire Ambiguity:** If you declare multiple `PasswordEncoder` beans within different config files, Spring will throw a `NoUniqueBeanDefinitionException` during deployment. Keep only one bean definition active.