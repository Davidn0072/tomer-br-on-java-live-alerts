# PLAN — Live Alerts over TCP and WebSocket

Work plan for the 3-day exercise described in `Developer_Exercise.md`. No code is written yet;
this document defines scope, components, and dependencies for five areas: **Emulator**,
**Server**, **Client**, **Tests**, **Infrastructure**.

## 1. System overview

```
[Emulator] --TCP (JSON, framed)--> [Java Server] --T-SQL--> [MSSQL]
                                         |
                                         |--WebSocket (alert push)--> [React Client]
                                         |
                                         |<--REST (GET messages)------|
```

Four containers total: `mssql`, `server`, `emulator`, `client`. The client is reached at
`http://localhost`; the server exposes TCP, WebSocket, and REST ports internally (and to the
host for debugging).

### 1.1 Protocol shape (to be finalized in README, not here)

TCP messages are JSON objects, newline-delimited (`\n` framing — simplest to implement/debug
with telnet/netcat, and sufficient since JSON payloads won't contain raw newlines). Minimum
message types:

- `Connect` — client→server handshake, includes a client/device identifier.
- `Disconnect` — graceful client→server notice before closing the socket.
- `SendMessage` — client→server payload to persist (message body + metadata).
- Server→client acknowledgements/errors (e.g. `Ack`, `Error`) for malformed input, so the
  emulator can log failures without crashing.

Exact field names, an `type` discriminator convention, and versioning are decided during Server
implementation and documented in README, not fixed here.

### 1.2 Data flow contract between areas

1. Emulator opens TCP connection → sends `Connect` → sends `SendMessage` (manual or on timer).
2. Server parses/validates frame → persists to MSSQL → broadcasts a lightweight alert
   (`{ type: "NewMessage", id, timestamp }`, no full payload) to all connected WebSocket
   clients.
3. Client receives WS alert → calls REST `GET /api/messages` (or `/api/messages/{id}`) →
   renders the alert + updates the stored-messages list.
4. On server restart: emulator's TCP client detects the dropped socket and reconnects with
   backoff; client's WS layer does the same. Both must resume without manual steps — this is a
   cross-cutting requirement touching Emulator, Server, and Client design.

---

## 2. Emulator — DONE (implementation; README still pending, see §7)

A standalone TCP client (Java, matching the server's runtime for protocol/DTO reuse, e.g. via
a small shared module or duplicated POJOs — decide during implementation).

**Decided: duplicated POJOs**, not a shared Maven module — `emulator/src/main/java/com/livealerts/emulator/protocol/`
is a byte-for-byte copy of the server's protocol package (minus the one `@Component` annotation,
since the emulator has no Spring context). A shared module would complicate each side's
independent Docker build for little benefit at five small record classes.

**Decided: manual trigger = a minimal HTTP control server**, using only the JDK's built-in
`com.sun.net.httpserver.HttpServer` (no framework dependency): `POST /trigger` sends one
`SendMessage` immediately, `GET /health` for readiness. Port via `EMULATOR_CONTROL_PORT`
(default 9000, published in `docker-compose.yml`) — this is what Playwright E2E scenario 1 will
call.

No Spring Boot here (unlike the Server) — a TCP client with a scheduler and a two-endpoint HTTP
control surface doesn't need it; plain Java + Jackson keeps the image small and the startup
instant.

Verified end-to-end against the real `server` + `mssql` containers:
- Connects on startup, sends `Connect`, receives `Ack`.
- `EMULATOR_INTERVAL_MS` periodic sends and `POST /trigger` manual sends both land in MSSQL and
  are visible via `GET /api/messages`.
