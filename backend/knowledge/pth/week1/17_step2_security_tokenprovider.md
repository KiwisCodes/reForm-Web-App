### Part 1: The Conceptual Groundwork

To write secure code, we must first demystify the terms that developers frequently confuse: **Encoding**, **Hashing**, **Encrypting**, and **Signing**.

```
  ┌─────────────────────────────────────────────────────────────┐
  │                           ENCODING                          │
  │  Translate data format (Reversible, No Key).                │
  │  Example: "Hello" ───► "SGVsbG8="                           │
  └──────────────────────────────┬──────────────────────────────┘
                                 ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                           HASHING                           │
  │  One-Way Math Fingerprint (Irreversible).                   │
  │  Example: "Hello" ───► "2cf24dba5fb0a30e26e83b2ac5b9e29e"    │
  └──────────────────────────────┬──────────────────────────────┘
                                 ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                          ENCRYPTING                         │
  │  Hiding data using a Key (Reversible only with Key).        │
  │  Example: "Hello" + Key ───► "🔒^&*%#$"                     │
  └──────────────────────────────┬──────────────────────────────┘
                                 ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                           SIGNING                           │
  │  Ensuring Integrity. Clear payload + Cryptographic Proof.   │
  │  Example: "Payload" + Secret Key ───► Proof (Signature)     │
  └─────────────────────────────────────────────────────────────┘
```

#### 1. What is the difference between Encoding, Hashing, Encrypting, and Signing?

*   **Encoding:** This is a **format translation** designed to make data transmittable across networks safely. It is **completely reversible** and uses **no key**. Anyone can decode it.
    *   *Real-World Analogy:* Translating English words into Morse Code.
    *   *Coding Example:* `"Hello"` translated to Base64 is `"SGVsbG8="`. You can decode it back instantly.
*   **Hashing:** This is a **one-way mathematical compression**. It takes any arbitrary input and produces a fixed-length string of bytes (the fingerprint). It is **conceptually irreversible**—you can never turn a hash back into its original text.
    *   *Real-World Analogy:* Blending a strawberry into a smoothie. You cannot turn the smoothie back into a strawberry.
    *   *Coding Example:* Hashing `"password"` with SHA-256 results in `5e8842c1...`.
*   **Encrypting:** This is a **two-way data concealment** process using a mathematical **Key**. Its purpose is to hide data from unauthorized eyes. Only someone with the corresponding decryption key can read the original plaintext.
    *   *Real-World Analogy:* Placing a secret message inside a locked steel safe.
*   **Signing:** This does **not** hide your data. Instead, it proves **integrity** and **authenticity**. It says: *"Here is my data in plain text, and here is a cryptographic proof (the signature) showing that I wrote it and that no one has modified it."* If anyone changes a single letter of the text, the mathematical signature is broken because they do not have your private key to recalculate it.
    *   *Real-World Analogy:* Signing a paper check. Anyone can read the amount, but only your signature makes it authentic.

---

#### 2. Visualizing Base64 Encoding vs. Decoding

**Encoding (Human-readable string $\rightarrow$ Base64 String):**
```
ASCII text:  "reForm" 
Byte Array:  [114, 101, 70, 111, 114, 109]
Base64:      "cmVGb3Jt"
```

**Decoding (Base64 String $\rightarrow$ Original Byte Array):**
```
Base64 Input: "cmVGb3Jt"
Byte Array:   [114, 101, 70, 111, 114, 109]
ASCII String: "reForm"
```

*   `getEncoded()` in Java's standard cryptography classes (like `Key`) returns the **raw binary representation (byte array)** of the cryptographic key material as specified by a standard format (like PKCS#8 or X.509). It does **not** generate human-readable text.
*   `getDecoded()` does not exist directly in the `Key` interface. Instead, we use helper utilities (like `Decoders.BASE64.decode()`) to convert a human-readable Base64 string back into raw cryptographic binary bytes.

---

### Part 2: JWT Structure, Anatomy & Journey

A JWT (JSON Web Token) is a stateless, signed identity ticket. It consists of three distinct parts separated by a dot (`.`):

$$\text{Header} \ . \ \text{Payload} \ . \ \text{Signature}$$

#### 1. Real-World Decoded Example

##### Header (Base64URL encoded)
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

##### Payload (Base64URL encoded)
```json
{
  "sub": "7a2d4f9e-bc91-49fa-92b0-8c281df69145",
  "email": "developer@reform.app",
  "role": "FORM_BUILDER",
  "iat": 1747051200,
  "exp": 1747052100
}
```

##### Signature (Cryptographic Verification Hash)
A mathematically calculated cryptographic output generated using the algorithm specified in the header, the encoded header, the encoded payload, and your private secret key:

$$\text{HMAC-SHA256}(\text{Base64Url}(\text{Header}) + "." + \text{Base64Url}(\text{Payload}), \ \text{secret\_key})$$

