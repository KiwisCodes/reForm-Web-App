# Form Module: Authorization Layer & Status Code Fixes

This document tracks the obstacles hit while adding a `FormSecurity` authorization
layer to `BuilderController` (mirroring the existing `WorkspaceSecurity` pattern),
and while cleaning up the HTTP status codes those endpoints return. Each entry is
an obstacle we actually hit, why it blocked correct behavior, and the fix applied.

---

### Obstacle 1: `Form` has no relation to `Workspace` to query membership through

**The problem:** `WorkspaceSecurity.isMember` works because `Workspace.members` is
a real `@ManyToMany Set<User>` — Spring Data can derive
`existsByIdAndMembersId(...)` from it. `Form`, however, only stores a raw
`UUID workspaceId` column — no JPA relation — so there's no navigable path from
`Form` to `Workspace.members` for a derived query to walk.

**Why it was an obstacle:** It forced a real design decision before `FormSecurity`
could even be written: either add a `@ManyToOne Workspace` relation to `Form`
(bigger schema change, and it would pull the `user` module's `Workspace` entity
into `form.entity` as a compile-time import — the exact cross-module coupling the
project's hexagonal rules try to avoid), or keep `workspaceId` as a raw UUID and
resolve membership by delegating to `WorkspaceSecurity` at runtime.

**The fix:** Kept `Form.workspaceId` as a raw UUID (no schema change). `FormSecurity`
resolves the form via `FormRepository`, then delegates the actual membership/ownership
decision to the existing `WorkspaceSecurity` bean:

```java
@Component("formSecurity")
@RequiredArgsConstructor
public class FormSecurity {

    private final FormRepository repository;
    private final WorkspaceSecurity workspaceSecurity;

    public boolean isMember(Authentication authentication, UUID formId) {
        return repository.findById(formId)
                .map(form -> workspaceSecurity.isMember(authentication, form.getWorkspaceId()))
                .orElse(false);
    }
}
```

---

### Obstacle 2: "Form not found" threw an exception instead of denying access

**The problem:** An early draft resolved the form with
`.orElseThrow(() -> new RuntimeException("Form not found!"))` instead of returning
`false`.

**Why it was an obstacle:** This method only ever runs *inside* a `@PreAuthorize`
SpEL evaluation. Throwing an unchecked exception there risks surfacing as a raw
500 instead of a clean 403, and — separately — returning `false` (not an error) for
a nonexistent resource avoids leaking whether the resource exists at all to an
unauthorized caller.

**The fix:** Switched to `Optional.map(...).orElse(false)` chaining, matching
`WorkspaceSecurity`'s existing idiom — a single query, no exception, `false` if the
form doesn't exist:

```java
// BEFORE
public boolean isMember(Authentication authentication, UUID formId) {
    Form form = repository.findById(formId).orElseThrow(() -> new RuntimeException("Form not found!"));
    UUID workspaceId = form.getWorkspaceId();
    return workspaceSecurity.isMember(authentication, workspaceId);
}

// AFTER
public boolean isMember(Authentication authentication, UUID formId) {
    return repository.findById(formId)
            .map(form -> workspaceSecurity.isMember(authentication, form.getWorkspaceId()))
            .orElse(false);
}
```

(An intermediate attempt fixed the "return false" behavior but called
`repository.findById(formId)` twice — once for `.isEmpty()`, once for
`.orElseThrow(...)` — two DB round trips for one row. The `Optional.map()` version
above does it in one query.)

---

### Obstacle 3: `@PreAuthorize`'s SpEL variable didn't match the controller's path variable

**The problem:** `getForm`'s annotation referenced `#formId`, but the method
parameter was declared `@PathVariable UUID id` — no variable named `formId` existed
in the SpEL evaluation context.

**Why it was an obstacle:** SpEL resolves parameter names by binding to the actual
method signature. A mismatched name means the expression can't resolve at all.

**The fix:** Renamed the path variable to match the SpEL reference:

```java
// BEFORE
@GetMapping("{id}")
@PreAuthorize("@formSecurity.isMember(authentication, #formId)")
public ResponseEntity<FormResponseDto> getForm(@PathVariable UUID id, @RequestHeader("X-Workspace-Id") UUID workspaceId){

// AFTER
@GetMapping("{formId}")
@PreAuthorize("@formSecurity.isMember(authentication, #formId)")
public ResponseEntity<FormResponseDto> getForm(@PathVariable UUID formId, @RequestHeader("X-Workspace-Id") UUID workspaceId){
```

---

### Obstacle 4: Not every `BuilderController` endpoint has a `formId` to check

**The problem:** `getAllForms` and `createForm` don't operate on an existing form —
there's nothing for `FormSecurity.isMember(formId)` to resolve. An early draft
still pointed `createForm`'s `@PreAuthorize` at `@formSecurity.isMember(authentication, #workspaceId)`
— passing a *workspace* UUID into a method that does `repository.findById(formId)`
on a `forms` table. That lookup always misses, so the check always evaluated to
`false` — **`createForm` was permanently blocked for everyone.**

**Why it was an obstacle:** The five endpoints actually split into two categories:
three have both a `formId` and a `workspaceId` header (`getForm`, `updateBlocks`,
`deleteForm`); two have only a `workspaceId` header (`getAllForms`, `createForm`).
The form-scoped endpoints also raised a security question: should they trust the
client-supplied `X-Workspace-Id` header, or derive the true workspace from the
form record itself? Trusting the header would let a member of Workspace A request
a form that actually belongs to Workspace B, by claiming Workspace A in the header.

**The fix:** Form-scoped endpoints use `@formSecurity.isMember`/`isCreator`
(resolves the *real* workspace from the form, ignoring the header). Endpoints with
no form yet use `@workspaceSecurity.isMember` directly against the header:

```java
@GetMapping
@PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
public ResponseEntity<List<FormResponseDto>> getAllForms(@RequestHeader("X-Workspace-Id") UUID workspaceId){ ... }

@PostMapping(params = "title")
@PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
public ResponseEntity<FormResponseDto> createForm(@RequestHeader("X-Workspace-Id") UUID workspaceId, ...){ ... }

@GetMapping("{formId}")
@PreAuthorize("@formSecurity.isMember(authentication, #formId)")
public ResponseEntity<FormResponseDto> getForm(@PathVariable UUID formId, ...){ ... }
```

---

### Obstacle 5: A client-supplied `creatorId` would let anyone spoof authorship

**The problem:** The original plan was to add `creatorId` straight into
`FormCreateDto` as a client-supplied field.

**Why it was an obstacle:** Trusting an identity field from request input is a
classic spoofing vector — any caller could claim to be a different user's UUID as
the form's creator, or claim someone else's identity outright.

**The fix:** Kept `creatorId` **out** of `FormCreateDto` entirely. The controller
resolves it from the authenticated principal and threads it through explicitly:

```java
// Form.java — write-once column, never client-supplied
@Column(updatable = false)
private UUID creatorId;

// FormCreateDto.java — unchanged, no creatorId field
public record FormCreateDto(String title, UUID workspaceId, List<AbstractBlock> blocks) {}

// BuilderController.java
public ResponseEntity<FormResponseDto> createForm(
        @RequestHeader("X-Workspace-Id") UUID workspaceId,
        @RequestBody FormCreateDto request,
        @AuthenticationPrincipal CustomerUserDetails customerUserDetails){
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.createForm(request, customerUserDetails.getId()));
}

// FormBuilderServiceImpl.java
public FormResponseDto createForm(FormCreateDto request, UUID creatorId) {
    Form form = new Form();
    ...
    form.setCreatorId(creatorId);
    repository.save(form);
    return mapper.toResponseDto(form);
}
```

---

### Obstacle 6: `isCreator` ignored `formId`, letting any past creator delete any form

**The problem:** An early draft of `isCreator` checked
`repository.existsByCreatorId(currentUserId)` — "has this user created *any* form,
anywhere?" — with no reference to the specific `formId` being authorized.

**Why it was an obstacle:** This was a live authorization bypass. `deleteForm`'s
`@PreAuthorize` is `@formSecurity.isCreator(authentication, #formId)` alone, with no
other check combined. Since `existsByCreatorId` ignored `formId`, any user who had
ever created one form anywhere in the system would pass `isCreator` for *every*
`formId* — including forms in workspaces they were never even a member of.

**The fix:** Added a repository method scoped by both fields, and used it:

```java
// FormRepository.java
Boolean existsByCreatorIdAndId(UUID creatorId, UUID formId);

// FormSecurity.java
public boolean isCreator(Authentication authentication, UUID formId){
    return  isOwner(authentication, formId)
            ||
            getCurrentUserId(authentication)
                    .map(currentUserId -> repository.existsByCreatorIdAndId(currentUserId, formId))
                    .orElse(false);
}
```

> **Not yet resolved:** the `isOwner(...) ||` clause means the workspace owner can
> currently delete *any* form regardless of who created it, at all times — not just
> as a fallback once the real creator has left the workspace (which was the
> original intent, to be handled later by a domain event that reassigns
> `creatorId` in the database). This is a deliberate open question, not a bug.

---

### Obstacle 7: HTTP status codes were inconsistent ("random")

**The problem:** Two separate issues compounded into unpredictable status codes:
1. `createForm` returned `ResponseEntity.ok(...)` (200) for a POST that creates a
   new resource, instead of 201.
2. `FormBuilderServiceImpl` (`retrieveForm`, `updateBlocks`, `delete`) and
   `FormQueryService.fetchForm` threw raw `IllegalStateException`/`RuntimeException`
   for "not found" — neither has a `@ResponseStatus` mapping, so Spring's default
   error handling returned **500**, not 404.

**Why it was an obstacle:** `WorkspaceController` already established the
convention (explicit `ResponseEntity.status(HttpStatus.X)` per method, and the
existing `core.exception.ResourceNotFoundException` — already annotated
`@ResponseStatus(HttpStatus.NOT_FOUND)` and already used by `WorkspaceServiceImpl`/
`UserServiceImpl`) but the form module wasn't following it.

**The fix:** Matched the existing convention instead of inventing a new one:

```java
// BuilderController.java
@PostMapping(params = "title")
@PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
public ResponseEntity<FormResponseDto> createForm(...){
    FormResponseDto formResponseDto = service.createForm(request, customerUserDetails.getId());
    return ResponseEntity.status(HttpStatus.CREATED).body(formResponseDto);
}

// FormBuilderServiceImpl.java
public FormResponseDto retrieveForm(UUID workspaceId, UUID id) {
    return mapper.toResponseDto(
            repository.findByIdAndWorkspaceId(id, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException(id + " not found!")));
}
```

---

### Obstacle 8: The `.http` test file broke once security started actually enforcing

**The problem:** `user-workspace.http`'s form section hardcoded a stale
`workspaceId` (`0770720f-...`) left over from before `@PreAuthorize` was wired up —
back when nothing checked whether that ID was real.

**Why it was an obstacle:** Once `@workspaceSecurity.isMember`/`@formSecurity.isMember`
started actually running, every form request 403'd, because Alex wasn't a member of
that stale, unrelated workspace ID.

**The fix:** Pointed every form-section request at `{{workspaceId}}` — the variable
already captured from the real `CreateWorkspace` response — instead of the
hardcoded UUID:

```http
### BEFORE
X-Workspace-Id: 0770720f-8dd1-4f86-85f1-263d631edbd7

### AFTER
X-Workspace-Id: {{workspaceId}}
```

---

## Still open (not fixed, worth tracking)

- `isOwner(...) ||` inside `isCreator` (Obstacle 6) — permanent owner override vs.
  event-driven fallback only after the real creator leaves the workspace.
- A leftover comment in `BuilderController.deleteForm` questioning whether the
  `X-Workspace-Id` header is still needed for the service-layer lookup now that
  `FormSecurity` already derives the true workspace from `formId` — never resolved.
- No endpoint transitions a form from `DRAFT` to `PUBLISHED` anywhere in the API —
  discovered while starting to test the submission module, out of scope for this doc.
