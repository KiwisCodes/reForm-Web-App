# Submission Module: Blockers Found While Writing Tests, and Their Fixes

While writing a `.http` test suite for `POST /api/v1/submissions/{slug}`, four
blockers surfaced that prevented the happy path (and even some negative paths)
from returning correct behavior. This document tracks each one: what it was, why
it blocked correct behavior, the wrong turns taken along the way, and the fix that
landed.

---

### Obstacle 1: No endpoint transitions a form from `DRAFT` to `PUBLISHED`

**The problem:** `FormBuilderServiceImpl.createForm` always sets `FormStatus.DRAFT`.
Nothing in the API ever moved a form to `PUBLISHED`.

**Why it was an obstacle:** `SubmissionProcessorImpl.saveSubmission` rejects any
submission to a `DRAFT` form. With no way to publish, the entire successful-submission
path was untestable through the API.

**The fix:** Added a dedicated publish action, following the sub-resource path
convention already used by `updateBlocks` (`/{formId}/blocks`) rather than a
query-param disambiguator, and guarded it with `isCreator` (same tier as delete,
since publishing makes a form permanently live):

```java
@PatchMapping("{formId}/publish")
@PreAuthorize("@formSecurity.isCreator(authentication, #formId)")
public ResponseEntity<FormResponseDto> publishForm(@PathVariable UUID formId,
                                                    @RequestHeader("X-Workspace-Id") UUID workspaceId){
    FormResponseDto formResponseDto = service.publishForm(formId, workspaceId);
    return ResponseEntity.status(HttpStatus.OK).body(formResponseDto);
}
```

```java
// FormBuilderServiceImpl.java
@Override
public FormResponseDto publishForm(UUID formId, UUID workspaceId) {
    Form form = repository.findByIdAndWorkspaceId(formId, workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException(formId + " not found!"));
    form.setStatus(FormStatus.PUBLISHED);
    repository.save(form);
    return mapper.toResponseDto(form);
}
```

**A wrong turn along the way:** the first draft reused `FormUpdateDto` (built for
`updateBlocks`, shaped `{id, workspaceId, blocks}`) as the request body. That forced
clients to submit an irrelevant `blocks` array just to publish a form, and the
service read `request.id()`/`request.workspaceId()` from the body instead of the
already-validated `formId`/`workspaceId` path/header params — meaning the same
identifiers existed in two channels, and the one actually used for the DB write
wasn't the one `@PreAuthorize` had checked. Fixed by dropping the body entirely,
mirroring `deleteForm`'s no-body shape.

---

### Obstacle 2: The anonymous-facing endpoints weren't actually public

**The problem:** `POST /api/v1/submissions/{slug}` (`RESTSubmissionController`) and
`GET /api/v1/f/{slug}` (`PublicRenderController`) have no `@PreAuthorize` and no
principal usage in either service — clearly designed for anonymous respondents. But
`SecurityConfig`'s `authorizeHttpRequests` only had `permitAll()` on
`/api/v1/auth/**` and `/api/v1/users/register`; everything else fell under
`.anyRequest().authenticated()`.

**Why it was an obstacle:** An anonymous survey respondent — the entire point of
the submission module — would get 401 trying to view or submit a form.