##### Raw Combined Output sent to Client:
```text
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3YTJkNGY5ZS1iYzkxLTQ5ZmEtOTJiMC04YzI4MWRmNjkxNDUiLCJlbWFpbCI6ImRldmVsb3BlckByZWZvcm0uYXBwIiwicm9sZSI6IkZPUk1fQlVJTERFUiIsImlhdCI6MTc0NzA1MTIwMCwiZXhwIjoxNzQ3MDUyMTAwfQ.X_XgW-qRk3Z-7x_5l_Yp9c-9Wf8e3Lg0y1Z7yU_o4e0
```

---

#### 2. What is a "Claim"?
A **Claim** is simply a key-value assertion stored inside the JWT Payload. It is a statement of truth made by the authentication server.
*   `sub` (Subject): The unique database UUID of the user.
*   `role` (Custom Claim): The user's programmatic access privileges.
*   `exp` (Expiration): The timestamp when this token becomes invalid.

---

#### 3. The Lifecycle Journey of a JWT

```
[ CLIENT ]                                                         [ REFORM SERVER ]
    │                                                                      │
    │ 1. POST /login { email, password }                                   │
    ├─────────────────────────────────────────────────────────────────────►│
    │                                                                      │ 2. Validates password hash in DB.
    │                                                                      │    Generates JWT (signed with Secret).
    │ 3. HTTP 200 { accessToken: "eyJ..." }                                │
    │◄─────────────────────────────────────────────────────────────────────┤
    │                                                                      │
    │───[ CLIENT STORES TOKEN IN MEMORY ]───                               │
    │                                                                      │
    │                                                                      │
    │ 4. GET /api/v1/workspaces                                            │
    │    Header -> Authorization: Bearer eyJ...                            │
    ├─────────────────────────────────────────────────────────────────────►│
    │                                                                      │ 5. Parse Authorization header.
    │                                                                      │    Decrypt signature with Secret.
    │                                                                      │    Integrity OK? Populate SecurityContext.
    │ 6. HTTP 200 [ Workspaces Payload ]                                   │
    │◄─────────────────────────────────────────────────────────────────────┤
    │                                                                      │
```

---

### Part 3: Code Architecture & Java Design Decisions

Let's address your questions about coding practices and standards.

#### 1. Why `@Component` instead of `@Service`?
*   `@Service` is a semantic Spring stereotype reserved for classes containing **business logic workflows** (use cases, transactional boundaries, domain modifications).
*   `@Component` is the correct technical stereotype for generic utility classes, cryptographic engines, and infrastructure adapters (such as file storage adapters or JWT engines) that perform purely mathematical or technical transformations without knowing business rules.

#### 2. Properties in `application.yml` vs. Static Constants
```yaml
app:
  security:
    secret: "dGhpcy1pcy1hLXNlY3VyZS1hbmQtMjg4LWJpdC1zZWNyZXQta2V5LTIwMjU="
    expiration-ms: 900000 # 15 minutes
```
*   **Why dynamic variables?** Hardcoding configurations (such as an expiration time) as static Java constants means you must recompile your entire application to change a configuration.
*   **The Power of Configuration:** Keeping them in `application.yml` allows you to change behavior dynamically between environments. For example, local developer testing might have a 24-hour expiration window, while production environments might enforce a strict 15-minute expiration limit.

---

#### 3. Method Signature Decision: Which one should we use?

*   **Option A:** `generateToken(User user)`
    *   *Drawback:* Tightly couples our cryptographic engine to our database JPA domain entity.
*   **Option B:** `generateToken(UserDetails userDetails)`
    *   *Advantage:* **This is the clean, industry-standard approach.** It relies on Spring Security's polymorphic interface contract. Any service or filter can generate a token using *any* standard principal container, keeping our package dependencies isolated. Inside the implementation class, we safely cast it to our custom wrapper to retrieve structural IDs.

---

#### 4. Can an interface extend another interface? Does `ITokenProvider` do this?
Yes, in Java, an interface can extend one or multiple other interfaces using the `extends` keyword:
```java
public interface ITokenProvider extends Serializable { ... }
```
However, **`ITokenProvider` should NOT extend any interfaces from Spring Security.** It is a custom "Port" (part of Hexagonal Architecture). Keeping our ports decoupled from external framework dependencies ensures that if we ever swap Spring Security for another framework, our core domain logic remains completely unmodified.

---

#### 5. Do we need a "UserPrincipal"?
No. In Spring Security, the authenticated "Principal" placed inside the `SecurityContext` is typically our custom implementation of `UserDetails` (which we built as `CustomerUserDetail`). There is no need to introduce another wrapper class.

---

### Part 4: Library Mechanics & Secret Keys

When verifying tokens, your code executes this statement:
```java
SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
```

#### 1. Demystifying the Line: Step-by-Step

```
1. Human-Readable Config:
   jwtSecret = "dGhpcy1pcy1hLXNlY3Vy..." (Base64 ASCII String)
                        │
                        ▼ [ Decoders.BASE64.decode() ]
2. Raw Binary Array:
   byte[] = [116, 104, 105, 115, 32, 105, 115...] (Raw cryptographical bytes)
                        │
                        ▼ [ Keys.hmacShaKeyFor() ]
3. Structured Cryptographic Object:
   SecretKey = (HMAC-SHA256 Cryptographic Algorithm Key Instance)
```

