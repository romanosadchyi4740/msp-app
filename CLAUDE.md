# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Project: SyncChord, built on msp-app.** The implementation plan is meant to live in
`docs/syncchord/IMPLEMENTATION_PLAN.md` (Ukrainian; **not committed yet**, so if it is missing, use the
Jira tickets). Work items are in Jira project **KAN** (`https://romanos4740.atlassian.net`). Reference every
change to a KAN key. `README.md` is a Ukrainian end-user/deployment manual for msp-app and has no
developer information beyond the commands below.

## 1. What we are building

**SyncChord** is a browser-only platform for live, remote guitar lessons and rehearsals. A host (teacher)
opens a room, picks a tab, sends an invite link; up to 4 participants join. Everyone sees **one tab canvas
that scrolls in sync** and hears **one shared metronome**, and they talk/play over **P2P WebRTC audio**
tuned for acoustic guitar. No installs, no audio interface.

It is the master's-course project (Lviv Polytechnic, "Innovative IT", labs 1–8). The labs define the
requirements, class/data model and processes summarised below. The code base is the owner's bachelor's
project **msp-app** (music social platform: tracks, playlists, encrypted chats, telemetry feed, and
"broadcast rooms" with MASTER/SYNC listening). SyncChord is added **on top of** it, reusing auth, users,
storage, encrypted messaging and the STOMP sync mechanism. Do not delete existing msp-app features unless
a ticket says so.

### Rules that shape the code (do not violate)

Only rules that change how code is written are listed here; the full requirement list lives in
`docs/syncchord/IMPLEMENTATION_PLAN.md` and the Jira tickets.

| Rule | What it means for the code | Source |
|---|---|---|
| Position/metronome drift between participants ≤ **150 ms** | Never stream positions or tick with `setInterval`; use the anchor-time model and Web Audio scheduling (section 6) | NFR-1 |
| Only the **HOST** sends scroll/tempo/metronome commands | Validate the sender's room role on the server for every command | FR-8 |
| Room capacity **5** (host + 4); closed rooms reject joins | Check capacity under a row lock; also caps the WebRTC mesh at 4 peers | FR-5 |
| Free plan lesson limit **40 min**; Premium unlimited | Server-side timer ends the lesson; the client only displays it | — |
| Late joiner / reconnect gets a full state snapshot | Every join path sends a snapshot; clients never assume they saw every update | FR-10 |
| Uploads only for material the user owns or public domain | `rightsConfirmed` must be true to create a tab | R14 |
| Account deletion removes all personal data | Every module that stores user-linked data implements `users.external.UserDataCleanup` (with a test); chat messages are the one exception, kept under the anonymized account (section 4) | FR-2, NFR-6 |

