---
name: contract-first-api
description: Add or change a REST endpoint of a SyncChord module contract-first with OpenAPI (spec in backend/src/main/resources/openapi/<module>.yaml, openapi-generator-maven-plugin execution, controller implementing the generated interface). Use when a ticket adds a REST API to a new module (tabs, rooms, sync, rtc, billing, moderation), adds or changes an endpoint of a module that already has a spec, or moves a hand-written controller to a spec.
---

# Contract-first REST endpoints

CLAUDE.md section 4 holds the rules; this is the procedure used for `tabs` (KAN-91, KAN-31) and `rooms` (KAN-34).
Existing msp-app controllers stay hand-written unless a ticket says otherwise. Combine with `kan-ticket` (branch,
PR, Jira) and, for a new module, `modulith-extract`.

## 1. The spec: `backend/src/main/resources/openapi/<module>.yaml`
Start from `tabs.yaml` and keep its shape:
- `openapi: 3.0.3`; `info.description` says what the API is (with the ticket key), who may read/change what, and
  that a missing or invalid token gets a bodiless 401 from the security filter. One tag per module
  (`tags: [Rooms]` → interface `RoomsApi`). Global `security: [bearerAuth: []]` and the `bearerAuth` scheme.
- Every operation has an `operationId` (it becomes the Java method name), a one-line `summary`, and every error
  status it can really return, each with `$ref: '#/components/schemas/ErrorResponse'` (copy that schema; it
  matches `GlobalExceptionHandler`'s body).
- **No validation keywords** (`minLength`, `maximum`, `pattern`, `required` on request body properties used as
  checks...). The plugin runs with `useBeanValidation=false`; the service checks input and throws
  `IllegalArgumentException`, so the error keeps the handler's body. Write the rules into `description` instead.
  `required` on *response* properties is fine (it documents non-null fields), and so is `required` on a multipart
  file part (`required: [file]` in `tabs.yaml`), which Spring enforces before the service runs.
- Timestamps: `type: string, format: date-time` → Java `Instant` (via the type and import mappings in the pom).
- Lists that page: an explicit schema with Spring-page-like fields (`TabPage`: `content`, `number`, `size`,
  `numberOfElements`, `totalElements`, `totalPages`, `first`, `last`, `empty`), never Spring's `Page<T>`; the request
  takes `pageNo` and `pageSize` query parameters. Cap the page size in the service (`MAX_PAGE_SIZE`), and escape `%`, `_` and `\` in
  `LIKE` searches (`TabService.escapeLike` with `ESCAPE '\\'` in the query).
- Multipart uploads: `multipart/form-data` with `format: binary` for the file; the method gets `MultipartFile` plus
  one parameter per form field.
- Name models after what they are in the API (`TabResponse`, `RoomCreateRequest`). They must not clash with the
  module's entities, because both may be imported in the mapping service.

## 2. The plugin execution (`backend/pom.xml`)
Copy an existing `<execution>` of `openapi-generator-maven-plugin`; only three values change:
```xml
<execution>
    <id>generate-<module>-api</id>
    <goals><goal>generate</goal></goals>
    <configuration>
        <inputSpec>${project.basedir}/src/main/resources/openapi/<module>.yaml</inputSpec>
        <apiPackage>com.romanosadchyi.smp.music_app.<module>.internal.api</apiPackage>
        <modelPackage>com.romanosadchyi.smp.music_app.<module>.internal.api.model</modelPackage>
    </configuration>
</execution>
```
The shared `<configuration>` is already right; do not change it per module. Things that went wrong before:
- `typeMapping OffsetDateTime=Instant` alone generates models without the `Instant` import; the
  `importMapping OffsetDateTime=java.time.Instant` next to it is what makes them compile.
- `skipDefaultInterface` keeps the generator from emitting `ApiUtil` (`generateSupportingFiles` is off).
- Generated code goes to `target/generated-sources/openapi`; it is never committed and never in a shared package
  (it lives in `internal`, so `ArchitectureTest` treats it as the module's own).

Run `./mvnw -q compile` from `backend/` and read the generated interface (`target/generated-sources/openapi/src/main/java/.../<module>/internal/api/<Tag>Api.java`) for the exact method signatures before writing the
controller.

## 3. The controller
```java
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomController implements RoomsApi {
    RoomService roomService;

    @Override
    public ResponseEntity<RoomResponse> createRoom(RoomCreateRequest request) {
        UUID userId = CurrentUser.get().id();
        log.info("Creating room for user: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(userId, request));
    }
}
```
- No mapping annotations: the interface holds them. Javadoc says it implements the interface generated from
  `openapi/<module>.yaml` (see `TabController`).
- The current user is `CurrentUser.get()`, never `@AuthenticationPrincipal` (the overriding method cannot add a
  parameter) and never the `User` entity.
- The controller only delegates. Services take and return the generated models; entity ↔ model mapping is in the
  module's `*MappingService` (batched `UserLookup.findSummaries` for lists). Internal-only inputs that are not API
  models (e.g. the multipart fields) get a small record in `internal.dto`.
- 201 for creates, 204 (`ResponseEntity.noContent().build()`) for deletes, as the spec says.

## 4. Security and errors
- `SecurityConfig`: `/api/**` already needs authentication; add a rule only for something public or role-bound.
- Domain errors: exceptions extending `NotFoundException` / `ConflictException`, `UserNotAuthorizedException` for 403,
  `IllegalArgumentException` for 400. No per-exception handlers. A resource the user may not read answers 404, not
  403, when its existence should not leak (tabs).
- When a hand-written API moves to a spec, compare old and new behaviour: the generated `consumes`/`produces` change
  which `Content-Type`/`Accept` headers are accepted, and an error must still come back as JSON when the client sent
  `Accept: application/json`. One integration test per changed endpoint pins it.

## 5. Tests
- Unit tests (`@ExtendWith(MockitoExtension.class)`) for the service rules: every rejection branch the spec
  description promises, page size cap, LIKE escaping.
- `<Module>ModuleIntegrationTest` (`@IntegrationTest`, nothing else; `MockMvc`, `JdbcTemplate`) through the real
  HTTP layer: happy path with status and body, 400 with the `ErrorResponse` body, 401 without a token, 403/404 for
  other users, and the DB constraints of the migration. That test is what proves the generated mappings match the
  spec (path, method, content types, parameter names).
- `./mvnw -Dtest=ArchitectureTest test` stays green.

## 6. Frontend and docs
- The frontend calls the paths through `src/api/axiosClient.js` from a Zustand store; field names are exactly the
  spec's property names (generated models serialize them as written).
- Every change to an endpoint changes the spec's `summary`/`description` in the same commit; the spec is the API's
  documentation (there is no Swagger UI).
- A new endpoint of an existing module goes into the same spec file; regenerate, then implement the new method in
  the controller (the build fails until it is implemented, which is the point).