- **Restart recovery, the exercise's core robustness requirement:** `docker restart
  live-alerts-server` while the emulator is running — it logs `Connection refused` every 3s,
  drops (logs, doesn't queue) any send attempted while down, and reconnects with a fresh
  `Connect` the moment the server is reachable again, resuming periodic sends automatically.
  Zero manual steps, exactly matching E2E scenario 2 in `Developer_Exercise.md`.

**Components:**
- **Connection manager** — opens/holds the TCP socket to the server, detects disconnects, and
  reconnects with backoff (exponential or fixed-interval retry) so it recovers automatically
  after a server restart.
- **Framing/codec** — same newline-delimited JSON (de)serialization logic the server uses, kept
  consistent between the two.
- **Protocol client** — sends `Connect` on startup/reconnect, `SendMessage` on trigger, and
  `Disconnect` on shutdown (SIGTERM handling for clean container stop).
- **Message trigger sources:**
  - Manual trigger — an interface to send one message on demand (e.g. a minimal HTTP/CLI
    control endpoint inside the emulator container, or a file/stdin-driven trigger — pick one
    that's testable from Playwright/E2E without extra tooling).
  - Scheduled trigger — periodic send on a configurable interval (env var, e.g.
    `EMULATOR_INTERVAL_MS`), using a simple scheduler (`ScheduledExecutorService` or similar).
- **Config** — server host/port, interval, and identity, all via environment variables (for
  docker-compose overrides).
- **Logging** — structured console logs for sent messages, acks, errors, and reconnect events
  (useful for debugging E2E test 2 — restart/reconnect).

**Dependencies:**
- Needs the **Server**'s TCP protocol/port defined first (or developed in lockstep with it).
- Needs **Infrastructure**'s docker-compose network/service name for the server host.
- Feeds **Tests** (E2E scenario 1 needs a way to trigger a send deterministically; scenario 2
  needs the emulator to survive/reconnect after server restart).

---

## 3. Server — DONE (implementation; README still pending, see §7)

Java application (Spring Boot is the natural fit for REST + WebSocket + MSSQL integration, but
confirm version/build tool — Maven vs Gradle — before scaffolding).

Verified end-to-end via a real `docker compose up -d mssql server` run (not just unit tests):
raw TCP `Connect`/`SendMessage` → `Ack` replies → row in MSSQL → visible via `GET
/api/messages` → a WebSocket client connected to `/ws/alerts` receives
`{"type":"NewMessage","id":...,"receivedAt":...}` the moment the message is persisted. That run
surfaced two bugs unit tests couldn't catch (both fixed, see `server/src/main/java/com/livealerts/server/`):
1. **The MSSQL image doesn't auto-create an app database** the way Postgres's `POSTGRES_DB`
   does — only `master` exists on first start. Fixed with `EnsureDatabaseExistsListener`, an
   `ApplicationListener<ApplicationEnvironmentPreparedEvent>` that creates the database (if
   missing) before Spring's JPA datasource connects.
2. **`MessageCodec` was never `@Component`-annotated**, so `TcpServer` (which Spring
   constructor-injects it into) failed to start under a real `SpringApplication.run()`. Every
   test up to that point either `new MessageCodec()`'d it directly or used a Spring test slice
   that never scanned the `tcp` package, so nothing caught it until the full app actually booted.

**Lesson for this project:** unit tests and slice tests (`@WebMvcTest`, `@DataJpaTest`) don't
prove the full Spring context wires together — run the real container at least once after
finishing a component.

**Components:**
- **TCP server** — listens on a configurable port, accepts multiple concurrent client
  connections (thread-per-connection or NIO), reads framed JSON, dispatches by message `type`.
  Must not crash or hang the listener on malformed JSON/abrupt disconnects — isolate per-
  connection failures.
- **Protocol handler / message router** — validates and interprets `Connect`, `Disconnect`,
  `SendMessage`; sends back `Ack`/`Error` responses.
- **Persistence layer** — MSSQL access (Spring Data JPA/Hibernate or plain JDBC — decide during
  implementation) for storing messages; schema auto-created on first start (`ddl-auto=update`
  or a migration tool like Flyway/Liquibase — prefer a migration tool for reliability across
  restarts).
- **WebSocket server** — endpoint the React client connects to; broadcasts an alert to all open
  sessions when a new message is persisted; must accept reconnects at any time (no lost state
  required client-side — REST is the source of truth for data, WS is just a nudge).
- **REST API** — read-only endpoints for the client:
  - `GET /api/messages` — list stored messages (pagination/limit to decide).
  - `GET /api/messages/{id}` — single message (used after a WS alert to fetch the new one).
  - `GET /api/health` — for container/E2E readiness checks (also useful for docker-compose
    healthchecks).
- **Resilience/robustness layer** — connection-level try/catch around per-message parsing so
  one bad client doesn't affect others; graceful handling of abrupt TCP disconnects (socket
  exceptions treated as normal disconnects, not crashes).
- **Startup/schema init** — runs schema creation/migration against MSSQL on boot, with retry/
  wait logic since MSSQL may not be ready yet when the server container starts (important for
  `docker compose up` cold start).
- **Config** — TCP port, WS/REST port(s), DB connection string, all via environment variables.

**Dependencies:**
- Needs **Infrastructure**'s MSSQL container (connection details, startup ordering/healthcheck)
  before persistence work can be integration-tested.
- Defines the contract that **Emulator** and **Client** both implement against — should be
  designed/documented early (even if implementation is iterative) so the other two areas aren't
  blocked.
- Feeds **Tests**: unit tests for protocol parsing/handlers, integration tests against a real
  (or test-container) MSSQL instance.

---

## 4. Client — DONE (implementation; README still pending, see §7)

React web application, **Vite + TypeScript**. **WS client: native browser `WebSocket`**, not a
library — the server is a plain Spring `TextWebSocketHandler`, not Socket.IO/STOMP, so a raw
client is the correct match, not an extra dependency.

**Decided: no build-time/runtime API base URL at all.** The client calls relative paths
(`/api/messages`, `/ws/alerts`) exclusively. In docker-compose, nginx (serving the built static
files) reverse-proxies `/api/*` and `/ws/*` to the `server` service — see §6 and
`client/nginx.conf`. In local `npm run dev`, Vite's own dev-server proxy does the same against
`http://localhost:8080` (`client/vite.config.ts`). Same-origin either way: no CORS
configuration needed anywhere, and this also resolves §8's "reverse proxy vs. direct port"
question for the whole system, not just the client.

**Components (all implemented, `client/src/`):**
- `hooks/useAlertSocket.ts` — connects on mount, exposes `connecting | open | reconnecting`
  status, reconnects on a fixed 3s delay after any drop (mirrors the emulator's own cadence) —
  no manual browser refresh needed after a server restart.
- `api/messages.ts` — `fetchMessages()` / `fetchMessageById(id)`.
- `components/AlertToast.tsx` — shown when a WS alert arrives (after fetching the full message
  via REST — the WS payload itself carries no body); auto-dismisses after 6s or on click.
- `components/MessageList.tsx` — renders `clientId` / `text` / `receivedAt`, newest first.
- `components/ConnectionStatus.tsx` — the "nice-to-have" indicator from the original plan;
  built after all, since it doubles as a way to see reconnect state during manual testing.
- `App.tsx` — wires the above: loads the initial list via REST, and on each WS alert fetches
  that one message and prepends it (no full-list refetch).

Verified end-to-end through the real `docker compose` stack (all four containers, through
nginx at `http://localhost`, not just against the server directly): static assets serve,
`GET /api/messages` proxies correctly, and a raw WebSocket client connecting to
`ws://localhost/ws/alerts` receives the live alert the moment the emulator's message lands —
confirming the reverse-proxy config work for both HTTP and the WS upgrade. Not yet verified in
an actual browser (no browser-automation tool was available in this session) — Playwright E2E
(§5) will be the first real-browser check.

**Dependencies:**
- Needs the **Server**'s REST/WS contracts defined (can start against a mocked/stubbed server
  or an early skeleton).
- Needs **Infrastructure** to decide how the client is served and how `http://localhost`
  reaches both static assets and the server's API/WS (reverse proxy vs. separate ports vs. the
  client container proxying `/api` and `/ws` to the server).
- Feeds **Tests**: Vitest unit tests for components/hooks; the app itself is the target of the
  Playwright E2E suite.

---

## 5. Tests — DONE

Three suites, each owned by its respective stack but planned together here for coverage and
sequencing.

**Java (JUnit):** 27 tests in `server/` (protocol codec, TCP server over real sockets incl.
robustness, REST controllers, WS broadcaster, persistence wiring) + the required MSSQL
integration test (`MessageRepositoryIT`, Testcontainers, run via `mvn verify`); 19 tests in
`emulator/` (protocol codec, `ConnectionManager` reconnect behavior, `ControlServer` HTTP). See
§3/§2 for what each covers.

**React (Vitest):** 14 tests in `client/` — the REST client, `useAlertSocket`'s connect/message/
reconnect/cleanup behavior against a fake WebSocket, `AlertToast`, `MessageList`. See §4.

**E2E (Playwright, `e2e/`):** both required scenarios, run against the real four-container
`docker-compose` stack (not mocks) and **passing**:
- `alert-flow.spec.ts` — triggers the emulator's manual send, asserts the alert toast appears
  and the message list grows.
- `restart-recovery.spec.ts` — asserts the connection indicator is "Live", restarts the `server`
  container (`docker compose restart server`), asserts the indicator goes to "Reconnecting…"
  and back to "Live" with no page action, then triggers another send and asserts the alert still
  arrives. ~2 minutes total (real Spring Boot/MSSQL restart, not simulated).

**Found and fixed running these for real, not caught by any unit/component test:**
- `workers: 1` in `playwright.config.ts` is load-bearing: the two specs share one live stack,
  and running them in parallel workers meant `restart-recovery`'s server restart broke
  `alert-flow`'s WebSocket connection mid-test.
- All three Dockerfiles' `HEALTHCHECK` used `http://localhost/...`; in the `nginx:1.27-alpine`
  image specifically, `localhost` resolved to `::1` while nginx only listened on IPv4, so
  `wget` got "connection refused" every time and the `client` container was permanently
  unhealthy under `docker compose up --wait`. Fixed by using `127.0.0.1` explicitly in all
  three (`server`, `emulator`, `client`) Dockerfiles, even though only `client` was actually
  broken — no reason to leave the same footgun in the other two.

**Dependencies:**
- Java unit tests can start as soon as protocol/parsing code exists in the **Server**.
- The MSSQL integration test needs **Infrastructure**'s MSSQL setup (or Testcontainers, which
  needs Docker available in the test environment).
- Vitest tests need **Client** components to exist.
- Playwright E2E needs all four containers runnable together, i.e. depends on
  **Infrastructure** (`docker compose up`) plus working **Emulator**, **Server**, and **Client**
  restart/reconnect behavior — this suite is necessarily last in the build order.

---

## 6. Infrastructure

Docker Compose orchestration and supporting config; ties the other four areas together into a
single `docker compose up`.

**Components:**
- **`docker-compose.yml`** — defines four services:
  - `mssql` — official MSSQL Linux image, with a healthcheck (so the server can wait for it).
  - `server` — Java server, built from a Dockerfile, depends on `mssql` (with
    `depends_on: condition: service_healthy` or app-level retry), exposes TCP/REST/WS ports.
  - `emulator` — TCP client, depends on `server`, configurable interval via env var.
  - `client` — React app, built and served (e.g. via Nginx) or served/proxied so the whole app
    is reachable at `http://localhost`; proxies `/api` and the WS path to `server`.
- **Dockerfiles** — one per Java service (multi-stage build: Maven/Gradle build stage + slim
  JRE runtime stage) and one for the client (Node build stage + Nginx/static serve stage).
- **Networking** — a shared docker-compose network so services address each other by service
  name; only the client (and optionally server, for debugging) ports are published to the host.
- **Reverse proxy strategy** — decide whether Nginx (in the client container) proxies REST/WS
  calls to `server`, or whether the server itself serves on a host-exposed port referenced
  directly by the client — needs to satisfy "reached at `http://localhost`" with no manual
  config for the end user.
- **Environment/config files** — `.env` or compose-level environment blocks for DB credentials,
  ports, emulator interval, etc. Avoid committing real secrets (use a local-only default
  password acceptable for a take-home exercise, documented in README).
- **Startup ordering & resilience** — healthchecks and restart policies so `docker compose up`
  from a clean clone works deterministically, and so a manual `docker compose restart server`
  (used to validate reconnect behavior) doesn't require re-running the whole stack.
- **Repo hygiene** — single git repository, meaningful commit history (small, logical commits
  per area/milestone, roughly matching section 7's build order), `.gitignore` for build
  artifacts/node_modules/target, `CLAUDE.md` at the repo root, and `/claude-sessions/` with 2–3
  exported session transcripts. **Process decision:** remote is
  `https://github.com/Davidn0072/tomer-br-on-java-live-alerts`; every commit is proposed and
  requires explicit approval before it's made — no autonomous committing, and pushes are
  confirmed separately from commits.
- **CI-less validation** — since evaluation is "runs from a clean clone using only
  `docker compose up`", periodically test that exact flow from a fresh checkout during
  development, not just incrementally.

**Dependencies:**
- Needs early input from **Server** (ports, health endpoint, DB connection env vars), **Client**
  (build output / serve mechanism, API base path), and **Emulator** (env vars, target host).
- Is a prerequisite for the **Tests** area's Playwright E2E suite and the MSSQL integration test.
- README (architecture diagram, run instructions, protocol docs) is written last, once the
  actual implementation (ports, message shapes, commands) is stable, but should be drafted
  incrementally rather than left entirely to the end.

---

## 7. Suggested build order (within the 3-day timebox)

1. **Infrastructure skeleton** — empty service scaffolds + `docker-compose.yml` wiring, so every
   area can build/run in a container from day one.
2. **Server core** — TCP framing + protocol handlers (unit-testable without MSSQL yet), then
   MSSQL persistence + schema init, then REST endpoints, then WebSocket broadcast.
3. **Emulator** — build against the server's TCP protocol once it's stable; add reconnect logic
   early since it's cheap and needed for E2E scenario 2.
4. **Client** — build against REST/WS once the server exposes them; add WS reconnect logic.
5. **Tests** — Java unit tests alongside server code; Vitest alongside client code; MSSQL
   integration test once persistence exists; Playwright E2E last, once all four containers work
   together and restart/reconnect behavior is implemented.
6. **README + CLAUDE.md + session transcripts** — DONE except session transcripts (a user
   action via Claude Code's own export feature, still pending). `README.md` written once behavior and protocol are
   stable; commit history should already reflect incremental work across the areas above.

## 8. Open decisions to make before/at the start of implementation

- ~~Build tool~~ **Decided: Maven** for Server + Emulator, targeting **Java 17** (LTS, matches
  the local JDK and `eclipse-temurin:17-jre-alpine`).
- ~~Server framework~~ **Decided: Spring Boot 3.3.x** (`web`, `websocket`, `data-jpa` starters)
  — the REST + WebSocket + JPA/MSSQL combination is exactly Spring Boot's sweet spot, and it
  keeps each area (REST controller, WS broadcaster, JPA repository) small. MSSQL access layer:
  Spring Data JPA/Hibernate (still to be wired up when persistence is implemented).
- ~~TCP protocol shape~~ **Decided:** `ProtocolMessage` sealed interface with Jackson
  polymorphic deserialization on an `EXISTING_PROPERTY` `type` discriminator; concrete records
  `ConnectMessage`, `DisconnectMessage`, `SendMessageMessage`, `AckMessage`, `ErrorMessage` in
  `server/src/main/java/com/livealerts/server/protocol/`, with `MessageCodec` doing the
  newline-delimited encode/decode and throwing a checked `MalformedMessageException` on bad
  input (see `MessageCodecTest` for the 12 cases covered: round-trips, blank/null/invalid JSON,
  missing/unknown `type`, JSON array instead of object).
- ~~Schema migration approach~~ **Decided: Hibernate `ddl-auto: update`** (not Flyway/
  Liquibase) — a single-table schema doesn't warrant a migration tool for this exercise, and
  `update` never drops data, so messages survive a server restart. Verified end-to-end against
  a real MSSQL instance: `StoredMessage` (`server/.../storage/`) + `MessageRepository` +
  `PersistingMessageReceivedListener` (wired to `TcpServer` via `MessageReceivedListener`), with
  `MessageRepositoryIT` (Testcontainers, real MSSQL, run via `mvn verify`) as the required MSSQL
  integration test. The server's own MSSQL-readiness wait is skipped: `docker-compose.yml`
  already gates `server` on `mssql`'s `service_healthy` condition, so no in-app retry loop is
  needed.
- ~~Manual trigger~~ **Decided: `POST /trigger` on a JDK `HttpServer`** inside the emulator —
  see §2.
- ~~Client build tool / WS client approach~~ **Decided: Vite + TypeScript, native `WebSocket`**
  — see §4.
- ~~Reverse proxy vs. direct port exposure~~ **Decided: nginx inside the `client` container**
  proxies `/api` and `/ws` to `server`; the client itself never has an API base URL — see §4.
  Verified end-to-end through `http://localhost` in the real docker-compose stack.
- Exact JSON protocol schema and framing edge cases (max message size, encoding) — not
  revisited beyond what's already implemented; document the as-built shape in `README.md`
  rather than treating this as still-open.
