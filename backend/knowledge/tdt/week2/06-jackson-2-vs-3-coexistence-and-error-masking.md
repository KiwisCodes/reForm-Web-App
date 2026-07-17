# Root-Causing the Submission Endpoint's 401s and 500s

After the four submission blockers (see the previous changelog) were fixed, the
`POST /api/v1/submissions/{slug}` endpoint still didn't behave correctly end to
end. This document tracks that debugging session: a masking bug that made real
errors look like authentication failures, and a two-major-versions-of-Jackson
coexistence problem underneath it, surfaced one layer at a time by reading actual
stack traces and a dependency tree rather than guessing.

---

### Obstacle 1: Real 400s/404s show up as 401 Unauthorized

**The problem:** Requests that should have failed with 404 (bad slug) or 400
(empty required field, unpublished form) all came back as `401 Unauthorized`
instead — even though the endpoints are correctly `permitAll()`'d and the request
genuinely reached the controller.

**Why it was an obstacle — the actual mechanism:** When a `@ResponseStatus`
exception (e.g. `ResourceNotFoundException`) propagates out of a controller,
Spring's `ResponseStatusExceptionResolver` calls `HttpServletResponse.sendError(...)`.
That doesn't write the response directly — per the servlet spec, it hands off to
the container's error-page mechanism, which Spring Boot wires to internally
**forward** the request to `/error` (`BasicErrorController`) so it can render a
proper JSON body. That forward is a second, full pass through the servlet
pipeline — including Spring Security's filter chain, which re-evaluates
`authorizeHttpRequests` from scratch for the new target path (`/error`), with no
memory that the original request was already permitted. Since `/error` wasn't in
the `permitAll()` list, it fell under `.anyRequest().authenticated()` — and since
these are anonymous requests (no JWT, correctly), that check fails and
`CustomAuthenticationEntryPoint` writes a 401, overwriting whatever the real
exception's status/body was supposed to be. The client never sees the original
404/400 — it's masked by a second, invisible, unauthenticated request to `/error`.

This is the same mechanism already documented once before in
`knowledge/pth/week1/.../03-create-form-request-flow.md` for an unrelated bug — it
resurfaces any time an endpoint throws past an authorization boundary that
doesn't also cover `/error`.

**The fix:**

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/api/v1/users/register").permitAll()
        .requestMatchers("/api/v1/submissions/**").permitAll()
        .requestMatchers("/api/v1/f/**").permitAll()
        .requestMatchers("/error").permitAll()
        .anyRequest().authenticated()
)
```

**A wrong turn along the way:** this fix was applied once, then found
**commented out** again later in the same file (`//  .requestMatchers("/error").permitAll()`),
which reintroduced the exact same symptom for the bad-slug and empty-field test
cases. Worth double-checking a fix wasn't silently reverted before assuming a new
bug exists.

---

### Obstacle 2: Jackson 2 `JsonNode` vs. Jackson 3 `JsonNode` — the HTTP layer

**The problem:** A genuine (unmasked, after fixing Obstacle 1) `500` on
`saveSubmission`, with this root cause:

```
tools.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance
of `com.fasterxml.jackson.databind.JsonNode` (no Creators, like default
constructor, exist): abstract types either need to be mapped to concrete types...
```

**Why it was an obstacle:** `pom.xml` pins `spring-boot-starter-parent` to
`4.1.0` — Spring Boot 4, which defaults to **Jackson 3**
(package renamed `com.fasterxml.jackson.*` → `tools.jackson.*`). But
`RESTSubmissionController`, `SubmissionProcessorImpl`, `Submission` (entity), and
`ISubmissionProcessor` all imported `JsonNode` from the old
`com.fasterxml.jackson.databind` package (Jackson 2 — also still explicitly pinned
in `pom.xml` at the time). Jackson 3's `HttpMessageConverter` has no idea how to
construct Jackson 2's *different, unrelated* `JsonNode` class — same class name,
two incompatible library generations.

**The fix:** swapped the import in all four files:

```java
// BEFORE
import com.fasterxml.jackson.databind.JsonNode;

// AFTER
import tools.jackson.databind.JsonNode;
```

---

### Obstacle 3: The same coexistence bug, one layer deeper — Hibernate's JSON column

**The problem:** After Obstacle 2's fix, a *different* 500 appeared — same shape,
opposite direction:

```
org.springframework.dao.InvalidDataAccessApiUsageException: Could not deserialize
string to java type: class tools.jackson.databind.JsonNode

com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct
instance of `tools.jackson.databind.JsonNode` (no Creators...)
    at ...(jackson-databind-2.18.2.jar:2.18.2)
```

This time Jackson **2**'s `ObjectMapper` was the one failing to construct Jackson
**3**'s `JsonNode`. `InvalidDataAccessApiUsageException` is Spring Data's wrapper —
this was coming from Hibernate's own JSON type handling for the
`Submission.answers` column (`@JdbcTypeCode(SqlTypes.JSON)`), not the HTTP layer.

