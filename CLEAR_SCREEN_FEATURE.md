# Feature plan — "Clear screen" message

**Status: implemented and verified end to end** (one commit per step below, plus a real
`docker compose up` + Playwright run — see `PLAN.md` §9 for the completed build log). This
document is kept as the design record: every 5th message the emulator sends, it sends a special
"clear screen" message instead of a normal one; the server persists it like any other message;
the browser, on receiving it, clears its currently displayed message list.

This file is scoped to this one feature. Once approved, implementation follows the same
per-area, one-logical-commit-per-step convention as `PLAN.md` §7.

---

## 1. Requirement, as given

> Every 5th message, the emulator sends the server a special message meaning "clear screen". The
> server receives it and saves it to the DB. When the client receives this message, it must
> clear the screen.

The spec leaves a few things open that the existing system's design already answers by
precedent (see `README.md` → "Design decisions"), plus a few genuinely new calls this plan makes
explicitly so they can be reviewed before coding starts.

## 2. Design decisions (confirmed with the repo owner unless noted otherwise)

1. **Counting scope — CONFIRMED.** One running counter on the emulator, incremented on *every*
   send attempt — periodic (`EMULATOR_INTERVAL_MS`) **and** manual (`POST /trigger`) combined,
   1-indexed. The 5th, 10th, 15th... attempt sends `ClearScreen` *instead of* `SendMessage` (not
   in addition to it). Counter lives in memory only and resets on emulator restart — consistent
   with how the emulator already treats all its other state (no persistence, no queueing; see
   `ConnectionManager`).
2. **Threshold is configurable — CONFIRMED.** `EmulatorConfig` gains a `clearScreenEvery` field
   read from a new env var `EMULATOR_CLEAR_SCREEN_EVERY` (default `5`, same `env(name, default)`
   helper the rest of `EmulatorConfig` already uses). `docker-compose.yml`'s `emulator` service
   gets the new env var added alongside its existing ones (defaulting to `5` there too, so the
   documented behavior and the compose default match). Any value `<= 0` should presumably
   disable the feature entirely (counter never matches) rather than error — confirm during
   implementation if that edge case matters, but it's not worth a blocking question now.
3. **New protocol message**, `ClearScreen`, client(emulator)→server only, same family as
   `Connect`/`Disconnect`/`SendMessage`:
   ```json
   {"type":"ClearScreen","clientId":"emulator-1"}
   ```
   Server replies `{"type":"Ack","forType":"ClearScreen"}`, exactly like the other three message
   types — no new error-handling path needed, `ClientHandler` already treats any unrecognized
   `ProtocolMessage` subtype as a `RuntimeException`-free dispatch.
4. **Persistence — one table, not two.** `StoredMessage` gets a `type` discriminator column
   (`SEND_MESSAGE` / `CLEAR_SCREEN`, stored as a string) instead of a separate entity/table for
   clear-screen events. Reasoning: it's one more small column on an existing row, not a new
   relationship; `GET /api/messages` stays a single ordered feed either way, which is what both
   the REST contract and the client's rendering already assume. `text` becomes nullable (a
   `ClearScreen` row has no body). Hibernate `ddl-auto: update` adds the column automatically on
   next boot, same as every other schema change so far in this project — no Flyway migration
   needed (see `PLAN.md` §8).
5. **REST:** `MessageResponse` gains a `type` field (`"SendMessage" | "ClearScreen"`), so the
   client (and Playwright) can tell the two apart from a `GET /api/messages` response without
   guessing from `text` being null.
