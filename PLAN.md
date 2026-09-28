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

## 2. Emulator

A standalone TCP client (Java, matching the server's runtime for protocol/DTO reuse, e.g. via
a small shared module or duplicated POJOs — decide during implementation).

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

## 3. Server

Java application (Spring Boot is the natural fit for REST + WebSocket + MSSQL integration, but
confirm version/build tool — Maven vs Gradle — before scaffolding).

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

## 4. Client

React web application (build tooling: Vite recommended for speed; confirm during setup).

**Components:**
- **WebSocket connection module** — connects to the server's WS endpoint on load, with
  auto-reconnect (backoff) so it recovers after a server restart with no manual steps (e.g.
  browser refresh not required).
- **REST data layer** — fetch/axios wrapper for `GET /api/messages` and `GET /api/messages/{id}`.
- **Alert/notification UI** — shows a live, transient alert when a WS message arrives (e.g. a
  toast or banner), triggering a REST fetch for the details.
- **Message list view** — displays stored messages fetched via REST, refreshed when new alerts
  arrive.
- **Connection status indicator** (nice-to-have) — shows WS connected/reconnecting state, useful
  for manual verification and possibly for E2E assertions.
- **Config** — server REST/WS base URL via build-time or runtime env variable (must work both
  in local dev and behind the docker-compose network/reverse proxy at `http://localhost`).

**Dependencies:**
- Needs the **Server**'s REST/WS contracts defined (can start against a mocked/stubbed server
  or an early skeleton).
- Needs **Infrastructure** to decide how the client is served and how `http://localhost`
  reaches both static assets and the server's API/WS (reverse proxy vs. separate ports vs. the
  client container proxying `/api` and `/ws` to the server).
- Feeds **Tests**: Vitest unit tests for components/hooks; the app itself is the target of the
  Playwright E2E suite.

---

## 5. Tests

Three suites, each owned by its respective stack but planned together here for coverage and
sequencing.

**Java (JUnit):**
- Unit tests: TCP frame parsing/serialization, protocol message validation, malformed-JSON
  handling, WS broadcast logic (mockable), REST controller logic.
- Integration test(s): server + real MSSQL (via Testcontainers or the docker-compose MSSQL
  instance) — verify a message written through the persistence layer is actually stored and
  retrievable via REST.

**React (Vitest):**
- Unit tests for the WS module (reconnect behavior with a mocked socket), the alert component,
  and the message list component/data-fetch hook.

**E2E (Playwright):**
- Scenario 1: emulator sends a message (via its manual trigger) → alert appears in the browser.
- Scenario 2: server container restarts → browser's WS reconnects automatically → next
  emulator-sent message still produces an alert, with no manual browser action.
- Both scenarios require orchestrating docker-compose (or an equivalent test harness) to
  start/restart specific containers from the test runner.

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
6. **README + CLAUDE.md + session transcripts** — finalized once behavior and protocol are
   stable; commit history should already reflect incremental work across the areas above.

## 8. Open decisions to make before/at the start of implementation

- ~~Build tool~~ **Decided: Maven** for Server + Emulator. Still open: Spring Boot vs. plain
  Java, and MSSQL access layer (JPA/Hibernate vs. JDBC vs. jOOQ).
- Client build tool (Vite) and WS client approach (native `WebSocket` vs. a library).
- Exact JSON protocol schema and framing edge cases (max message size, encoding).
- How the emulator's "manual trigger" is exposed for E2E control.
- Reverse proxy vs. direct port exposure for reaching the client/server at `http://localhost`.
- Schema migration approach (Hibernate `ddl-auto` vs. Flyway/Liquibase) for "created
  automatically on first start."