**The fix:**

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/api/v1/users/register").permitAll()
        .requestMatchers("/api/v1/submissions/**").permitAll()
        .requestMatchers("/api/v1/f/**").permitAll()
        .anyRequest().authenticated()
)
```

**A wrong turn along the way:** the first attempt was
`.requestMatchers("/api/v1/submissions").permitAll()` — no `/**` wildcard. Spring
Security matches that literally, so it only covered the exact path
`/api/v1/submissions` with nothing after it. The real endpoint,
`/api/v1/submissions/{slug}`, never matched, and the rule was silently a no-op.
`/api/v1/f/**` was also missing entirely in that first pass and had to be added
afterward.

---

### Obstacle 3: "Not found" / "not published" errors returned 500, not 404/400

**The problem:** `RESTSubmissionController`, `FormRenderingServiceImpl`, and
`SubmissionProcessorImpl` all threw raw, unmapped exceptions
(`IllegalStateException`, `RuntimeException`) with no `@ResponseStatus` — Spring's
default error handling turns those into 500 regardless of what actually went wrong.
This is the same pattern already fixed once in `FormBuilderServiceImpl`
(see the previous changelog) — the fix just hadn't been applied to these files yet.

**Why it was an obstacle:** A missing form, an unpublished form, and a validation
failure are three different classes of error a client should be able to
distinguish by status code (404 vs 400) — none of that was surfaced.

**The fix:** `ResourceNotFoundException` (`@ResponseStatus(NOT_FOUND)`) already
existed for "doesn't exist" cases. A new sibling was added for "exists but the
request is invalid":

```java
// core/exception/ResourceNotAccessedException.java
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ResourceNotAccessedException extends RuntimeException {
    public ResourceNotAccessedException(String message) {
        super(message);
    }
}
```

Applied across all three files:

```java
// RESTSubmissionController.java
Form form = repository.findBySlug(slug)
        .orElseThrow(() -> new ResourceNotFoundException("Form does not exist"));

// FormRenderingServiceImpl.java
Form form = repository.findBySlug(slug)
        .orElseThrow(() -> new ResourceNotFoundException("Form does not exist!"));
if (form.getStatus() == FormStatus.DRAFT) throw new ResourceNotAccessedException("Form is not published!");

// SubmissionProcessorImpl.java
if (form.status() == FormStatus.DRAFT){
    throw new ResourceNotAccessedException("The form is not published.");
}
```

**A wrong turn along the way:** an intermediate fix reached for
`org.springframework.web.client.ResourceAccessException` instead — a
similarly-named but unrelated Spring class. It belongs to `RestTemplate`, thrown
when *this app* fails to call *another* server over HTTP (connection refused,
timeout, DNS failure) — not something meant to be thrown from your own service
logic to signal an invalid request. It has no `@ResponseStatus` either, so it kept
resolving to 500 despite looking like a fix. It was used this way in two places
(`SubmissionProcessorImpl` and `FormRenderingServiceImpl`) before being replaced
with the purpose-built `ResourceNotAccessedException` above.

---

### Obstacle 4: A missing (not just blank) required field threw an uncontrolled NPE

**The problem:** `SubmissionProcessorImpl` validated required fields with:

```java
if (answers.get(key).asText().isBlank()) throw new RuntimeException("Field cannot be empty");
```

`JsonNode.get(key)` returns Java `null` if `key` isn't present in the JSON at all
(as opposed to being present with an empty value). Calling `.asText()` on that
`null` throws a raw `NullPointerException` — a different, uncontrolled failure mode
from the intentional "field cannot be empty" validation error, for what's
conceptually the same problem (no usable answer was provided).

**The fix:** Swapped `get` for Jackson's null-safe `path`, which returns a
`MissingNode` (a real, non-null `JsonNode`) instead of `null` when the key is
absent — so a missing key and a blank value now both resolve to `""` and hit the
same, correct validation branch:

```java
if (answers.path(key).asText().isBlank()) throw new ResourceNotAccessedException("Field cannot be empty");
```

**A wrong turn along the way:** an intermediate fix changed the thrown exception
to `throw new NullPointerException("Field cannot be empty")` — deliberately
throwing `NullPointerException` as an expected validation outcome. NPE
conventionally signals a programming bug (an unexpected null dereference), not a
client validation failure, and it still had no `@ResponseStatus` mapping, so it
was still 500. It also didn't touch the actual bug: `answers.get(key)` was still
being called, so a genuinely missing key still triggered an *unintentional* NPE
with a JVM-generated message, coexisting confusingly with the *intentional* one for
the blank-value case. The final fix (above) replaced both the exception type and
the `get`/`path` call in one pass.

---

## Status after these fixes

All four blockers are resolved. One piece of cleanup remains: `ResourceAccessException`
imports are now unused dead code in `SubmissionProcessorImpl` and
`FormRenderingServiceImpl` (the type they used to reference was removed, the
`import` line wasn't).
