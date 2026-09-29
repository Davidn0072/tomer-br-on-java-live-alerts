# Live Alerts over TCP and WebSocket

An emulator sends TCP messages to a Java server, which stores them in MSSQL and pushes live
alerts to a React web client over WebSocket; the client reads the actual message data through
the server's REST API. Built for the "Live Alerts" take-home exercise (see
[`Developer_Exercise.md`](Developer_Exercise.md) for the original spec).

## Use cases

- An operator opens the web client and sees every message received so far, newest first.
- The emulator sends a message (on a timer, or via a manual trigger) and it appears in every
  open browser tab within moments, with no page refresh.
- The server container restarts (deploy, crash, manual `docker compose restart server`) and both
  the emulator and every open browser reconnect and resume automatically, with no manual step.
- A client sends malformed JSON, or disconnects mid-message; the server logs it, keeps every
  other connection alive, and keeps accepting new ones.
- A new developer runs `docker compose up` on a clean clone and reaches the full working system
  at `http://localhost`, with no manual DB setup.

## Architecture

```
                         TCP (newline-delimited JSON)
   ┌───────────┐   Connect / SendMessage / Disconnect   ┌────────────┐
   │ emulator  │ ──────────────────────────────────────>│            │
   │ (Java)    │ <──────────────────────────────────────│            │
   └───────────┘        Ack / Error                      │            │
                                                          │   server   │      T-SQL      ┌───────┐
                                                          │  (Java /   │────────────────>│ mssql │
                                                          │Spring Boot)│<────────────────│       │
                                                          │            │                 └───────┘
   ┌───────────┐   WebSocket: NewMessage alert (id only) │            │
   │  browser  │<──────────────────────────────────────  │            │
   │ (React    │                                          │            │
   │  client,  │   REST: GET /api/messages, /messages/{id}│            │
   │  via nginx)│─────────────────────────────────────────>            │
   └───────────┘                                          └────────────┘
```

Four containers, one docker-compose network, reachable as a whole at `http://localhost`:

- **`mssql`** — official `mcr.microsoft.com/mssql/server:2022-latest`. Schema is created
  automatically on first boot (Hibernate `ddl-auto: update`); no manual DB setup.
- **`server`** — Java 17 / Spring Boot. Listens for raw TCP connections on `5000` and serves
  REST + WebSocket on `8080`.
- **`emulator`** — Java 17 TCP client. Sends `Connect` on startup, then `SendMessage` on a timer
  (`EMULATOR_INTERVAL_MS`) and on demand via `POST /trigger` on its own control port (`9000`).