Out of scope for the MVP (Won't): UI localization (the UI is English only; do not add i18n), mobile app, lesson recording, third-party tab catalogue, rooms > 5
(future SFU such as LiveKit/mediasoup), AI features.

## 2. Tech stack (as in the repo)

- **Backend** `backend/`: Java 25, Spring Boot 4.1.1, Spring Modulith 2 (structure verification only),
  Maven wrapper. webmvc, websocket (STOMP + SockJS),
  security (stateless JWT, BCrypt, Google sign-in), data-jpa (Hibernate), Flyway, actuator, Lombok,
  MinIO client (S3-compatible). Base package `com.romanosadchyi.smp.music_app`.
  `openapi-generator-maven-plugin` (`spring` generator) for contract-first APIs: interfaces and models are
  generated during the build into `target/generated-sources/openapi` (never committed) from
  `src/main/resources/openapi/<module>.yaml`, one plugin execution per module.
- **DB**: PostgreSQL 15, Flyway migrations in `backend/src/main/resources/db/migration`; a new one takes the next free
  version number (tickets written early may name an older number such as `V17__`).
- **Frontend** `frontend/`: React 19, Vite 8, Tailwind 4, Zustand 5, axios, react-router 7,
  `@stomp/stompjs` + `sockjs-client`, `@react-oauth/google`, react-hot-toast, lucide-react. Plain JS (no TS).
- **New libraries planned**: `@coderline/alphatab` (tab rendering, Guitar Pro parsing in the browser),
  Web Audio API (metronome), native WebRTC, Playwright (e2e), JaCoCo (coverage).
- **Infra target**: AWS `eu-central-1` via Terraform (EC2 + ALB, RDS PostgreSQL, S3, CloudFront,
  Secrets Manager, coturn on a small EC2), GitHub Actions with OIDC. Budget ≤ 55 USD/month.

## 3. Commands

```bash
docker-compose up -d                         # Postgres 15 + MinIO for local dev
docker compose --profile full up -d --build  # whole app in Docker: frontend :5173, API :8080, + coturn :3478
docker compose --profile turn up -d          # infra + coturn only (WebRTC testing with apps run from source)
cd backend && ./mvnw spring-boot:run         # API on :8080 (Flyway runs on start)
cd backend && ./mvnw test                    # all tests; integration tests run on Testcontainers: only a Docker daemon needed
cd backend && ./mvnw test -DexcludedGroups=integration   # unit tests only, no Docker needed
cd backend && ./mvnw -Dtest=ArchitectureTest test   # module boundaries (Spring Modulith), no DB needed
cd backend && ./mvnw -Dtest=ChatServiceTest test
cd backend && ./mvnw -Dtest=ChatServiceTest#methodName test
cd frontend && npm install && npm run dev    # http://localhost:5173
cd frontend && npm run lint && npm run build # there is no frontend test runner yet
```

Local defaults: Postgres `postgres/postgres` db `music_app_db` on 5432; MinIO API :9000, console :9001
(`minio_user/minio_password`), bucket `music-files`. All backend config is in
`backend/src/main/resources/`: `application.yaml` (shared), `application-local.yaml` (default profile; env vars
with docker-compose defaults) and `application-prod.yaml` (`SPRING_PROFILES_ACTIVE=prod`; no defaults, fails to
start if a variable is missing). Outside the `local` and `test` profiles `shared.config.SecretsStartupCheck` also
refuses to start while a secret is missing or has a value committed to the repo; settings are bound to
`@Validated` `@ConfigurationProperties` records (`JwtProperties`, `EncryptionProperties`, `MinioProperties`).
Frontend config comes from Vite env vars (`VITE_API_URL`, `VITE_WS_URL`, `VITE_GOOGLE_CLIENT_ID`) read in
`src/config.js`; dev defaults are in the committed `frontend/.env.development`,
and `frontend/.env.example` lists them for builds.

## 3a. How the existing msp-app pieces fit together

- **REST auth**: `JwtAuthenticationFilter` turns `Authorization: Bearer <jwt>` into an `Authentication` whose
  principal is `shared.security.AuthenticatedUser` (id = JWT subject, email, role), built from the claims
  without loading the `User` entity. The role is a claim, so `POST /api/users/me/become-artist` returns a new
  token in the `Authorization` response header. Public/role rules are in `SecurityConfig`
  (`/auth/**` is public; note it is not under `/api`). The frontend keeps the token in `localStorage`;
  `axiosClient` attaches it and redirects to `/login` on 401.
- **STOMP auth** happens only on `CONNECT`, in `JwtStompChannelInterceptor` (registered in `WebSocketConfig`). It
  reads the `Authorization` native header and sets an `AuthenticatedUser` as the session user. There is **no** per-destination authorization yet
  (the room `ChannelInterceptor` from section 6 has to be added). Endpoints are `/ws` (SockJS, used by the
  frontend) and `/ws-raw` (plain WebSocket, for Postman). The broker is the simple in-memory broker on
  `/topic` and `/queue`, with app prefix `/app` and user prefix `/user`.
- **Chat** (module `chats`): `MessageController` `@MessageMapping("/chat")` → `ChatMessageService` persists the message
  (content is encrypted transparently by the JPA `MessageEncryptionConverter` → `EncryptionUtil`) and
  `ChatMessageNotifier` fans out with `convertAndSendToUser(..., "/queue/messages")` to each member. The `socketClient` singleton subscribes to `/user/queue/messages` in `onConnect`. Its
  `subscribe()` returns `null` when not connected, so callers must subscribe after connect.
- **Broadcast rooms (the existing sync feature)**: `BroadcastController` is a **stateless relay**
  (`/app/broadcast/{id}/sync` → `@SendTo /topic/broadcast/{id}`) with no role check. All logic is
  client-side in `components/AudioPlayer.jsx` + `store/playerStore.js`: the MASTER client publishes
  play/pause/seek/track events, and SYNC clients send `REQUEST_SYNC` that the MASTER answers. SyncChord rooms
  must **not** copy this pattern. They use the server-authoritative, versioned anchor-time state from
  section 6. Treat the broadcast code as a reference for the STOMP client plumbing only.
- **Files**: stored through `storage.external.FileStorage` (implemented by `storage.internal.MinioFileStorage`;
  the MinIO clients are package-private to `storage`); clients get presigned GET URLs (1 h expiry).
- Seeders: `music.internal.config.ReferenceDataSeeder` (genres) runs in every profile; `users.internal.config.DemoDataSeeder`
  (`artist@example.com`) only in `local` and only when `data-seeder.enabled=true` (`DATA_SEEDER_ENABLED`). Never add demo data to the reference seeder.
- `GET /actuator/health` is public and returns only the overall status (ALB health check); no other actuator
  endpoints are exposed.

## 4. Modular monolith (Spring Modulith 2) and repository layout

The backend is a **modular monolith** verified by Spring Modulith 2. Modules call each other
**synchronously** through narrow Java interfaces, like ordinary method calls. No application events,
no outbox, no broker between modules; one shared PostgreSQL database. The goal is structure and
enforced boundaries, not distribution. Restructuring the existing msp-app code into modules is epic
KAN-64 and is done **before** any new feature work.

Every top-level package under `com.romanosadchyi.smp.music_app` is a module:

```
music_app/
  shared/                 open module: what every module needs
    package-info.java     @ApplicationModule(type = ApplicationModule.Type.OPEN)
    config/               SecurityConfig, WebSocketConfig, JWT filter and STOMP interceptor, HTTP clients
    security/             JwtService, AuthenticatedUser (principal built from JWT claims)
    exception/            GlobalExceptionHandler, base exception types
    util/, constants/     EncryptionUtil, Constants, small helpers
  users/                  User, profile, follow, auth (register/login/Google), account deletion
    external/             package-info.java with @NamedInterface("external") + UserLookup interface (+ its DTOs)
    internal/             entities, repositories, services, controllers, mappers, implementation of UserLookup
  storage/                MinIO/S3 file storage           external: FileStorage
  music/                  tracks, albums, genres, likes    external: TrackLookup
  playlists/              playlists, forks, integration requests
  chats/                  chats, memberships, messages, listening broadcast (MASTER/SYNC)
  telemetry/              status broadcasts, polls
  search/                 global search over other modules' external interfaces
  tabs/ rooms/ sync/ rtc/ billing/ moderation/     new SyncChord modules, same shape
```

Module rules (enforced by `ArchitectureTest`: `verifyModularity()` → `ApplicationModules.of(MusicAppApplication.class).verify()`
for named interfaces and cycles, `modulesAreTheKnownOnes` for the module list (a new module is added there),
`moduleHasOnlyInternalAndExternalPackages`, `moduleUsesOnlyItsAllowedModules` for which way dependencies point,
and `sharedDependsOnNoFeatureCode`). Since KAN-71 no legacy layered
packages are left; all code lives in a module:
- A module has at most two subpackages: `internal` (everything, exposes nothing) and, if it offers anything,
  `external` (only the interface(s) the module offers to others, plus the records used in their signatures).
  `external/package-info.java` carries `@NamedInterface("external")`.
- The implementation of an external interface lives in `internal` (package-private where possible).
- Other modules depend only on `<module>.external` and on `shared`. Never import another module's
  `internal` types: entities, repositories, services, mappers.
- **Cross-module references are UUIDs, not JPA associations.** An entity never has `@ManyToOne`/
  `@ManyToMany` to another module's entity; it stores `UUID authorId` on the same column. Join tables
  that link two modules (`user_liked_tracks`, `playlist_tracks`) are owned by one module and mapped as
  an entity or `@ElementCollection` of UUIDs. Column names stay the same, so no data migration is needed.
- **No cycles.** When a flow naturally points the other way, invert it with an interface:
  `users.external.UserDataCleanup` is implemented by each module that stores user data, and `users`
  calls all beans of that type on account deletion; `rooms` asks `billing.external.PremiumChecker`.
  The same goes for references to another module's rows: deleting a track calls every
  `music.external.TrackDeletionCleanup` (playlists and chat messages drop the track), so `music` never touches their
  tables. Account deletion anonymizes the `users` row instead of deleting it and sets `users.deleted_at`, so
  `ON DELETE CASCADE` to `users` never fires: each `UserDataCleanup` deletes its rows itself. Other modules ask
  `UserLookup.findDeletedIds` which users are deleted, and `UserLookup.exists` is false for them, so a deleted
  account can neither act nor be addressed; its profile and follow endpoints answer 404. Exception by owner decision:
  chat messages stay, shown under the anonymized account; the user leaves group chats but keeps direct-chat
  memberships, and a direct chat the other member never accepted, or a chat whose remaining members all have
  deleted accounts, is deleted with its messages (`ChatsUserDataCleanup`).
- Responses that list many rows resolve users in one batched call (`UserLookup.findSummaries`, or
  `UserLookup.findProfiles` where a row embeds the full profile, used by the `mapAll`/`mapPage` methods of the
  mappers), never one lookup per row.
- Other modules read users through `users.external.UserLookup`: `UserSummary` (id, display name, avatar, role)
  next to their own data, `UserResponseDto` where a response embeds the full public profile.
- The current user comes from `shared.security.AuthenticatedUser` (id, email, role from the JWT), not
  from the `User` entity, so `shared` never depends on `users`.
- The hot sync path (host command → state broadcast) stays a direct call inside `sync`.
- Seeders live in the module whose data they create. Demo data is split the same way: each module seeds
  its own part behind `data-seeder.enabled`, and `shared` never depends on a feature module.
- Inside a module, keep the familiar layering in `internal` (controller, service, repository,
  entity, dto, mapper subpackages as the module grows).
- Optional: `Documenter` can render module diagrams into `docs/syncchord/modules/`.

Follow the existing style:
- Java formatting: refer to types through imports, never fully qualified names in code or Javadoc, unless two
  types with the same simple name clash in one file. A file ends with the last `}` followed by exactly one
  newline, and there is no blank line before that last `}`. Fields get their modifiers from Lombok
  `@FieldDefaults` (entities `level = PRIVATE`, beans also `makeFinal = true`); do not write `private`/`final`
  on fields. Test classes keep fields package-private, as in the existing tests.
- Entities: `@Entity @Table`, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)`, `@Builder.Default` for collections/defaults,
  `@Enumerated(EnumType.STRING)`, **UUID primary keys** (`GenerationType.UUID`), LAZY relations.
  Entities hold data only: no methods besides the Lombok-generated ones. Logic goes into services, or into a
  small helper class next to them (e.g. `ChatMemberships`).
- Services/controllers: `@RequiredArgsConstructor @FieldDefaults(level = PRIVATE, makeFinal = true)`,
  `@Slf4j`, `@Transactional` on writes. DTOs are Java `record`s (`@Builder` where useful), except the API
  models of contract-first modules, which are generated.
- **New REST APIs are contract-first** (KAN-91; `tabs` is the first): write or extend
  `src/main/resources/openapi/<module>.yaml`, add a plugin execution generating into `<module>.internal.api` and
  `<module>.internal.api.model` (never a shared package), and let the controller implement the generated
  interface, which holds the mappings. Existing msp-app controllers stay hand-written. Specs carry no validation
  constraints (`useBeanValidation=false`): input is checked in the services, so errors keep the
  `GlobalExceptionHandler` body. Paginated responses use an explicit page schema, not `Page<T>`.
- Mapping is done in `*MappingService` beans, not in controllers.
- Domain errors are dedicated `RuntimeException`s mapped in `GlobalExceptionHandler`: extend
  `shared.exception.NotFoundException` (404) or `ConflictException` (409); 403 is `UserNotAuthorizedException`,
  400 `IllegalArgumentException`. Do not add per-exception handlers.
- Current user in REST: `@AuthenticationPrincipal AuthenticatedUser user`; a controller implementing a generated
  interface cannot add that parameter and calls `shared.security.CurrentUser.get()` instead (STOMP handlers: cast
  `authentication.getPrincipal()` to it); never the `User` entity. Its `getName()` is the user UUID, so per-user
  STOMP delivery uses `convertAndSendToUser(userId.toString(), ...)`.
- Tests: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`), one test
  class per service/mapper, mirroring the module packages. Add tests for every new service.
  `ArchitectureTest` must stay green.
  Integration tests are annotated `@IntegrationTest` (full context, `test` profile, Postgres + S3Mock
  Testcontainers shared per run; never the local docker-compose services) and use no other test annotations,
  so they share one cached context.
