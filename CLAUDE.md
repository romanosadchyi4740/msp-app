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
| Account deletion removes all personal data | Every new user-linked table needs cascade/cleanup and a deletion test | FR-2, NFR-6 |

Out of scope for the MVP (Won't): UI localization (the UI is English only; do not add i18n), mobile app, lesson recording, third-party tab catalogue, rooms > 5
(future SFU such as LiveKit/mediasoup), AI features.

## 2. Tech stack (as in the repo)

- **Backend** `backend/`: Java 25, Spring Boot 4.0.6, Maven wrapper. webmvc, websocket (STOMP + SockJS),
  security (stateless JWT, BCrypt, Google sign-in), data-jpa (Hibernate), Flyway, actuator, Lombok,
  MinIO client (S3-compatible). Base package `com.romanosadchyi.smp.music_app`.
- **DB**: PostgreSQL 15, Flyway migrations `V1..V16` exist; **new migrations start at `V17__`**.
- **Frontend** `frontend/`: React 19, Vite 8, Tailwind 4, Zustand 5, axios, react-router 7,
  `@stomp/stompjs` + `sockjs-client`, `@react-oauth/google`, react-hot-toast, lucide-react. Plain JS (no TS).
- **New libraries planned**: `@coderline/alphatab` (tab rendering, Guitar Pro parsing in the browser),
  Web Audio API (metronome), native WebRTC, Playwright (e2e), JaCoCo (coverage).
- **Infra target**: AWS `eu-central-1` via Terraform (EC2 + ALB, RDS PostgreSQL, S3, CloudFront,
  Secrets Manager, coturn on a small EC2), GitHub Actions with OIDC. Budget ≤ 55 USD/month.

## 3. Commands

```bash
docker-compose up -d                         # Postgres 15 + MinIO for local dev
cd backend && ./mvnw spring-boot:run         # API on :8080 (Flyway runs on start)
cd backend && ./mvnw test                    # all tests; MusicAppApplicationTests (@SpringBootTest) needs Postgres up
cd backend && ./mvnw test -Dtest='!MusicAppApplicationTests'   # unit tests only, no DB needed
cd backend && ./mvnw -Dtest=ChatServiceTest test
cd backend && ./mvnw -Dtest=ChatServiceTest#methodName test
cd frontend && npm install && npm run dev    # http://localhost:5173
cd frontend && npm run lint && npm run build # there is no frontend test runner yet
```

Local defaults: Postgres `postgres/postgres` db `music_app_db` on 5432; MinIO API :9000, console :9001
(`minio_user/minio_password`), bucket `music-files`. All backend config is in
`backend/src/main/resources/`: `application.yaml` (shared), `application-local.yaml` (default profile; env vars
with docker-compose defaults) and `application-prod.yaml` (`SPRING_PROFILES_ACTIVE=prod`; no defaults, fails to
start if a variable is missing). Frontend config comes from Vite env vars (`VITE_API_URL`, `VITE_WS_URL`,
`VITE_GOOGLE_CLIENT_ID`) read in `src/config.js`; dev defaults are in the committed `frontend/.env.development`,
and `frontend/.env.example` lists them for builds.

## 3a. How the existing msp-app pieces fit together

- **REST auth**: `JwtAuthenticationFilter` turns `Authorization: Bearer <jwt>` into an `Authentication` whose
  principal is the `User` entity (JWT subject = user UUID). Public/role rules are in `SecurityConfig`
  (`/auth/**` is public; note it is not under `/api`). The frontend keeps the token in `localStorage`;
  `axiosClient` attaches it and redirects to `/login` on 401.
- **STOMP auth** happens only on `CONNECT`, in the inline `ChannelInterceptor` in `WebSocketConfig`. It reads the
  `Authorization` native header and sets the session user. There is **no** per-destination authorization yet
  (the room `ChannelInterceptor` from section 6 has to be added). Endpoints are `/ws` (SockJS, used by the
  frontend) and `/ws-raw` (plain WebSocket, for Postman). The broker is the simple in-memory broker on
  `/topic` and `/queue`, with app prefix `/app` and user prefix `/user`.
- **Chat**: `MessageController` `@MessageMapping("/chat")` → `ChatService` persists the message (content is encrypted
  transparently by the JPA `MessageEncryptionConverter` → `EncryptionUtil`) and fans out with `convertAndSendToUser(..., "/queue/messages")` to each
  member. The `socketClient` singleton subscribes to `/user/queue/messages` in `onConnect`. Its
  `subscribe()` returns `null` when not connected, so callers must subscribe after connect.
- **Broadcast rooms (the existing sync feature)**: `BroadcastController` is a **stateless relay**
  (`/app/broadcast/{id}/sync` → `@SendTo /topic/broadcast/{id}`) with no role check. All logic is
  client-side in `components/AudioPlayer.jsx` + `store/playerStore.js`: the MASTER client publishes
  play/pause/seek/track events, and SYNC clients send `REQUEST_SYNC` that the MASTER answers. SyncChord rooms
  must **not** copy this pattern. They use the server-authoritative, versioned anchor-time state from
  section 6. Treat the broadcast code as a reference for the STOMP client plumbing only.
- **Files**: uploaded to MinIO through `MinioService`; clients get presigned GET URLs (1 h expiry).
- `DataSeeder` runs only when `data-seeder.enabled=true` (`@ConditionalOnProperty`).

## 4. Repository layout and conventions

Layered packages under `backend/src/main/java/com/romanosadchyi/smp/music_app/`:
`config/` (Security, WebSocket, MinIO, JWT filter, DataSeeder), `rest/` (REST controllers and STOMP
`@MessageMapping` handlers), `service/` (+ `service/mapping/*MappingService` for entity↔DTO),
`repository/` (Spring Data JPA), `model/entity`, `model/dto/request`, `model/dto/response`,
`model/enums`, `model/converter`, `exception/` (+ `GlobalExceptionHandler`), `util/`.
Put SyncChord code in the **same layers** (e.g. `service/RoomService`, `rest/RoomController`), not in a new
top-level module.

Follow the existing style:
- Entities: `@Entity @Table`, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)`, `@Builder.Default` for collections/defaults,
  `@Enumerated(EnumType.STRING)`, **UUID primary keys** (`GenerationType.UUID`), LAZY relations.
- Services/controllers: `@RequiredArgsConstructor @FieldDefaults(level = PRIVATE, makeFinal = true)`,
  `@Slf4j`, `@Transactional` on writes. DTOs are Java `record`s (`@Builder` where useful).
- Mapping is done in `*MappingService` beans, not in controllers.
- Domain errors are dedicated `RuntimeException`s mapped in `GlobalExceptionHandler`
  (404 not found, 403 `UserNotAuthorizedException`, 409 conflict, 400 `IllegalArgumentException`).
- Current user in REST: `Authentication` → `(User) authentication.getPrincipal()`; `User.getUsername()`
  returns the UUID string. Per-user STOMP delivery uses `convertAndSendToUser(userId.toString(), ...)`.
- Tests: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`), one test
  class per service/mapper under `backend/src/test/...`. Add tests for every new service.
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