- **`client`** — React (Vite + TypeScript) built to static files and served by nginx on port
  `80`. nginx reverse-proxies `/api/*` and `/ws/*` to `server:8080`, so the client's own code
  never has an API base URL — it only ever calls relative paths, in docker-compose and in local
  `npm run dev` alike (Vite's dev-server proxy does the same thing there).

The WebSocket alert carries only an id and timestamp, never the message body — the browser
always re-fetches the actual content over REST, so REST/MSSQL stays the single source of truth
and the WS channel is just a push notification.

## How to run

From a clean clone, with Docker running:

```
docker compose up
```

Then open `http://localhost`. All four services build and start; `server` waits for `mssql` to
report healthy, and `emulator`/`client` wait for `server`.

Useful ports published to the host for debugging: `server` TCP on `5000`, server REST/WS on
`8080`, emulator control API on `9000`, mssql on `1433`.

To watch the restart-recovery behavior manually: `docker compose restart server` while the app
is open — the emulator's log shows it losing and regaining the connection, and the browser's
connection indicator goes `Live` → `Reconnecting…` → `Live` with no page action.

## Running the tests

| Suite | Command | Needs |
|---|---|---|
| Server unit tests | `cd server && mvn test` | nothing (no Docker/DB) |
| Server + MSSQL integration test | `cd server && mvn verify` | Docker (Testcontainers spins up a real `mssql`) |
| Emulator unit tests | `cd emulator && mvn test` | nothing |
| Client unit tests | `cd client && npm test` | nothing (Vitest + jsdom) |
| Client type-check/build | `cd client && npm run build` | nothing |
| E2E (Playwright) | see below | Docker |

**E2E** drives the real stack, not mocks:

```
docker compose up -d --build --wait
cd e2e && npx playwright test
```

Both scenarios required by the exercise are covered and pass against real containers:
- `alert-flow.spec.ts` — triggers a send via the emulator's control API, asserts the alert toast
  appears in the browser and the message list grows.
- `restart-recovery.spec.ts` — restarts the `server` container mid-run and asserts the browser
  reconnects and receives the next alert with no manual step (~2 minutes: a real Spring Boot /
  MSSQL restart, not simulated).

`playwright.config.ts` runs with `workers: 1` — both specs share the one live stack, and running
them in parallel let the restart test's server restart break the other spec's WebSocket mid-test.

**Windows/Docker Desktop note:** if `mvn verify` fails with "Could not find a valid Docker
environment" even though `docker` works fine, it's Testcontainers failing to detect Docker
Desktop over the Windows named pipe on some 1.20.x versions — `server/pom.xml` already pins
`testcontainers.version` to `1.21.4`, which resolves it.

## Protocol

### TCP wire protocol (emulator ↔ server)

Newline-delimited JSON: one JSON object per line, each terminated by `\n`. Chosen over
length-prefixing for readability and debuggability (works with `telnet`/`netcat`) — message
bodies are short text, so a raw `\n` inside one is not a real-world concern here.

Every message is a JSON object with a `type` discriminator. Client → server:

**`Connect`** — sent once, right after opening the socket (and again on every reconnect).
```json
{"type":"Connect","clientId":"emulator-1"}
```

**`SendMessage`** — the actual payload to persist and alert on.
```json
{"type":"SendMessage","clientId":"emulator-1","text":"Hello from the emulator"}
```

**`Disconnect`** — sent right before the client closes the socket on purpose (not sent on an
abrupt drop, by definition).
```json
{"type":"Disconnect","clientId":"emulator-1","reason":"shutting down"}
```

Server → client, in reply to each line above:

**`Ack`** — the previous message was accepted.
```json
{"type":"Ack","forType":"SendMessage"}
```

**`Error`** — the previous line could not be processed (blank line, invalid JSON, unknown/missing
`type`). The connection is **not** closed; the client can keep sending.
```json
{"type":"Error","message":"Invalid protocol message: Unrecognized field \"typo\""}
```

### WebSocket (server → browser), `/ws/alerts`

Server push only, no messages expected from the client. One frame per newly persisted message,
carrying no body — the browser fetches the actual content over REST:

```json
{"type":"NewMessage","id":42,"receivedAt":"2026-09-29T14:03:21.512Z"}
```

The browser connects to `wss://<host>/ws/alerts` (or `ws://` locally) and reconnects on a fixed
3-second delay after any drop — no backoff, since a server restart resolves in seconds and a
tight fixed interval gets the browser back online fastest.

### REST API (browser → server)

Read-only; the client never talks to MSSQL directly.

- `GET /api/messages` — all stored messages, newest first.
- `GET /api/messages/{id}` — one message by id (404 if it doesn't exist); this is what the
  browser calls right after a WebSocket alert.
- `GET /api/health` — liveness check, used by the docker-compose healthcheck and E2E readiness
  waits.

`GET /api/messages` / `GET /api/messages/{id}` response shape:
```json
{"id":42,"clientId":"emulator-1","text":"Hello from the emulator","receivedAt":"2026-09-29T14:03:21.512Z"}
```

### Emulator control API (test/manual trigger), `emulator:9000`

Not part of the exercise's core protocol — a small HTTP surface so a message can be triggered
on demand from outside the container (used by Playwright's `alert-flow` scenario).

- `POST /trigger` — sends one `SendMessage` immediately using the emulator's own `clientId`.
- `GET /health` — liveness check.

## Design decisions

- **Newline-delimited JSON framing**, not length-prefixed — see "TCP wire protocol" above.
- **Spring Boot for the server** (`web`, `websocket`, `data-jpa`) — REST + WebSocket + MSSQL is
  exactly its sweet spot, and it keeps each concern (TCP handler, REST controller, WS
  broadcaster, JPA repository) in its own small class rather than hand-rolling any of them.
- **No framework in the emulator** — a TCP client with a scheduler and a two-endpoint HTTP
  control surface doesn't need Spring; plain Java + Jackson (+ the JDK's built-in
  `com.sun.net.httpserver.HttpServer` for the control API) keeps the image small and startup
  instant.
- **Duplicated protocol POJOs between server and emulator**, not a shared Maven module — the
  protocol is five small records; a shared module would complicate each side's independent
  Docker build for little benefit at this size.
- **Hibernate `ddl-auto: update`**, not Flyway/Liquibase — a single-table schema doesn't warrant
  a migration tool for a 3-day exercise, and `update` never drops data, so messages survive a
  server restart.
- **WebSocket alerts carry no message body**, only `id` + `receivedAt` — REST/MSSQL stays the
  single source of truth; the browser always re-fetches, so a missed or out-of-order WS frame
  can never leave it showing stale data.
- **Fixed-delay reconnect** (3s) in both the emulator and the browser, not exponential backoff —
  the only failure mode in scope is a single server restart, which resolves in a few seconds;
  backoff would only slow down recovery from the case this system is actually built to handle.
  The emulator drops (logs, doesn't queue) any message it can't send while disconnected — the
  next scheduled or manual trigger succeeds once reconnected.
- **No API base URL anywhere in the client** — it only calls relative paths (`/api/...`,
  `/ws/...`); nginx (docker-compose) and Vite's dev proxy (`npm run dev`) both forward those to
  `server`, so the browser and server are always same-origin and no CORS config exists anywhere.
- **A bad line never closes a TCP connection** — only the client closing its end, or a real I/O
  failure, does. One misbehaving client can't affect any other connection or crash the listener.
- Two bugs only surfaced by running the real `docker compose` stack, not by any unit/slice test:
  the MSSQL image doesn't auto-create an app database (only `master` exists on first start), and
  a missing `@Component` on `MessageCodec` broke the full Spring context boot without breaking
  any test slice that didn't scan that package. See [`PLAN.md`](PLAN.md) §3 for both fixes.

## What I'd do with more time

- Tighten the WebSocket's `setAllowedOrigins("*")` now that the reverse-proxy setup is finalized
  (same-origin via nginx/Vite proxy in every deployment shape actually used), instead of leaving
  it wide open.
- Pagination/limit on `GET /api/messages` — it currently returns the full table, fine for a
  demo's message volume but not for a long-running deployment.
- A max message size / line length limit on the TCP codec, so a malicious or buggy client can't
  send an unbounded line and grow server-side buffers unboundedly.
- Exponential backoff (capped) instead of a fixed 3s retry, if reconnect scenarios beyond "one
  restart of a few seconds" become in scope (e.g. extended MSSQL outages).
- Persist and expose emulator client identity/session history (currently only the messages
  themselves are stored, not connect/disconnect events) for an audit trail.
- Structured logging (JSON logs) and basic metrics (messages/sec, active WS sessions) for actual
  production observability — current logging is plain text, fine for this exercise's scale.
- A small TLS/auth story for the TCP and WebSocket endpoints — both are unauthenticated and
  unencrypted, acceptable for a local take-home exercise but not for anything beyond it.

## Repository layout

See [`CLAUDE.md`](CLAUDE.md) for the full repo layout, tech stack rationale, environment
gotchas, and the agreed git workflow. See [`PLAN.md`](PLAN.md) for the area-by-area build plan
and the running log of every design decision made during development.