- Frontend: pages in `src/pages`, components in `src/components/<area>`, global state in Zustand stores
  in `src/store`, HTTP through `src/api/axiosClient.js`, STOMP through `src/api/socketClient.js`.
- The msp-app UI uses sci-fi wording (Nodes, Clusters, Transmissions); SyncChord screens use plain
  musical wording (room, host, participant, tab, measure, beat).

### Deliberate deviations from the lab models
The lab class model (lab 7) uses `Long`/BIGSERIAL ids, `Instant`, and `USER/ADMIN` roles. In code:
- ids are **UUID** (repo convention); new tables use `TIMESTAMPTZ` and `Instant` in Java (sync math is UTC);
- user roles stay `ROLE_USER / ROLE_ARTIST / ROLE_ADMIN`; HOST/PARTICIPANT is a **room** role;
- lab `Session` → entity `LessonSession`, table `lesson_sessions` (avoids clashing with Hibernate `Session`);
- lab `ChatMessage` → entity `RoomMessage`, table `room_messages` (the msp-app already has `messages`
  and `ChatMessage*Dto`).

## 5. Target domain model (lab 7)

| Entity (table) | Key fields | Notes |
|---|---|---|
| `User` (`users`) | existing | reused unchanged |
| `Subscription` (`subscriptions`) | user, plan FREE/PREMIUM, status ACTIVE/CANCELLED/EXPIRED, startedAt, expiresAt | `activate`, `cancel`, `isActive` |
| `Payment` (`payments`) | subscription (composition, cascade), amount `BigDecimal`, currency, status PENDING/PAID/FAILED/REFUNDED, providerTxId (unique) | confirmed only by verified webhook |
| `Tab` (`tabs`, SINGLE_TABLE, discriminator `format`) | owner, title, artist, fileKey (S3), tempoBpm, timeSignature, isPublic, rightsConfirmed, hidden | subclasses `GuitarProTab(gpVersion)`, `AsciiTab(rawText)`; index (title, artist) |
| `Complaint` (`complaints`) | reporter, tab, reason, status NEW/ACCEPTED/REJECTED, resolvedBy | ACCEPTED hides the tab |
| `Room` (`rooms`) | host, name, inviteCode (unique), capacity=5, status OPEN/CLOSED | `create`, `generateInvite`, `join`, `transferHost`, `close` |
| `RoomParticipant` (`room_participants`) | room (cascade), user, role HOST/PARTICIPANT, joinedAt, rttMs, online | unique (room, user); analogue of `ChatMembership` |
| `LessonSession` (`lesson_sessions`) | room (cascade), tab (nullable), startedAt, endedAt, limitMinutes | `start`, `selectTab`, `end`, `isExpired` |
| `PlaybackState` (`playback_states`, PK = session_id) | measure, beat, tempoBpm, status PLAYING/PAUSED/STOPPED, anchorTime (server epoch ms), metronomeOn, `@Version version` | `apply`, `positionAt`, `snapshot` |
| `RoomMessage` (`room_messages`) | room (cascade), author participant, content (encrypted), sentAt | index (room_id, sent_at) |
| `SyncCommand` (transient DTO) | type PLAY/PAUSE/SEEK/SET_TEMPO/METRONOME/LOAD_TAB/REQUEST_SYNC, argument, senderId, clientTime | never stored |
| `SignalMessage` (transient DTO) | type OFFER/ANSWER/ICE_CANDIDATE, fromId, toId, payload (SDP/ICE) | relayed only |

