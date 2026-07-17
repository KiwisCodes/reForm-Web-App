# CreateForm Request Flow

End-to-end path of `POST /api/v1/form?title=...` from the client to the
database row, as traced while debugging the two deserialization bugs below.

## Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant Tomcat as Tomcat / Servlet Filters
    participant Security as Spring Security Filter Chain
    participant Dispatcher as DispatcherServlet
    participant Controller as BuilderController
    participant Jackson as Jackson (HTTP message converter)
    participant Service as FormBuilderServiceImpl
    participant Hibernate
    participant Converter as AbstractBlockConverter
    participant DB as Postgres (forms table)

    Client->>Tomcat: POST /api/v1/form?title=...\n+ JSON body (blocks[])
    Tomcat->>Security: run filter chain
    Security->>Security: JwtAuthenticationFilter (extract/validate token, if any)
    Security->>Security: AuthorizationFilter checks route rules\n(/api/v1/form/** = permitAll)
    Security->>Dispatcher: request authorized, forward
    Dispatcher->>Controller: route to createForm()\n(requires "title" query param)
    Controller->>Jackson: deserialize body -> FormCreateDto
    Jackson->>Jackson: for each block, read "staticType"\n-> @JsonSubTypes picks concrete class\n(e.g. ShortTextStaticBlock)
    Jackson-->>Controller: FormCreateDto (blocks fully typed)
    Controller->>Service: createForm(request)  [@Transactional starts]
    Service->>Service: new Form(); set title, workspaceId,\nstatus=DRAFT, blocks
    Service->>Hibernate: repository.save(form)
    Hibernate->>Hibernate: persist(): build dirty-check snapshot
    Hibernate->>Converter: deep-copy "blocks" via round trip
    Converter->>Converter: convertToDatabaseColumn (serialize)\nconvertToEntityAttribute (re-parse)
    Converter-->>Hibernate: snapshot copy OK
    Hibernate->>Hibernate: @PrePersist: BaseEntity.onCreate()\n+ Form.onCreate() (generates slug from title)
    Hibernate->>Converter: convertToDatabaseColumn (final JSON for column)
    Hibernate->>DB: INSERT INTO forms (...)
    DB-->>Hibernate: row saved
    Hibernate-->>Service: managed Form entity
    Service->>Service: mapper.toResponseDto(form)  [transaction commits]
    Service-->>Controller: FormResponseDto
    Controller-->>Jackson: serialize response
    Jackson-->>Client: 200 OK + JSON body
```

## Where the two bugs lived

| Step | What broke | File |
|---|---|---|
| Jackson deserializes the request body | `AbstractBlock`'s `@JsonTypeInfo` pointed at the abstract `StaticBlock` class instead of resolving straight to the concrete leaf (`ShortTextStaticBlock`, etc.) — Jackson doesn't cascade nested `@JsonTypeInfo` annotations | `AbstractBlock.java` / `StaticBlock.java` |
| Hibernate's dirty-check snapshot round-trips `blocks` through the converter | `convertToDatabaseColumn` called `writeValueAsString(Object)` on a raw `List`, losing the generic element type at runtime — Jackson never attached the `staticType` discriminator, so the immediate re-parse failed | `AbstractBlockConverter.java` |

Both surfaced as a **misleading `401 Unauthorized`** at the HTTP layer: any
unhandled exception forwards internally to `/error`, and since `/error` isn't
`permitAll`, Spring Security rejected that *forwarded* request as
unauthenticated — masking the real 500-level bug underneath.

## Notable side-quests during this debug session

- `SecurityConfig` requires a `JWT_SECRET_KEY` env var with no default —
  missing it fails startup with `Unable to start embedded Tomcat`.
- `X-Workspace-Id` header is required by `createForm`'s method signature but
  never actually used by the service (`workspaceId` comes from the JSON body
  instead) — a latent inconsistency, not a hard blocker.
- `title` is not optional even though `FormCreateDto` doesn't enforce it at
  the DTO level — `Form.onCreate()` throws `IllegalStateException` if
  `title` is blank, since the slug is derived from it.
