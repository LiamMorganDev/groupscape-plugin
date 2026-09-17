# Group Chat (`!gs`) — Build-Ready Spec

Compiled from the [Group chat (!gs) spec](https://github.com/LiamMorganDev/groupscape-plugin/issues/8) wayfinder map. Each section links back to the decision ticket that owns the detail.

## Destination

In-game `!gs`-prefixed messages are captured client-side and suppressed from public chat, relayed via websocket to a persistent per-group chat log (DB-backed), and rendered on three configurable in-game surfaces — inline in the Clan tab (default on), a GroupScape side-panel tab with send capability (default on), and an optional floating overlay (default off) — plus the webapp chat panel. Webapp users are full send/receive peers regardless of whether a game client is connected. Every chat line shows the sender's helmet icon rendered in their GroupScape member color.

**Out of scope:** moderation/spam controls (muting, message deletion, profanity filtering) — trusted small-group assumption, consistent with GroupScape's existing full-visibility group data model.

## 1. Client-side capture and suppression

_[!gs client-side suppression feasibility](https://github.com/LiamMorganDev/groupscape-plugin/issues/11) · [!gs chat channel scope](https://github.com/LiamMorganDev/groupscape-plugin/issues/19)_

- Subscribe to RuneLite's `net.runelite.client.events.ChatboxInput`, fired before the client sends chat to the OSRS server.
- When the typed line is `!gs`-prefixed, call `.consume()` to suppress the send and reroute the text to GroupScape instead — regardless of `ChatboxInput.chatType`/which chat tab it was typed into (Public, Clan, Private, Trade, All, ...). A `!gs`-prefixed message always goes to GroupScape and never sends as real chat.
- This is the same pre-send interception pattern RuneLite's own `ChatCommandManager` uses (`!kc`, `!pb`), and is distinguishable from the "auto typing" policy violation (runelite/runelite#17440): it only reads/suppresses player-typed text, never generates or inserts text into the chatbox.
- Full research writeup: [docs/research/gs-chat-suppression-feasibility.md](https://github.com/LiamMorganDev/groupscape-plugin/blob/research/gs-chat-suppression/docs/research/gs-chat-suppression-feasibility.md).
- **Known limitation, accepted:** suppression is client-side only. A group member without the plugin (or with it disabled) who types `!gs ...` leaks that literal text into real OSRS public chat. No detection/mitigation logic — same trusted-small-group rationale as the moderation out-of-scope call. Document via a tooltip on the plugin's `!gs` toggle and a line in the written docs. ([Non-plugin !gs leak handling](https://github.com/LiamMorganDev/groupscape-plugin/issues/18))

## 2. In-game display surfaces

_[In-game chat display design](https://github.com/LiamMorganDev/groupscape-plugin/issues/9)_

Three surfaces ship, each behind its own settings toggle:

| Surface | Default | Notes |
|---|---|---|
| Inline in the Clan chat tab | **On** | Teal `[GS]` tag + colored name, printed via `ChatMessageType.CLAN_MESSAGE` (no real clan membership required). Live-only, no send capability here. |
| GroupScape side-panel tab | **On** (primary surface) | RuneLite-owned side panel (Party-plugin style) with its own input box — send + receive. A native chatbox tab is not feasible; RuneLite's plugin API cannot add to the chatbox tab strip, which is a game-client widget, not RuneLite UI. |
| Floating overlay panel | **Off** (opt-in) | Always-on-top, draggable box over the viewport, independent of the chatbox. |

Every chat line, on every surface (in-game and webapp), shows the sender's **helmet icon** next to their name, rendered in their existing GroupScape per-member color (same color source as `color_update` websocket messages / `player-portrait`). Helmet icon assets sourced from the OSRS wiki `Category:Icons`/`Icon` pages, per project convention.

Mockup reference: https://claude.ai/artifact/RivhhRoX2aQLv3fqmqGcsd (superseded detail: the mockup's "Option B" showed a native chatbox tab; the shipped surface is a RuneLite side panel instead).

## 3. Wire protocol and storage schema

_[Chat wire protocol and storage schema](https://github.com/LiamMorganDev/groupscape-plugin/issues/10)_

Dedicated table — not a reuse of `activity_events`, which FKs to `session_id` and therefore requires an open plugin session; webapp-only senders have no session to hang a row off.

```sql
-- db.rs migration "create_chat_messages_table" (has_migration_run/commit_migration convention)
CREATE TABLE groupscape.chat_messages (
  message_id BIGSERIAL PRIMARY KEY,
  group_id BIGINT NOT NULL REFERENCES groupscape.groups(group_id) ON DELETE CASCADE,
  account_id BIGINT NOT NULL REFERENCES groupscape.accounts(id) ON DELETE CASCADE,
  member_name CITEXT,                    -- nullable: point-in-time display snapshot, not a live FK
  message_text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX chat_messages_group_id_idx ON groupscape.chat_messages (group_id, message_id DESC);
```

- **Ordering/id:** `message_id BIGSERIAL`, globally auto-incrementing — total order, cheap `WHERE message_id > X` backfill, no clock-skew ties.
- **Sender identity:** `account_id` (server-internal auth identity, never sent over the wire) + nullable `member_name` (point-in-time display snapshot — full send/receive eligibility rules are in §4).
- **Group scoping:** implicit via which group's broadcast channel the envelope is published on, same as every existing `WsEnvelope` variant — no `groupId` field in the payload.
- **Color:** deliberately omitted from the payload. Every surface already tracks member→color via `RosterSnapshot`/`ColorUpdate`; renderers key into that existing state by `memberName` rather than denormalizing a copy that can go stale.

```rust
// websocket.rs
ChatMessage { payload: ChatMessagePayload, ts: DateTime<Utc> },

#[serde(rename_all = "camelCase")]
pub struct ChatMessagePayload {
    pub message_id: i64,
    pub member_name: Option<String>,
    pub text: String,
}
// wire: { type: "chat_message", payload: { messageId, memberName, text }, ts }
```

```java
// RosterWireTypes.java
public static class ChatMessagePayload {
    public long messageId;
    public String memberName;
    public String text;
}
```

## 4. Auth and permission model

_[Chat auth and permission model](https://github.com/LiamMorganDev/groupscape-plugin/issues/13)_

Scoped to authenticated group members with **at least one linked RuneScape character** — narrower than general group-data visibility (roster, activity feed, etc.), which doesn't require a linked character.

- Pending/unlinked members are excluded from send/receive until they have a linked character (every chat line needs a real character identity for the helmet-icon-in-member-color rendering).
- No admin-only/service account carve-out — GroupScape has no such account state distinct from real members.

**Plugin `account_id` resolution:** `chat_messages.account_id` is a server-internal identity, never sent over the wire (§3), so the plugin never needs to know or supply it. `/get-chat-messages` and `/send-chat-message` are mounted under the character-key scope (`/api/characters/{account_hash}/...`, same as `get-activity-events`/`ping`/etc.), not the group-token dashboard scope. `CharacterAuthenticateMiddleware` already resolves an `Account` row from the API key during auth (`account.id`) to look up the character by `account_hash` — that `account_id` just wasn't previously threaded through into `AuthenticationResult`, because no character-scope handler needed a DB `account_id` FK until chat. Fix: `AuthenticationResult` gained an `account_id: Option<i64>` field (`Some` for the character-key scope, `None` for the group-token scope — same shape as the existing `account_hash`/`character_id` fields). `send_chat_message` uses `auth.account_id` when present (plugin path) and falls back to the webapp's session-token `require_account()` otherwise (webapp path) — no route duplication, one handler serves both scopes.

## 5. Multi-group scoping

_[Multi-group active-group chat scoping](https://github.com/LiamMorganDev/groupscape-plugin/issues/17)_

No new scoping mechanism — chat reuses the existing active-group precedent at both layers:

- **Plugin:** the server already resolves a connected character's group unambiguously (`character_auth_middleware.rs`, `character_group_links` PK on `character_id`). `!gs` input/output scopes to whatever group the logged-in character belongs to — implicit, not configured.
- **Webapp:** chat scopes to `storage.getGroup()`, the same localStorage-held active group every other webapp view uses.
- **Concurrent-clients edge case:** two RuneLite clients on the same account, each logged into a different character in a different group, isolate naturally — each connection is already scoped per-character, one connection per group, no multiplexing.

## 6. History, backfill, and read state

_[Chat history and backfill behavior](https://github.com/LiamMorganDev/groupscape-plugin/issues/12) · [New-message notification behavior](https://github.com/LiamMorganDev/groupscape-plugin/issues/15)_

**Backfill (fixed history window — supersedes the original per-account delivery cursor):**

- Inline Game/All tab: live-only, no backfill — it's a native shared widget with no GroupScape-owned scrollback.
- Side panel, overlay, webapp: identical mechanism. On connect, `GET /get-chat-messages` returns every message from the last **7 days** (`CHAT_HISTORY_DAYS`), capped at ~200 messages, oldest-first.
- The window is per-group, not per-account — every session backfilling a group sees the same messages regardless of its own read/delivery history. The original design gated this on a per-account server-side "delivered up to" cursor that advanced on every backfill; that made a plain refresh (webapp) or reconnect (plugin) look like "nothing happened" for anything already delivered once, which read as message history silently disappearing. The fixed window replaces it outright — there is no more delivery cursor, `chat_delivery_cursors` table included.
- A background job (`prune_old_chat_messages`, hourly) deletes rows older than `CHAT_HISTORY_DAYS` from `groupscape.chat_messages`, so the window is also the retention policy, not just a query filter — nothing extends past 7 days by outliving the delete job.

**Notifications and read cursor (unchanged — still separate from the history window):**

Only two surface-states get a "missed message" signal: side panel (chat tab not active) and webapp (chat panel not visible, or browser tab lost OS focus). No signal for the inline Game tab (the live chat is the notification), the overlay when disabled, or a fully-closed webapp (no OS push).

- **Side panel:** chat ships as a new internal tab inside the existing single `GroupScapePanel` (not a second toolbar icon). Plain unread dot (no count) on the chat tab. Also fires RuneLite's `Notifier` (tray flash/sound) on new messages — off by default, toggle in plugin settings. (First plugin use of `Notifier`.)
- **Webapp:** badge dot on the Chat nav item, plus browser tab title/favicon change when the tab loses OS focus (Page Visibility API / `document.hasFocus()`). Does not reuse `toast-stack`'s per-event toast pattern — too chatty for a per-message toast.
- **Read cursor:** per-account, server-side cursor (`groupscape.chat_read_cursors`). Auto-advances only when the chat surface is both visible (tab/panel selected) **and** focused (window/client has OS focus) — merely having the tab selected while unfocused doesn't count as read. Advancing it broadcasts a new `ChatRead` websocket message to the account's other live sessions, clearing their dot/badge in real time (same live-push pattern as `color_update`/`DropEvent`).
- **Unread divider:** side panel and webapp both draw a divider line above the first message newer than the read cursor at the time the surface last rendered (frozen for that viewing session — it doesn't jump mid-session as `markRead`/`mark-chat-read` calls advance the cursor underneath it). Suppressed when the cursor is `0`/unset (nothing to distinguish as "already read" yet) or when everything currently in view is unread. Not drawn on the floating overlay window, which has no unread concept of its own per the no-signal list above.

## 7. Flood protection

_[Minimum flood-protection guard](https://github.com/LiamMorganDev/groupscape-plugin/issues/14)_

Server self-protection, distinct from the out-of-scope moderation feature:

- Keyed on `account_id` (not websocket connection) — survives reconnects.
- Cap: ~10 messages / 10 seconds (order-of-magnitude anchor; exact algorithm — fixed window vs. token bucket — is an implementation detail).
- Over-cap: drop the excess message server-side and return an error to the sender.
- New `WsEnvelope` variant, e.g. `ChatRateLimited`, following the existing per-message-type variant convention.

## 8. Message formatting and length limits

_[Message formatting and length limits](https://github.com/LiamMorganDev/groupscape-plugin/issues/16)_

- **Max length:** 150 characters, both webapp-typed and plugin-relayed messages.
- **Enforcement:** truncate silently to 150 chars, client-side and server-side — no rejection error path (diverges from the 300-char reject-on-violation precedent in `activity_event_comments`).
- **Formatting:** plain text only. No RuneLite markup (`<col=...>`, etc.) interpreted on any surface. Raw string stored as-is in `groupscape.chat_messages`; each renderer (webapp DOM, RuneLite chat line) escapes/encodes for its own output context.
- **Unicode/emoji:** stored and relayed unfiltered. In-game surfaces may render unsupported glyphs as tofu — accepted as a cosmetic limitation.

Precedent referenced: `ACTIVITY_COMMENT_MAX_LEN = 300` (`groupscape-web/server/src/models.rs:1032`) and its reject-on-violation validation (`authed.rs:1459`) — chat intentionally diverges given its more conversational, rapid-fire nature.

## 9. Webapp chat UI scope

_[Webapp chat UI polish beyond MVP](https://github.com/LiamMorganDev/groupscape-plugin/issues/20)_

- **Search — in scope, narrowly:** client-side filter/highlight over the already-backfilled (~200-message) set. No new server-side search endpoint, no new DB query, no new `WsEnvelope` variant.
- **Reactions/threads — deferred**, not part of this spec. Chat is lightweight/ephemeral group banter (the `!gs` relay use case), not a discussion thread; reactions/threads would need real schema (`activity_event_comments`-style table) that doesn't serve that core use case. Revisit as a separate later effort if usage shows demand.

## Implementation precedents to follow

- `RosterClient.java` / `RosterWireTypes.java` (plugin-side wire DTOs) mirror `groupscape-web/server/src/websocket.rs`'s `WsEnvelope` enum field-for-field (camelCase via Gson). The new `ChatMessage` variant follows this same mirroring convention.
- DB schema precedent: `groupscape.activity_event_comments` (author-snapshot + text + timestamp pattern) and the `db.rs` migration style (`has_migration_run`/`commit_migration`).
- Per-member color: reuse the `color_update` websocket message / `player-portrait` component's color source — don't reinvent.
- Helmet icon source: OSRS wiki `Category:Icons` / `Icon` pages, per project icon-asset convention.