Enums are stored as VARCHAR with CHECK constraints in SQL. All FKs indexed. The operations listed with the
entities (`create`, `join`, `apply`, `positionAt`, ...) are the lab model's; in code they are service methods,
because entities hold data only (section 4).

## 6. Real-time protocol (the innovative core)

**Sync model (anchor time, not streaming positions).** The server never streams the current measure.
It stores `{measure, beat, tempoBpm, status, anchorTime, version}`; every client computes:

```
beatsElapsed = (serverNow - anchorTime) * tempoBpm / 60000      (only if status == PLAYING)
position     = (measure, beat) + beatsElapsed   (wrap beats by the time signature)
serverNow    = Date.now() + clockOffset
```

- Host command → server checks HOST role → `PlaybackState.apply(cmd)` sets a new `anchorTime`
  (= server receive time), updates measure/beat/tempo/status, increments `version`, saves, broadcasts
  the full state.
- Clients drop any state whose `version` ≤ the last applied one.
- Clock offset: NTP-style ping over STOMP, several samples, keep the one with the smallest RTT:
  `offset = serverTime - (t0 + t1) / 2`. Re-sample periodically.
- If a client detects drift > 150 ms (e.g. after tab freeze or tab switch), it sends `REQUEST_SYNC` and
  gets a snapshot. Joiners and reconnects always get a snapshot.