**Why it was an obstacle:** having *both* Jackson majors on the classpath at once
means different subsystems can each independently auto-detect and lock onto a
different one. Spring MVC's converters correctly picked Jackson 3; Hibernate's
JSON format-mapper auto-detection picked Jackson 2.

**A wrong turn along the way:** removing the explicit
`com.fasterxml.jackson.core:jackson-databind:2.18.2` dependency from `pom.xml`
looked like the fix, but the *exact same* error kept happening afterward. Running

```
./mvnw dependency:tree -Dincludes=com.fasterxml.jackson.core:jackson-databind
```

showed Jackson 2 was still present — pulled in **transitively**:

```
io.jsonwebtoken:jjwt-jackson:0.13.0 (runtime)
  └─ com.fasterxml.jackson.core:jackson-databind:2.21.4 (runtime)
```

`jjwt-jackson` (JJWT's JSON serializer for JWT claims) is built against Jackson 2
and pulls it in unconditionally — nothing to do with our own code, and not
something removable without changing JJWT's serialization backend entirely.

**The fix:** rather than fight JJWT's dependency chain, told Hibernate explicitly
which `FormatMapper` to use instead of letting it auto-detect. Verified (via
Hibernate's own javadocs, not assumed) that Hibernate ORM 7.3+ ships a built-in
Jackson 3 mapper: `org.hibernate.type.format.jackson.Jackson3JsonFormatMapper`,
sitting alongside the classic `JacksonJsonFormatMapper`. Our resolved Hibernate
version, `7.4.1.Final`, has it.

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
        type:
          json_format_mapper: org.hibernate.type.format.jackson.Jackson3JsonFormatMapper
```

This makes Hibernate's JSON handling unambiguous regardless of what else is on
the classpath — `jjwt-jackson`'s Jackson 2 stays isolated to JJWT's own token
signing/parsing and never gets a chance to be picked for anything else.

*Sources consulted while diagnosing this (Hibernate/Jackson 3 support is recent
enough that guessing the config property or class name from memory wasn't safe):*
- [Missing FormatMapper for JSON format with Jackson 3.x, Hibernate 7.x](https://discourse.hibernate.org/t/missing-formatmapper-for-json-format-with-jackson-3-x-hibernate-7-x/11819)
- [org.hibernate.type.format.jackson package summary (Hibernate 7.3 Javadocs)](https://docs.hibernate.org/orm/7.3/javadocs/org/hibernate/type/format/jackson/package-summary.html)
- [What's New in 7.3 — Hibernate ORM](https://docs.hibernate.org/orm/7.3/whats-new/)

---

### Obstacle 4: Two more files still on Jackson 2, blocking full cleanup

**The problem:** `AbstractBlockConverter` (the `Form.blocks` JSONB
`AttributeConverter`) imported Jackson 2's `ObjectMapper`/`JavaType`, and
`PublicRenderController` had a leftover, unused `JsonNode` import — both would
break compilation once Jackson 2 was fully removed, and `AbstractBlockConverter`
in particular is the same class that had a nasty polymorphic-deserialization bug
once before (see `03-create-form-request-flow.md`), so it needed care, not a
blind find-and-replace.

**The fix:**

```java
// AbstractBlockConverter.java — BEFORE
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

// AFTER
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
```

`PublicRenderController`'s dead `JsonNode` import was simply deleted — nothing in
the file referenced it.

Verified afterward: no file in `src/main/java` imports anything from
`com.fasterxml.jackson.databind` anymore.

---

## A non-bug worth noting

While fixing Obstacle 2, `SubmissionProcessorImpl`'s blank-field check changed
from `answers.path(key).asText()` to `answers.path(key).asString()`. That looked
suspicious at first glance, but it's correct: Jackson 3 deprecated `asText()` in
favor of `asString()` as part of a broader `Text` → `String` renaming across the
API (confirmed via Jackson's own migration notes, not assumed) — same behavior,
new name.

*Sources:*
- [Rename `TextNode` as `StringNode`; `JsonNode.xxxTextYyy()` as `JsonNode.xxxStringYyy()` (JSTEP-3)](https://github.com/FasterXML/jackson-databind/issues/4879)
- [Rename Jackson 2.x methods to 3.x equivalents for JsonNode — OpenRewrite Docs](https://docs.openrewrite.org/recipes/java/jackson/upgradejackson_2_3_jsonnodemethodrenames)

---

## Status after these fixes

All four obstacles are resolved: `/error` is `permitAll()`'d (again — watch for
this being commented out a third time), the HTTP layer and the Hibernate
persistence layer both consistently use Jackson 3, and the last two
Jackson-2-dependent files are migrated. `jjwt-jackson`'s Jackson 2 dependency
remains on the classpath by design — it's isolated to JWT token handling and no
longer able to interfere with anything else.
