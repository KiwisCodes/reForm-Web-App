# Masterclass Blueprint: Step 1 — The Domain Bridge (Data Translation)

This document serves as the comprehensive, production-ready technical design and implementation blueprint for **Step 1: The Domain Bridge (Data Translation)** within the *reForm* backend architecture. It consolidates all conceptual models, mental maps, implementation requirements, and finalized code verification assets.

---

## 1. Core Architectural Role

Within the decoupled enterprise architecture of *reForm*, the authentication engine resides under the `com.reForm.backend.auth` package. Before we can enforce security rules or sign stateless tokens, we must build a translation mechanism.

Currently, Spring Security is completely unaware of our database schema, JPA configurations, or our PostgreSQL `User` domain entity. **The Domain Bridge** acts as the adapter layer that translates our core domain entities into data structures that the Spring Security context can natively process, verify, and store in thread-local memory.

---

## 2. Component A: The User Details Adapter (`CustomerUserDetail.java`)

*   **Location:** `src/main/java/com/reForm/backend/auth/service/CustomerUserDetail.java`
*   **Design Pattern:** Adapter (Wrapper) Pattern via Object Composition.

```
                  ┌──────────────────────────────────────────────┐
                  │              CustomerUserDetail              │
                  │   (Implements Spring Security UserDetails)   │
                  │                                              │
                  │   ┌──────────────────────────────────────┐   │
                  │   │             user (Field)             │   │
                  │   │     (Wraps core JPA User Entity)     │   │
                  │   └──────────────────────────────────────┘   │
                  └──────────────────────────────────────────────┘
```

### Phase 1 — The Problem Landscape

#### What are you looking at?
You are looking at a translation template. By implementing the `UserDetails` interface, you establish a compiler-enforced contract promising that this class will serve as the mediator between the application’s custom database schema and Spring Security’s authorization pipelines.

#### Do you need to put a User attribute here?
Yes. Without the wrapped domain `User` entity, this class is an empty shell with no state to translate.

Instead of extending the `User` entity or polluting our JPA entity with transient security methods, we use **Composition**. We wrap our real database `User` entity inside this class so that Spring Security interacts exclusively with the outer wrapper, leaving our domain layer clean and decoupled.

#### Why is there no default constructor?
In Java, interfaces cannot declare constructors. Therefore, the compiler does not force you to write a constructor to satisfy the implementation contract. If omitted, Java defaults to a zero-argument constructor: `public CustomerUserDetail() {}`.

However, a default empty constructor is non-functional here because **we cannot wrap a user if we do not pass the user into the class during instantiation.** We must explicitly define a parameterized constructor to bind the domain entity.

---

### Phase 2 — The Mental Model: The Login Journey

To visualize the lifecycle of this class during a login attempt, track this execution path:

```
[1. Client submits credentials] ───► [2. CustomerUserDetailService]
                                            │
                                            ▼ (Queries DB)
                                      Loads User entity
                                            │
                                            ▼ (Composition)
                                  Instantiates CustomerUserDetail(user)
                                            │
                                            ▼ (Matches cryptographic hashes)
                               getPassword() ◄──► PasswordEncoder
                                            │
                                            ▼ (If credentials match)
                              Placed inside SecurityContextHolder
                                            │
                                            ▼ (Future Requests)
                               Read directly from thread-local memory
                               (Exposes custom UUID instantly!)
```

#### Why return the UUID?
Your business controllers frequently require the logged-in user's database ID (e.g., to load default workspaces, assign form ownership, or check tenant boundaries).

*   **The Inefficient Approach:** Extract the user's email from the security context, run a SQL query (`SELECT id FROM users WHERE email = ?`), retrieve the UUID, and then execute your business logic. This introduces an unnecessary, expensive database round-trip on every protected request.
*   **The High-Performance Approach:** Because the `CustomerUserDetail` wrapper lives directly in memory (`SecurityContextHolder`), we expose a custom `getId()` method. This allows controllers to fetch the user's UUID instantly without hitting PostgreSQL.