- Metronome: Web Audio API lookahead scheduler (schedule clicks ~100 ms ahead on `AudioContext.currentTime`,
  derived from the same anchor), count-in before PLAY. Never use `setInterval` for clicks.

**STOMP destinations (proposed; msp-app keeps `/app/chat` and `/app/broadcast/{id}/sync`)**

| Direction | Destination | Payload |
|---|---|---|
| client → server | `/app/rooms/{roomId}/command` | `SyncCommand` |
| server → room | `/topic/rooms/{roomId}/state` | `PlaybackStateDto` (with `serverTime`, `version`) |
| server → room | `/topic/rooms/{roomId}/participants` | join/leave/online/host-changed/rtt events |
| client → server | `/app/rooms/{roomId}/signal` | `SignalMessage` (server sets `fromId` from the principal) |
| server → user | `/user/queue/signal` | `SignalMessage` |
| client → server / user | `/app/time` → `/user/queue/time` | `{clientTime}` → `{clientTime, serverTime}` |
| server → user | `/user/queue/rooms/{roomId}/snapshot` | snapshot on join / `REQUEST_SYNC` |
| room chat | `/app/rooms/{roomId}/chat` → `/topic/rooms/{roomId}/chat` | `RoomMessage` DTO |

A `ChannelInterceptor` must reject `SUBSCRIBE` to `/topic/rooms/{id}/**` and `SEND` to `/app/rooms/{id}/**`
unless the principal is a current participant of that room.