Enums are stored as VARCHAR with CHECK constraints in SQL. All FKs indexed.

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

- `WebSocketConfig` logs the full `Authorization` header (JWT) at INFO. Remove it.
- Secrets have committed working defaults (JWT secret, AES key, DB/MinIO passwords, Google client id).
  Treat them as public. Non-local profiles must fail to start without real secrets.
- `EncryptionUtil`: AES-CBC with a fixed IV and silent decrypt fallback. Target AES-GCM, random 12-byte
  IV per message, versioned ciphertext prefix, data migration for existing `messages`.
- `setAllowedOriginPatterns("*")` on WebSocket endpoints; `/actuator/health` not public (ALB check fails).
- Simple in-memory STOMP broker → one backend instance only (fine for MVP; later RabbitMQ STOMP relay or
  Redis pub/sub).
- No Dockerfiles yet; `DataSeeder` mixes required genres with a demo artist.

## 8. Working agreements

- Branch per ticket, prefixed by purpose: `feature/KAN-<n>-short-slug` (new functionality),
  `bugfix/KAN-<n>-short-slug` (non-urgent fixes), `hotfix/KAN-<n>-short-slug` (urgent fixes to released code).
  Commit messages start with the key (`KAN-12: ...`).
- Small PRs; each backend change ships with unit tests; run `./mvnw test` and `npm run lint` before PRs.
- New schema only through new Flyway migrations (`V17__...`); never edit applied migrations.
- Never log tokens, SDP payloads, or message plaintext. Never commit secrets.
- Do not touch Terraform state or run `terraform apply`; the owner runs `plan/apply` with their credentials.
- When a requirement is unclear, check the requirement IDs referenced in the Jira ticket. Jira tickets are
  in English and use BR/UR/FR/NFR/OR; the Ukrainian lab reports use БВ/КВ/ФВ/НВ/ІВ for the same IDs
  (business, user, functional, non-functional, other requirements; e.g. FR-8 = ФВ-8). Risks are R1–R19 (Р1–Р19).