---

### Phase 3 — The Foundational Prerequisites

#### 1. What does `UserDetails.super.isEnabled();` do?
Java 8 introduced `default` implementations inside interfaces. The `super` keyword refers to the parent interface (`UserDetails`). This statement tells Java to run whatever default fallback behavior Spring Security's developers wrote inside the interface for that method. Explicitly overriding this to return `true` is preferred for clarity and predictability across different runtime compilers.

#### 2. The Status Methods and Business Realities
Even if our current database does not yet track locks, suspensions, or expiration dates, we must implement these methods. They are designed to support standard enterprise software patterns:

| Method | Enterprise Business Case | Database Implementation Blueprint |
| :--- | :--- | :--- |
| **`isEnabled()`** | **Email Verification / Admin Ban:** Prevents new users from logging in until they confirm their email, or allows administrators to instantly ban bad actors. | Add a `boolean active` or `boolean enabled` column to your database table. |
| **`isAccountNonLocked()`** | **Brute-Force Lockout:** Automatically locks an account for 24 hours if a user inputs the incorrect password 5 times in a row. | Add a `LocalDateTime lockedUntil` column. Return `true` if current time is past `lockedUntil`. |
| **`isAccountNonExpired()`** | **Subscription Trial Management:** Cuts off account access automatically 30 days after registration if the user has not added a credit card. | Add a `LocalDate trialEndDate` column. Check if today is before that date. |
| **`isCredentialsNonExpired()`**| **Mandatory Password Rotation:** Enforces corporate compliance policies requiring users to reset their passwords every 90 days. | Add a `LocalDateTime lastPasswordReset` column. |

---

### Phase 4 — Implementation Guide: `CustomerUserDetail.java`

1.  **Composition & Construction:** Add a `private final User user;` instance variable and construct the class by passing in the core JPA `User`.
2.  **`getAuthorities()`:** Spring Security maps user privileges using `SimpleGrantedAuthority`. Take the `Role` enum from your entity (e.g., `FORM_BUILDER`, `ADMIN`), prefix it with `"ROLE_"`, and wrap it in a `SimpleGrantedAuthority`. Return this single authority inside `List.of(...)`.
3.  **`getPassword()` and `getUsername()`:** Delegate these directly to `user.getPasswordHash()` and `user.getEmail()`. Remember, **email is our system identifier**.
4.  **Account Status Flags:** Hardcode these to return `true` explicitly for now so that standard active users are never blocked.
5.  **Expose the Custom UUID:** Declare a non-override method `public UUID getId()` that returns `user.getId()`.

---

## 3. Component B: The User Lookup Service (`CustomerUserDetailService.java`)

*   **Location:** `src/main/java/com/reForm/backend/auth/service/CustomerUserDetailService.java`
*   **Design Pattern:** Service Adapter mapping user repository actions.

---

### Phase 1 — The Problem Landscape

#### Why do you need this service, and what does it do?
Spring Security is generic; it does not know if your storage layer is PostgreSQL, MongoDB, LDAP, or an external Web Service.

To bridge this, Spring Security defines the `UserDetailsService` interface containing a single lookup method. Your implementation, `CustomerUserDetailService`, is the designated data accessor that plugs directly into your custom `UserRepository`.

Without this service, Spring Security cannot query your PostgreSQL database during credential verification.

---

### Phase 2 — The Mental Model: The Lookup Step

This diagram demonstrates how Spring Security invokes this service during the authentication loop:

```
[User submits: "User@Domain.com" & "secret123"]
                       │
                       ▼
    [Spring calls: loadUserByUsername("User@Domain.com")]
                       │
                       ▼
             1. Normalize to lowercase: "user@domain.com"
             2. Query PostgreSQL via UserRepository
                       │
             ┌─────────┴─────────┐
             ▼ (User found)      ▼ (User NOT found)
     Wrap in wrapper class     Throw UsernameNotFoundException
    [CustomerUserDetail]         │
             │                   ▼
             │         Spring intercepts exception,
             │         aborts login, returns 401.
             ▼
    Spring compares password hash
```