6. **WebSocket: no new wire shape.** `AlertMessage` (`{"type":"NewMessage","id":...,"receivedAt":...}`)
   is reused unchanged for both kinds of persisted row — it was already designed as a
   content-free "something changed, go fetch it" nudge (see README's WS design decision). The
   browser fetches the row via `GET /api/messages/{id}` same as today, and only then looks at
   *that* row's new `type` field to decide: append to the list (`SendMessage`) or clear the list
   (`ClearScreen`). This avoids teaching the WS layer a second payload shape for one extra bit of
   information that REST already carries.
7. **Client behavior on `ClearScreen` — CONFIRMED.** Empties the currently rendered message list
   (`setMessages([])`) instead of prepending, and shows a brief, non-blocking acknowledgement —
   text **"Screen was cleared"** — reusing the existing `AlertToast` component as-is (no new
   component). This gets the exact behavior asked for with zero new logic, because `App.tsx`
   already holds only one `activeAlert` at a time: the toast requires no user confirmation,
   auto-dismisses after 6s or on click same as today, and is simply replaced the moment the next
   alert (clear or normal) arrives — so it can never block or linger past a new message.
8. **Page-load / refresh semantics — CONFIRMED (option A).** Today `GET /api/messages` always
   returns full history. On a fresh page load, the client now finds the most recent
   `ClearScreen` row in the fetched list and renders only the `SendMessage` rows after it — so
   reloading the page shows the same thing you'd see if you'd had the tab open the whole time.
   Requires a few lines of client-side filtering in `App.tsx`, no server change.

## 3. Component-by-component changes

### Protocol (`server/.../protocol/`, duplicated in `emulator/.../protocol/`)
- New `ClearScreenMessage(String type, String clientId)` record, same shape/pattern as
  `DisconnectMessage` minus the `reason` field.
- Add it to `ProtocolMessage`'s `@JsonSubTypes` and `permits` list (both copies — server and
  emulator, per the project's existing "duplicated POJOs, no shared module" decision).
- `MessageCodecTest` (both modules): add round-trip + the existing malformed-input cases don't
  need new coverage, just confirm this new type decodes/encodes correctly.

### Server
- `ClientHandler.dispatch`: new `else if (message instanceof ClearScreenMessage clear)` branch —
  log, call `listener.onMessageReceived(clear)` (requires widening `MessageReceivedListener`'s
  parameter type, or overloading it — see below), ack.
- `MessageReceivedListener`: currently typed to `SendMessageMessage` specifically (check exact
  signature before coding — this plan assumes it needs widening to accept `ClearScreenMessage`
  too, e.g. a common supertype or an overload).
- `StoredMessage`: add `type` (enum `MessageType { SEND_MESSAGE, CLEAR_SCREEN }` mapped
  `@Enumerated(EnumType.STRING)`), make `text` nullable, update the constructor(s) accordingly.
- `PersistingMessageReceivedListener`: branch on the incoming message's concrete type to build
  the right `StoredMessage` (clientId + null text + CLEAR_SCREEN, vs. today's behavior).
- `MessageResponse`: add `type` field, map from `StoredMessage.getType()`.
- No `WebSocketConfig`/`AlertBroadcaster`/`AlertMessage` changes — reused as-is (see decision 5).
- New/updated tests: `MessageCodecTest`, a `ClientHandler`/`TcpServer` test asserting a
  `ClearScreen` line gets acked and reaches the listener, a `MessageRepositoryIT` case (or
  extension of the existing one) persisting a `ClearScreen` row and reading it back via REST,
  and a `MessageController` test asserting `type` serializes correctly in both cases.

### Emulator
- `ConnectionManager` (or wherever sends are currently triggered from — confirm exact call site
  during implementation): add a send counter; every 5th call to "send a message" builds and
  sends `ClearScreenMessage(clientId)` instead of `SendMessageMessage(clientId, text)`.
- `ConnectionManagerTest`: new case asserting the 5th/10th/etc. send is a `ClearScreen` frame and
  the others are `SendMessage` frames, across both the periodic and manual trigger paths.

### Client
- `types.ts`: `StoredMessage.type: 'SendMessage' | 'ClearScreen'`.
- `App.tsx`:
  - initial `fetchMessages()` load: apply the "slice after last ClearScreen" filter (decision 7A).
  - `handleAlert`: after fetching the alerted message, branch on its `type` — `ClearScreen` empties
    the list (+ optional toast), `SendMessage` keeps today's prepend behavior.
- `MessageList.test.tsx` / `App` test (new, if one doesn't exist at that level): assert a
  `ClearScreen` alert empties a previously-populated list; assert initial load with a
  `ClearScreen` in history only shows rows after it.

### E2E (Playwright)
- New `clear-screen.spec.ts`: trigger the manual endpoint 5 times (or temporarily lower the
  threshold via an env var for the test — decide during implementation whether the "5" should be
  configurable, e.g. `EMULATOR_CLEAR_SCREEN_EVERY`, default `5`, so the test doesn't need 5 real
  round trips... though 5 manual triggers is also just fine and simpler); assert the message
  list visibly empties on the 5th.

## 4. Does this need a DB change?

**Yes, but a small, backward-compatible one:** one new nullable column (`type`, or whatever the
discriminator is named) on the existing `messages` table, added automatically by Hibernate
`ddl-auto: update` on next server boot — no manual migration step, no Flyway, consistent with
how every other schema change in this project has worked so far (`PLAN.md` §8). Existing rows
get the column's default/null and are treated as `SEND_MESSAGE` going forward (add a
`@Column(... , nullable = false)` with a default only if you want to backfill explicitly — for a
take-home exercise with no production data, this plan doesn't think that's necessary).

## 5. Suggested build order

1. Protocol: `ClearScreenMessage` + codec changes in both `server/` and `emulator/` (small,
   independently testable, unlocks everything else).
2. Server: dispatch → persistence/schema → REST `type` field, with tests alongside each.
3. Emulator: counter logic + test.
4. Client: alert-handling branch + history-filter logic + tests.
5. Playwright: new `clear-screen.spec.ts` against the real stack (last, same reasoning as the
   existing two scenarios — needs every other piece working end to end first).
6. `docker compose up` smoke test of the whole flow for real, per this project's standing
   practice, before considering it done.
7. Update `README.md`'s protocol section (new message type + example) and `PLAN.md`'s log, same
   as every other feature so far.

## 6. Open questions for you before coding starts

None remain — every judgment call in §2 is confirmed. This document is ready to implement on
your go-ahead.
