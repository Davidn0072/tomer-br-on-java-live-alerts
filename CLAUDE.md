# CLAUDE.md

Guidance for Claude Code (and any future contributor) working in this repository.

## What this repo is

Implementation of the "Live Alerts over TCP and WebSocket" exercise: an emulator sends TCP
messages to a Java server, the server stores them in MSSQL and pushes alerts over WebSocket to a
React client, which reads message data through the server's REST API.

- Full requirements: [`Developer_Exercise.md`](Developer_Exercise.md)
- Build plan, scope per area, and open/resolved design decisions: [`PLAN.md`](PLAN.md)
- User-facing docs (architecture diagram, protocol spec, run instructions): `README.md`
  (written once behavior stabilizes — see PLAN.md section 7)

## Repository layout

```
server/      Java TCP server + REST API + WebSocket broadcaster + MSSQL persistence (Maven)
emulator/    Java TCP client: manual + periodic message sending (Maven)
client/      React web app: WebSocket alerts + REST reads
docker-compose.yml   Orchestrates mssql, server, emulator, client
claude-sessions/     Exported Claude Code session transcripts (required deliverable)
```

## Tech stack decisions (see PLAN.md §8 for the full list and rationale)

- **Server & Emulator:** Java 17, built with **Maven**.
- **Server framework:** **Spring Boot 3.3.x** (`web`, `websocket`, `data-jpa` starters).
- **MSSQL:** official `mcr.microsoft.com/mssql/server` Linux image; schema created
  automatically on startup — no manual DB setup steps.
- **Client:** React + **Vite** + TypeScript. Native `WebSocket` (no library). No API base URL
  anywhere — the client only ever calls relative paths; nginx (in the `client` container) and
  Vite's dev-server proxy (for `npm run dev`) both forward `/api` and `/ws` to `server`.
- **Orchestration:** Docker Compose, four services (`mssql`, `server`, `emulator`, `client`),
  reachable as a whole at `http://localhost`.
- **TCP framing:** newline-delimited JSON. Message types at minimum: `Connect`, `Disconnect`,
  `SendMessage` (plus server→client `Ack`/`Error` for malformed input). Exact schema is
  finalized during Server implementation and documented in `README.md`.

## Conventions

- **Env vars**, not hardcoded config, for anything that differs between local/dev/docker
  (ports, hosts, DB credentials, emulator interval). Keep naming consistent across services
  (e.g. `DB_HOST`, `DB_PORT`, `SERVER_HOST`, `SERVER_TCP_PORT`).
- **No premature abstractions.** This is a 3-day-scoped exercise — prefer straightforward code
  over frameworks-within-frameworks; add structure only where the exercise's requirements
  (robustness, reconnects, tests) actually need it.
- **Robustness is a first-class requirement**, not an afterthought: malformed JSON on the TCP
  socket, abrupt client disconnects, and server restarts must never require a manual recovery
  step anywhere in the system (emulator, server, or browser).

## Git workflow (agreed with the repo owner)

- Remote: `https://github.com/Davidn0072/tomer-br-on-java-live-alerts`
- Commits are scoped to one logical unit of work (roughly matching the build order in
  `PLAN.md` §7 — e.g. "protocol DTOs + framing", "TCP server", "REST API", "emulator reconnect
  logic").
- **Every commit is proposed (message + file list) and requires explicit approval before it's
  made.** No autonomous committing.
- **Every push is confirmed separately from the commit that precedes it.**
- Commit messages end with the required Claude Code attribution trailer.

## How to run (fill in as each piece lands)

- `docker compose up` — starts the full stack. *(Currently: all four services — `mssql`,
  `server`, `emulator`, `client` — are fully functional and verified end-to-end together through
  `http://localhost`: nginx proxies both `/api` and the `/ws` upgrade to `server`, and a raw WS
  client receives the live alert the moment the emulator's message lands. Also verified
  restarting `server` mid-run and watching the emulator reconnect and resume sending with zero
  manual steps. Not yet verified by actually looking at it in a browser — no browser-automation
  tool was available this session; that's still owed before calling this exercise done.)*
- **Always smoke-test a finished component with the real `docker compose up`**, not just `mvn
  test`/`mvn verify`. Two Server bugs only surfaced this way: MSSQL's official image doesn't
  auto-create an app database (only `master` exists on first start), and `MessageCodec` was
  missing `@Component` so the full Spring context failed to boot — neither was catchable by
  unit tests or Spring test slices (`@WebMvcTest`, `@DataJpaTest`) alone. See PLAN.md §3.
- `cd server && mvn test` — fast JUnit unit tests (protocol codec, TCP server over real
  sockets). No Docker/DB required.
- `cd emulator && mvn test` — fast JUnit unit tests (protocol codec, `ConnectionManager`
  reconnect behavior over real sockets, `ControlServer` HTTP endpoints). No Docker required.
- `cd server && mvn verify` — unit tests plus `MessageRepositoryIT`, the required MSSQL
  integration test (Testcontainers spins up a real `mssql` image). Requires a working Docker
  daemon. **Windows/Docker Desktop note:** Testcontainers 1.20.x fails to detect Docker Desktop
  29.x over the Windows named pipe (`BadRequestException`/"Could not find a valid Docker
  environment", even though `docker` and `docker compose` work fine); Testcontainers **1.21.4**
  resolved it — keep `testcontainers.version` in `server/pom.xml` at or above that if bumping
  Docker Desktop causes this again.
- `cd client && npm test` — Vitest unit tests (API client, `useAlertSocket` reconnect behavior,
  `AlertToast`, `MessageList`). `npm run build` type-checks and produces the production bundle;
  `npm run dev` runs it locally against a `server` on `localhost:8080` via the Vite dev proxy.
- Playwright E2E: commands to be added here once the suite exists.

## Required deliverables checklist (from `Developer_Exercise.md`)

- [x] `PLAN.md` — work plan
- [x] `CLAUDE.md` — this file
- [ ] `claude-sessions/` — 2–3 exported session transcripts (add near the end of the exercise)
- [ ] `README.md` — use cases, architecture diagram, run/test instructions, protocol spec,
      design decisions
- [ ] Working `docker compose up` from a clean clone