**WebRTC audio.** Mesh: one `RTCPeerConnection` per peer (≤ 4). `getUserMedia({audio: {echoCancellation:false,
noiseSuppression:false, autoGainControl:false, channelCount: 2}})`; Opus with music-friendly SDP params
(stereo, higher `maxaveragebitrate`, low ptime). ICE servers come from `GET /api/rtc/ice-servers`, which
returns STUN plus short-lived TURN credentials generated with the coturn `use-auth-secret` scheme
(HMAC-SHA1 over `expiry:userId`). Relay-only mode sets `iceTransportPolicy: 'relay'`. Use the
"perfect negotiation" pattern to avoid glare. RTT/jitter/loss from `getStats()` feed the quality indicator
and `RoomParticipant.rttMs`.

## 7. Known issues in the base project (fix before production; see Jira epic KAN-5)

- The local and test secrets are committed and public; they are only accepted in those profiles (KAN-20).
- `EncryptionUtil`: AES-CBC with a fixed IV and silent decrypt fallback. Target AES-GCM, random 12-byte
  IV per message, versioned ciphertext prefix, data migration for existing `messages` (KAN-21, postponed).
- `setAllowedOriginPatterns("*")` on WebSocket endpoints.
- Simple in-memory STOMP broker → one backend instance only (fine for MVP; later RabbitMQ STOMP relay or
  Redis pub/sub).

## 8. Working agreements

- Branch per ticket, prefixed by purpose: `feature/KAN-<n>-short-slug` (new functionality),
  `bugfix/KAN-<n>-short-slug` (non-urgent fixes), `hotfix/KAN-<n>-short-slug` (urgent fixes to released code).
  Commit messages start with the key (`KAN-12: ...`).
- `development` is the integration branch: branch off it and open PRs against it (`gh pr create --base development`),
  not `main`. The owner reviews and merges PRs. Move the Jira ticket along with the work: In Progress when
  starting, In Review when the PR is opened, Done after the merge.
- Small PRs; each backend change ships with unit tests; run `./mvnw test` and `npm run lint` before PRs.
- Project skills in `.claude/skills/`: `kan-ticket` (ticket, branch, PR and Jira workflow), `modulith-extract`
  (extracting a module), `split-service` (splitting an oversized service). Their Java helpers are in
  `.claude/scripts/java/` (`java_style.py --check` checks the mechanical style rules of section 4).
- Project agents in `.claude/agents/`: `code-reviewer` (read-only review of the branch against these rules; run it
  before opening or updating a PR) and `test-gap-finder` (lists untested branches; writes tests under
  `backend/src/test` only when asked).
- New schema only through new Flyway migrations (next free version number); never edit applied migrations.
- Never log tokens, SDP payloads, or message plaintext. Never commit secrets.
- Do not touch Terraform state or run `terraform apply`; the owner runs `plan/apply` with their credentials.
- When a requirement is unclear, check the requirement IDs referenced in the Jira ticket. Jira tickets are
  in English and use BR/UR/FR/NFR/OR; the Ukrainian lab reports use БВ/КВ/ФВ/НВ/ІВ for the same IDs
  (business, user, functional, non-functional, other requirements; e.g. FR-8 = ФВ-8). Risks are R1–R19 (Р1–Р19).
