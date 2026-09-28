# Developer Exercise — Live Alerts over TCP and WebSocket

**Time box:** 3 days.

## Goal

Build a small system in which an emulator sends messages over TCP to a Java server; the server
stores them in MSSQL and pushes an alert to a React web client over WebSocket.

## Components

1. **Java server**
   - Hosts a TCP server speaking a small JSON protocol that you define, with at least
     `Connect`, `Disconnect` and `SendMessage`.
   - Stores every received message in MSSQL.
   - Notifies connected browsers over WebSocket.
   - Exposes a REST API the client uses to read messages.
2. **Emulator** — a TCP client that sends messages on demand (manual trigger) and periodically
   (configurable interval).
3. **React web client**
   - Receives the WebSocket notification, then fetches the data through the server's REST API.
     The client never connects to MSSQL.
   - Shows alerts live, and lists the stored messages.
4. **MSSQL** — the schema is created automatically on first start.

## Requirements

- Frame messages on the TCP stream explicitly, either newline-delimited or length-prefixed.
- Recover from restarts. After the server restarts, the emulator reconnects and the browser
  reconnects its WebSocket, with no manual steps.
- The server keeps working when a client sends malformed JSON or disconnects abruptly.

## Tests

- **Java:** JUnit unit tests, plus at least one integration test against MSSQL.
- **React:** Vitest unit tests.
- **E2E:** Playwright, at least these cases:
  - the emulator sends a message, and the alert appears in the browser;
  - the server restarts, and the browser reconnects and receives the next alert.

## Non-functional

- All code lives in one git repository with a meaningful commit history.
- `docker compose up` starts everything: MSSQL, the server, the client and the emulator.
- Use any official Linux-based Docker images you like.
- The app is reached at `http://localhost` in a local browser.
- **All development is done with Claude Code.** Commit your `CLAUDE.md`, plus 2–3 exported
  session transcripts under `/claude-sessions/`.

## Documentation

Write one `README.md` covering:

- the main use cases, 3–5 of them, one line each;
- the high-level architecture: one diagram of the four containers and the TCP, WebSocket and
  REST flows between them;
- how to run the system and how to run each test suite;
- the JSON protocol: every message, its fields, the framing, and an example of each;
- the design decisions you made, and what you would do with more time.

## How it is evaluated

- It runs from a clean clone, using only `docker compose up`.
- Protocol design and robustness: reconnects, malformed input.
- Quality and relevance of the tests.
- How Claude Code was used: `CLAUDE.md`, the prompts, and how you reviewed its output.
- README clarity.