1.  `Decoders.BASE64.decode(jwtSecret)` takes the human-readable, printable ASCII text representation of your secret from `application.yml` and converts it back into its raw, unencoded binary format (`byte[]`).
2.  `Keys.hmacShaKeyFor(bytes)` takes that raw byte array and verifies that it is long enough for secure hashing (at least 256 bits/32 bytes). It then returns a strongly typed cryptographic `SecretKey` object initialized specifically for HMAC digital signing.
3.  **Why do we have a `SecretKey` type?** This is a type-safe object wrapper that contains the raw bytes and prevents security-sensitive key material from being printed as clear plaintext in standard application logs.

#### 2. Base64 vs. Base64URL: What is the difference?
*   **Base64:** Uses the standard character set including `+` and `/`. These characters are unsafe inside Web URLs because they function as query delimiters (`+` can mean a space, `/` is a path separator).
*   **Base64URL:** Replaces `+` with `-` and `/` with `_`, and drops the padding character `=`. This ensures the output token string can be passed safely as part of URL paths or HTTP headers without triggering URL parser conflicts.

---

### Part 5: Security Concerns (Replay Attacks & Revocation)

#### 1. What happens if a hacker steals an active JWT?
Because JWT authentication is **stateless**, the server does not keep a record of issued tokens. The server simply validates the signature. Therefore, **if an attacker steals an active JWT, they can impersonate that user completely until the token expires.** This is a classic Replay Attack.

#### 2. Prevention Strategies

1.  **Strictly Short Lifespans (Best Practice):** Keep your access tokens short-lived (e.g., 10 to 15 minutes). Once they expire, they are completely useless to an attacker.
2.  **Stateless Revocation List (Redis Blacklist):** When a user logs out, or if security anomaly detection detects suspicious behavior, save the unique ID (`jti`) of that specific token in a high-speed Redis memory cache with a Time-To-Live (TTL) matching the token's remaining lifespan. Your JWT filter can check this cache before proceeding.
3.  **Use of Refresh Tokens:** Access tokens are short-lived. Users obtain a long-lived, securely encrypted, stateful `RefreshToken` (stored securely inside HTTP-only, secure, samesite cookies) to renew access tokens automatically without requiring frequent password inputs.

---

### Part 6: Production-Ready Source Code

To build Step 2, we will use the modern **JJWT (0.12.x)** library. This syntax avoids deprecated builders and provides a secure, fluent API.

#### 1. The Decoupled Port (The Interface)
Create this file to define our cryptographic contract:

*   **Location:** `src/main/java/com/reForm/backend/auth/port/ITokenProvider.java`

```java
package com.reForm.backend.auth.port;

import org.springframework.security.core.userdetails.UserDetails;
import java.util.UUID;

public interface ITokenProvider {

    /**
     * Generates a stateless cryptographically signed JWT for the authenticated principal.
     */
    String generateToken(UserDetails userDetails);

    /**
     * Validates if the token's cryptographic signature is intact and not expired.
     */
    boolean validateToken(String token);

    /**
     * Extracts the core subject identifier (User UUID) from the payload claims.
     */
    UUID extractUserId(String token);
}
```

---

#### 2. The Cryptographic Implementation (The Adapter)
Create this file to implement token parsing and validation:

*   **Location:** `src/main/java/com/reForm/backend/auth/service/JwtTokenImpl.java`

```java
package com.reForm.backend.auth.service;

import com.reForm.backend.auth.port.ITokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenImpl implements ITokenProvider {

    private final String jwtSecret;
    private final Long expirationMs;

    public JwtTokenImpl(
            @Value("${app.security.secret}") String jwtSecret,
            @Value("${app.security.expiration-ms}") Long expirationMs) {
        this.jwtSecret = jwtSecret;
        this.expirationMs = expirationMs;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(this.jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        CustomerUserDetail customerUserDetails = (CustomerUserDetail) userDetails;
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + this.expirationMs);

        return Jwts.builder()
                .subject(customerUserDetails.getId().toString())
                .claim("email", customerUserDetails.getUsername())
                .claim("role", customerUserDetails.getAuthorities().iterator().next().getAuthority())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("JWT validation failure: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }
}
```

---

### Step 2 Status: COMPLETED

You have built the complete cryptographic token engine.

Before we integrate this engine into our HTTP request pipeline (Step 4: The Interceptor Filter), we must first answer our Socratic design challenge.

---

### Phase 5 — The Socratic Question

Now that your cryptographic engine is fully built:

> **If we use the purely stateless token validation strategy (extracting user details directly from the decrypted JWT payload instead of querying PostgreSQL on every request), how do we handle a scenario where a user's account is suspended or deleted *after* they have been issued a valid token?**
>
> **If their token is still active for another 10 minutes, is there a simple, elegant way to reject their request immediately without sacrificing our stateless database performance goals?**

---

### Phase 6 — The Call to Action

To proceed with Step 3 and 4:
1.  **Formulate your answer to the Socratic question above.**
2.  Let me know if you are ready to construct **Step 3: The Security Architecture & Gatekeeper**, and I will lay out the design specifications for your global configuration mappings.