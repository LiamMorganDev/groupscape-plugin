# `!gs` client-side chat suppression — feasibility research

Research for [issue #11](https://github.com/LiamMorganDev/groupscape-plugin/issues/11) ("!gs client-side suppression feasibility"), a child of [#8](https://github.com/LiamMorganDev/groupscape-plugin/issues/8) ("Group chat (!gs) spec").

> **Note on placement**: this repo has no `docs/` convention yet (checked before writing — no `docs/` dir in `groupscape-plugin` or `groupscape-web`). This file was placed at `docs/research/` as the most reasonable default. Reconsider the location once a repo-wide docs convention exists.

## Question

Can a RuneLite plugin intercept a chat-input line the player typed themselves, prefixed with `!gs`, **before** it is transmitted to the OSRS game server as public chat, and prevent/reroute it — without violating RuneLite's "auto typing" policy?

**Short answer: yes.** RuneLite exposes a purpose-built, EventBus-delivered event (`net.runelite.client.events.ChatboxInput`) fired from inside the client's own chat-send script, before the network send happens, with a `.consume()` method that blocks the send. This is not a workaround — it is the same mechanism RuneLite's own built-in `ChatCommandManager` uses for its `!`-prefixed commands, and it is distinct from what got runelite/runelite#17440 closed.

## 1. The mechanism

### 1.1 Where the interception actually happens: `ChatInputManager`

The real interception point is a script callback, not an event a plugin author needs to touch directly. `ChatInputManager` (package-private, internal to `runelite-client`) subscribes to `ScriptCallbackEvent` and, on the `"chatboxInput"` callback name — fired by the client's own chat-send script *before* it calls `ScriptID.CHAT_SEND` — pulls the not-yet-sent text off the client's script stacks, wraps it in a `ChatboxInput` event, and posts it to the shared `EventBus`. If any subscriber calls `.consume()` on it, the manager blanks the pending value so the client script sends nothing:

```java
private void handleInput(ScriptCallbackEvent event)
{
    final Object[] objectStack = client.getObjectStack();
    ...
    final String typedText = (String) objectStack[objectStackCount - 1];
    final int chatType = intStack[intStackCount - 2];
    final int clanTarget = intStack[intStackCount - 1];

    ChatboxInput chatboxInput = new ChatboxInput(typedText, chatType,
        () -> clientThread.invokeLater(() -> sendChatboxInput(typedText, chatType, clanTarget)));
    eventBus.post(chatboxInput);

    if (chatboxInput.isConsumed())
    {
        // input was blocked.
        objectStack[objectStackCount - 1] = ""; // prevent script from sending
    }
}
```
Source: [`ChatInputManager.java` (master)](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/chat/ChatInputManager.java) (`handleInput`, `onScriptCallbackEvent`).

Note the resume callback captured in the `ChatboxInput` constructor: if a subscriber does *not* consume, or consumes now and calls `.resume()` later, the original text is (re-)sent via `client.runScript(ScriptID.CHAT_SEND, ...)`. This is what lets a handler defer/async-process and then decide whether the message should still go to public chat.

### 1.2 The event a plugin actually subscribes to: `ChatboxInput` / `ChatInput`

`ChatboxInput` (public API, `net.runelite.client.events`) extends `ChatInput`, which carries the `consume()`/`resume()`/`isConsumed()` contract:

```java
public abstract class ChatInput
{
    private final Runnable resume;
    @Getter private boolean consumed;
    protected ChatInput(Runnable resume) { this.resume = resume; }
    public void resume() { resume.run(); }
    public void consume() { consumed = true; }
}
```
Source: [`ChatInput.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/ChatInput.java)

```java
public class ChatboxInput extends ChatInput
{
    private final String value;   // the typed text
    private final int chatType;   // 0=public, 1=cheat, 2=friends chat, 3=clan, 4=guest clan
    public ChatboxInput(String value, int chatType, Runnable resume) { ... }
}
```
Source: [`ChatboxInput.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/ChatboxInput.java)

Because this is posted to the shared, plugin-visible `EventBus`, **any plugin** can subscribe directly, with no dependency on `ChatCommandManager`:

```java
@Subscribe
public void onChatboxInput(ChatboxInput chatboxInput)
{
    String message = chatboxInput.getValue();
    if (message.startsWith("!gs "))
    {
        // send message.substring(4) over the GroupScape websocket here
        chatboxInput.consume(); // never reaches OSRS chat
    }
}
```

### 1.3 RuneLite's own `ChatCommandManager` — the existing precedent

`ChatCommandManager` (`net.runelite.client.chat`) is the class backing RuneLite's built-in `!`-prefixed commands (`!price`, `!kc`, `!pb`, etc., used by the core, Jagex-approved `chatcommands` plugin). It subscribes to `ChatboxInput` exactly as above:

```java
@Subscribe
public void onChatboxInput(ChatboxInput chatboxInput)
{
    final String message = chatboxInput.getValue();
    String command = extractCommand(message);
    ChatCommand chatCommand = commands.get(command.toLowerCase());
    if (chatCommand == null) return;

    BiPredicate<ChatInput, String> input = chatCommand.getInput();
    if (input == null) return;

    if (input.test(chatboxInput, message))
    {
        chatboxInput.consume();
    }
}
```
Source: [`ChatCommandManager.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/chat/ChatCommandManager.java)

`registerCommand(String command, BiConsumer<ChatMessage,String> execute, BiPredicate<ChatInput,String> input)` is the two-phase API: the `input` predicate runs pre-send (on `ChatboxInput`) and can veto the send; `execute` runs post-send (on `ChatMessage`, after the message round-trips through the server) for commands that are allowed through.

**A concrete, shipped example that does exactly "consume before send, hand off to an external non-Jagex service, then conditionally let it through"** — `!kc <boss>` submission in the core `chatcommands` plugin:

```java
private boolean killCountSubmit(ChatInput chatInput, String value)
{
    ...
    executor.execute(() ->
    {
        try { chatClient.submitKc(playerName, boss, kc); }   // <-- external HTTP call, not Jagex's server
        catch (Exception ex) { log.warn(...); }
        finally { chatInput.resume(); }                       // <-- only *then* does it hit OSRS chat
    });
    return true; // returning true => chatboxInput.consume() is called immediately by the caller
}
```
Source: [`ChatCommandsPlugin.java`, `killCountSubmit`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java) (registered at [line 219](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java#L219): `registerCommandAsync(KILLCOUNT_COMMAND_STRING, this::killCountLookup, this::killCountSubmit)`).

This is core RuneLite, shipped to every user, and RuneLite is on Jagex's Approved Client List — i.e. this exact pattern (consume player-typed prefixed input pre-send, talk to a third-party backend, decide whether to let it reach OSRS chat at all) is already in production and accepted.

### 1.4 A third-party plugin-hub example doing precisely the GroupScape pattern

[`digiholic/osrs-archipelago`](https://github.com/digiholic/osrs-archipelago) (an OSRS/Archipelago multiworld-randomizer integration plugin) intercepts a player-typed `!ap <command>` line and reroutes it to an external websocket-based service instead of OSRS chat — this is functionally the same shape as `!gs`:

```java
@Subscribe
public void onChatboxInput(ChatboxInput chatboxInput)
{
    final String message = chatboxInput.getValue();
    String command = extractCommand(message);
    if ("!ap".equals(command)){
        String cmd = message.substring(3);
        apClient.sendChat(cmd);
        log.info("Sending string to AP: "+cmd);
        chatboxInput.consume();
    }
}
```
Source: [`ArchipelagoPlugin.java`, `onChatboxInput`](https://github.com/digiholic/osrs-archipelago/blob/master/src/main/java/gg/archipelago/ArchipelagoPlugin.java#L292-L303)

This plugin is publicly distributed (GitHub) and uses the identical `!<prefix> <rest>` → `chatboxInput.consume()` → send-to-external-service pattern GroupScape needs for `!gs`.

## 2. Policy check: is this "auto typing"?

### 2.1 What actually got #17440 closed

[runelite/runelite#17440 ("Add custom messages Plugin")](https://github.com/runelite/runelite/discussions/17440) asked for a plugin that would let a player bind pre-written messages to a hotkey, which would then **write that pre-written text into the chatbox input** for the player. Full text of the request and the maintainer's closure, pulled via the GitHub API:

> **Request body:** "It would be nice if you could add a plugin that lets you write one or more messages and every time you press a button assigned to that message, it is print in the type box to be sent."
>
> **Maintainer (`Alexsuperfly`) reply, discussion closed as `invalid`:** "this would be auto typing and is not allowed"

That is a plugin *generating* chatbox content on the player's behalf and injecting it into the input box (or sending it) without the player having typed it themselves. It never touches player-typed text — it substitutes for it.

### 2.2 The actual written policy

RuneLite's wiki, "Rejected or Rolled Back Features," under "Not currently being considered," states verbatim:

> "Plugins which programmatically insert text into the user's chatbox input for any reason (pasting messages, shorthand expansion, etc.). This is considered to be autotyping. **The only way text can get into the chatbox is if the user is pressing keys themselves.**"
>
> "Plugins which programmatically modify an outgoing chat message after user sends it (same as above)."

Source: [`Rejected-or-Rolled-Back-Features` wiki, "Not currently being considered"](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) (raw markdown confirmed via `raw.githubusercontent.com/wiki/runelite/runelite/Rejected-or-Rolled-Back-Features.md`, lines 38–39).

This page also points to Jagex's own current policy document, [Third Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) — that page was fetched and searched directly; it contains **no mention of chat, typing, or automation of chat input at all** (its "automatic" hits are exclusively about boss-mechanic positioning indicators). The "auto typing" rule as written is a RuneLite-authored interpretation/house rule, not literal text from Jagex, for what that's worth to a future reviewer.

### 2.3 Why `!gs` suppression is the opposite case, not a gray area

Both banned behaviors are about **text the player never typed** appearing in the chatbox or on the wire:
- Rule 1 bans *inserting* text into the input box that the player didn't type.
- Rule 2 bans *modifying* an outgoing message's content after the player already sent it.

`!gs` suppression does neither. The player types the full `!gs ...` line themselves, in full, with their own keystrokes. The plugin reads that exact text via `ChatboxInput.getValue()` and either lets it through unchanged or calls `.consume()` to stop it from reaching the OSRS server — it never writes anything into the chatbox, and it never sends an altered version of the player's message to Jagex's server. This is architecturally identical to `ChatCommandManager`'s own consume-before-send path used for `!kc`, `!pb`, etc., which is shipped in RuneLite's core, Jagex-approved client.

### 2.4 A counter-reading worth noting (see risks below)

Not everyone reads it that way. A third-party plugin's design notes (`DanielTate/runelite-timezone-hud`, [`DECISIONS.md`](https://github.com/DanielTate/runelite-timezone-hud/blob/master/DECISIONS.md) and [`ChatDisplayRewriter.java`](https://github.com/DanielTate/runelite-timezone-hud/blob/master/src/main/java/nz/tate/timezonehud/ChatDisplayRewriter.java)) explicitly rejected using `ChatboxInput`/`.consume()` for a `!TZ` chat-command feature, reasoning (in their own words, unsourced to a specific Jagex/RuneLite citation beyond a general reference to "Jagex's third-party client guidelines"):

> "Do not 'simplify' this into the ChatInput path. `ChatCommandManager` also accepts a `BiPredicate<ChatInput, String>` that runs *before* the message is sent and can call `chatboxInput.consume()` to swallow or alter it. That would be shorter and it would be modifying an outgoing message. D4 rejects it explicitly."

This is a single third-party author's own defensive interpretation, not a RuneLite/Jagex ruling, and it does not cite a specific policy line beyond the general "guidelines" reference — but it shows the reasoning isn't universally considered open-and-shut, and a future build session should be aware a more conservative reviewer could raise it. It's also arguably conflating "consume and never send" (what `!gs` needs) with "consume, then still send an altered version" (which was never proposed here and would be closer to Rule 2).

## 3. Recommended technique (summary for a build session)

- Subscribe to `net.runelite.client.events.ChatboxInput` on the plugin's `EventBus`-registered subscriber (`@Subscribe public void onChatboxInput(ChatboxInput event)`).
- Check `event.getValue()` for the `!gs ` prefix.
- If matched: extract the payload, hand it to the GroupScape websocket client, and call `event.consume()` — nothing further needs to happen; per §1.1 this blanks the pending client-script value so nothing is sent to the OSRS server.
- If not matched: do nothing (leave uncon­sumed so normal chat proceeds).
- No need to touch `ScriptCallbackEvent`/script stacks directly — `ChatboxInput` is the public, already-abstracted seam RuneLite provides for this.
- `chatType` on the event distinguishes public/cheat/friends/clan/guest-clan (0–4) if `!gs` should only fire from certain chat channels — confirm with the #8 spec whether `!gs` should work from any channel or public-only.

Primary class/method: **`net.runelite.client.events.ChatboxInput`**, consumed via **`ChatboxInput#consume()`**, subscribed via RuneLite's `EventBus` (`@Subscribe`). Source: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/ChatboxInput.java and https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/chat/ChatInputManager.java

## 4. Open risks / unknowns for the build session

1. **Version fragility.** `ChatInputManager` reads specific positions off `client.getObjectStack()`/`getIntStack()` keyed to the `"chatboxInput"` script-callback contract. That plumbing is internal to RuneLite core and could change between client updates; GroupScape's plugin only touches the *public* `ChatboxInput` event, so it should be insulated from that as long as RuneLite itself keeps shipping the event — but if RuneLite ever changed/removed `ChatboxInput`, `!gs` breaks silently until RuneLite updates.
2. **Ordering / re-entrancy with other plugins.** Multiple plugins can subscribe to the same `ChatboxInput` event. `EventBus` subscriber order isn't something GroupScape controls; if another installed plugin also inspects/consumes `ChatboxInput` for its own prefix matching, there's a theoretical (if narrow) collision risk. Should be fine as long as `!gs` is a distinctive-enough prefix, but worth a defensive check (don't assume GroupScape's subscriber is the only one running).
3. **Client-side only, not server-enforced.** Suppression happens purely in the local RuneLite client. If the player runs vanilla client, HDOS, or a modified/unofficial build, `!gs` text will go to real OSRS public chat unfiltered. The `#8` spec should account for this (e.g., still treat literal `!gs` text arriving as a normal `ChatMessage` from someone not running the plugin, or accept that non-plugin users leak `!gs ...` into public chat).
4. **`resume()` semantics if `!gs` messages should ever "also" go to OSRS chat.** Current ticket framing says never send to public chat. If a future revision wants `!gs` to *also* appear in-game (mirrored), the `resume()` callback captured in the original event lets you replay the original send later — but doing so after modifying content in any way would land back in Rule 2 territory (modifying outgoing message) and should be avoided; only unmodified pass-through resume is clearly safe.
5. **`chatType` filtering not yet decided.** Whether `!gs` should be recognized from every chat channel (public/friends/clan/CC) or restricted to one is a `#8` spec question, not a technical constraint — the event exposes `chatType` either way.
6. **No Jagex-specific statement on this exact pattern.** Jagex's own guidelines page (§2.2) doesn't mention chat automation at all; the "autotyping" rule is RuneLite's own gloss. That's mostly reassuring (nothing on record singles out consume-before-send of player-typed text) but also means there's no authoritative Jagex text to point to if a future reviewer disputes it — only RuneLite's own precedent (`ChatCommandManager`, `!kc` submit flow) as the strongest evidence.

## Sources

- [`ChatInputManager.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/chat/ChatInputManager.java) — where `ChatboxInput` is created from the pre-send script callback.
- [`ChatboxInput.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/ChatboxInput.java)
- [`ChatInput.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/events/ChatInput.java) — `consume()`/`resume()` contract.
- [`ChatCommandManager.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/chat/ChatCommandManager.java) — RuneLite's own `!`-command consume-before-send precedent.
- [`ChatCommandsPlugin.java`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java), [`killCountSubmit`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java#L989-L1017) — shipped example of consume → external HTTP call → conditional resume.
- [`digiholic/osrs-archipelago` `ArchipelagoPlugin.java`](https://github.com/digiholic/osrs-archipelago/blob/master/src/main/java/gg/archipelago/ArchipelagoPlugin.java#L292-L303) — third-party plugin doing the identical `!<prefix>` → external websocket → `consume()` pattern.
- [RuneLite wiki: Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) — verbatim "autotyping" / outgoing-message-modification policy text.
- [runelite/runelite discussion #17440](https://github.com/runelite/runelite/discussions/17440) — the closed request and maintainer reasoning (fetched via GitHub GraphQL API).
- [Jagex: Third Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) — official Jagex policy; contains no chat/typing-specific language.
- [`DanielTate/runelite-timezone-hud` `DECISIONS.md`](https://github.com/DanielTate/runelite-timezone-hud/blob/master/DECISIONS.md) and [`ChatDisplayRewriter.java`](https://github.com/DanielTate/runelite-timezone-hud/blob/master/src/main/java/nz/tate/timezonehud/ChatDisplayRewriter.java) — third-party author's more conservative counter-reading (cited as a risk note, not authoritative policy).