---

### Phase 3 — The Structural Analysis

Your implementation of `CustomerUserDetailService` is clean and follows production-grade patterns:

1.  **Email Normalization:** By using `email.toLowerCase()`, you resolve casing inconsistencies. This ensures typing mistakes (e.g., `User@Domain.com` vs `user@domain.com`) do not prevent a successful lookup.
2.  **Constructor Injection:** By avoiding `@Autowired` and using Lombok's `@RequiredArgsConstructor` alongside `private final UserRepository userRepository`, you ensure class immutability and ease of unit testing.
3.  **Semantic Exception Handling:** Throwing Spring Security’s native `UsernameNotFoundException` is critical. It allows Spring’s security filters to intercept the error and coordinate authentication failure events (such as logging or auditing) before converting them to clean standard client responses.

---

## 4. Compiled Verification Code

Here are the side-by-side, fully integrated, compiler-verified implementations for Step 1.

### 1. The Adapter Wrapper Class
```java
package com.reForm.backend.auth.service;

import com.reForm.backend.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CustomerUserDetail implements UserDetails {

    private final User user;

    public CustomerUserDetail(User user) {
        this.user = user;
    }

    public UUID getId() {
        return this.user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = "ROLE_" + this.user.getRole().name();
        return List.of(new SimpleGrantedAuthority(roleName));
    }

    @Override
    public String getPassword() {
        return this.user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return this.user.getEmail(); // Email is the core identifier
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

### 2. The Database Lookup Service

```java
package com.reForm.backend.auth.service;

import com.reForm.backend.user.entity.User;
import com.reForm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Could not find user with email: " + normalizedEmail
                ));
        return new CustomerUserDetails(user);
    }
}
```

---

## 5. Pitfall Radar (Always Keep This in Mind)

*   **The Lazy Loading Pitfall:** In `CustomerUserDetail`, do not attempt to write code that calls relations on the `user` entity (e.g., `user.getWorkspaces().size()`) inside this wrapper unless those relations are eagerly fetched. If accessed outside of a transaction context, Hibernate will throw a `LazyInitializationException` and crash the thread.
*   **The Spelling Typo Risk:** Keep your naming conventions clear. If you choose to keep the name `CustomerUserDetail` (singular), make sure your imports and declarations never mistake it for plural `CustomerUserDetails`. Consistency prevents package compilation mismatches.
*   **Case Collation Constraints:** Lowercasing the email inside `loadUserByUsername` works because we normalized the emails to lowercase *during* user registration in `UserServiceImpl.java`. **Both registration and login pathways must share this exact normalization logic** to avoid user-not-found mismatches.

---

## 6. Transition to Step 2: The Cryptographic Layer

Now that Step 1 is complete, we are ready to build the engine that secures our stateless backend: **Step 2: The Cryptographic Layer (The Token Engine)**.

We will construct `ITokenProvider` and `JwtProviderImpl` inside the `auth/port` and `auth/service` directories. This layer will handle generating tokens upon login and parsing them on incoming API requests.

### Phase 5 — The Socratic Question

Before we write the JWT provider implementation, consider this security problem:

> **A JWT payload contains claims like user ID and roles, encoded in plain, Base64-readable text. Any user can easily decode their own token using tools like `jwt.io`.**
>
> **Since the payload itself is completely visible and modifiable by the client, what cryptographic mechanism prevents a malicious user from editing their payload to change their role from `FORM_FILLER` to `ADMIN` before sending the token back to our API, and how does our backend detect this tampering?**

---

### Phase 6 — The Call to Action

1.  **Formulate your answer to the Socratic question above.** Explain the cryptographic concept that keeps stateless tokens safe from client tampering.
2.  Once your answer is ready, confirm that you are prepared to begin **Step 2: The Cryptographic Layer**, and I will lay out the design specifications for your JWT ports and services.