package com.groupscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

/**
 * Kill/loot/death/chest-or-clue-loot aren't per-tick snapshot data like the rest of
 * {@link DataManager}'s {@link DataState} fields - they're discrete events detected between
 * uploads, so this accumulates them into a list (mirroring {@link DepositedItems}'s
 * accumulator pattern rather than DataState's latest-value-wins one) and drains everything
 * buffered since the last upload into the "events" key on the next {@code consumeState}.
 *
 * Kill+loot correlation is best-effort, matched by NPC name. It runs in both directions:
 * a same-name {@code LootReceived} arriving after {@code NpcDespawned} attaches straight to
 * the held {@link PendingKill}; one arriving *before* it (loot is typically granted before the
 * corpse actually despawns) is buffered in {@link #pendingUnmatchedLoot} and claimed by the
 * next matching {@link #onKill}. A freshly-captured, still loot-less {@link PendingKill} is held
 * back from the drain for {@link #LOOT_GRACE_MILLIS} (see {@link #consumeState}) rather than
 * shipped immediately, so a {@code LootReceived} that lands a moment after the despawn (observed
 * for bosses with a longer death animation, e.g. Vardorvis) still has a chance to attach before
 * the kill goes out loot-less. Once that grace period elapses with no match, the kill ships
 * without loot and the loot is dropped rather than held back indefinitely.
 *
 * Chest/clue loot (see {@link #onChestOrClueLoot}) is unrelated to that correlation - it has
 * no kill to attach to, so it's queued and drained as its own standalone "loot"-typed event
 * alongside kills/deaths. Everything here is drained into the same "events" key by
 * {@link #consumeState}, so a second, independent accumulator would race with this one over
 * who last wrote that key - hence chest/clue loot living on this class rather than a sibling
 * one. Must only be touched from the client thread.
 *
 * Every event gets a random {@code eventId} at the moment it's captured (in {@link PendingKill}'s
 * constructor, {@link #onDeath}, {@link #onChestOrClueLoot}) rather than when it's sent, so a
 * resend from {@link #restoreState} - e.g. after a server restart drops the connection before its
 * (already-successful) response reaches the client - carries the same id both times and the
 * server can reject the replay instead of double-counting the kill/loot/death.
 */
public class KillLootDeathEvents {
    /** Bound on {@link #pendingUnmatchedLoot} so a kill that never despawns (e.g. NPC leaves
     * the area) can't leak memory - the oldest unmatched loot is dropped once this is hit. */
    private static final int MAX_UNMATCHED_LOOT = 20;

    /** How long a loot-less {@link PendingKill} is held back from the drain in {@link #consumeState}
     * to give a late-arriving same-name {@code LootReceived} a chance to attach (see class doc). */
    private static final long LOOT_GRACE_MILLIS = 3000;

    /** Doom of Mokhaiotl needs a much longer grace period than {@link #LOOT_GRACE_MILLIS}: its
     * reward isn't a ground drop granted at the moment of the final delve-level despawn, but a
     * separate interface claim (right-click the "Burrow hole" it leaves, "Investigate", then
     * "Claim reward and exit") that the player triggers manually, often many seconds after the
     * kill. The default 3s window was expiring and shipping the kill loot-less before the
     * player could even open the reward menu, silently dropping the run's actual loot. */
    private static final long DOOM_OF_MOKHAIOTL_LOOT_GRACE_MILLIS = 30000;

    private static long lootGraceMillisFor(String npcName) {
        return "Doom of Mokhaiotl".equals(npcName) ? DOOM_OF_MOKHAIOTL_LOOT_GRACE_MILLIS : LOOT_GRACE_MILLIS;
    }

    private final List<PendingKill> pendingKills = new ArrayList<>();
    private final List<Map<String, Object>> pendingDeaths = new ArrayList<>();
    private final List<Map<String, Object>> pendingLoot = new ArrayList<>();
    // Internal correlation buffers only - unlike pendingKills/Deaths/Loot they're never
    // themselves part of the "events" upload, so consumeState/restoreState don't touch them.
    private final List<UnmatchedLoot> pendingUnmatchedLoot = new ArrayList<>();
    // Bound for the same leak reason as pendingUnmatchedLoot: an "X kill count is" line whose
    // boss never actually despawns (e.g. a kill count chat message from a source we don't track
    // a despawn for) can't accumulate forever.
    private static final int MAX_UNMATCHED_KC = 20;
    private final List<UnmatchedKc> pendingUnmatchedKc = new ArrayList<>();
    private String owner;

    private List<PendingKill> consumedKills;
    private List<Map<String, Object>> consumedDeaths;
    private List<Map<String, Object>> consumedLoot;
    private String consumedOwner;

    private static final class UnmatchedLoot {
        final String npcName;
        final List<Map<String, Object>> items;

        UnmatchedLoot(String npcName, List<Map<String, Object>> items) {
            this.npcName = npcName;
            this.items = items;
        }
    }

    private static final class UnmatchedKc {
        final String npcName;
        final int accountKc;

        UnmatchedKc(String npcName, int accountKc) {
            this.npcName = npcName;
            this.accountKc = accountKc;
        }
    }

    private static final class PendingKill {
        final int npcId;
        final String npcName;
        final int worldX;
        final int worldY;
        final int plane;
        final int world;
        final String occurredAt;
        // Generated once here, at capture time, not on every send - consumeState/restoreState
        // requeue this same PendingKill object verbatim on a failed upload, so a resend carries
        // the identical id and the server can recognize it as a replay rather than a new kill
        // (see DataManager.restoreStateIfNothingUpdated).
        final String eventId;
        // Wall-clock capture time, purely local bookkeeping for the LOOT_GRACE_MILLIS hold in
        // consumeState - unlike occurredAt this is never sent to the server.
        final long createdAtMillis;
        List<Map<String, Object>> loot;
        Integer accountKc;
        // Non-null only for a combo kill synthesized by ComboKillEvents (e.g. "Barrows"/"Moons of
        // Peril") - the short display labels of whichever sub-bosses were actually killed that
        // run, in kill order. Absent for every ordinary single-NPC kill.
        List<String> subKills;

        PendingKill(int npcId, String npcName, int worldX, int worldY, int plane, int world) {
            this.npcId = npcId;
            this.npcName = npcName;
            this.worldX = worldX;
            this.worldY = worldY;
            this.plane = plane;
            this.world = world;
            this.occurredAt = Instant.now().toString();
            this.eventId = UUID.randomUUID().toString();
            this.createdAtMillis = System.currentTimeMillis();
        }

        Map<String, Object> toMap() {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "kill");
            event.put("npcId", npcId);
            event.put("npcName", npcName);
            event.put("worldX", worldX);
            event.put("worldY", worldY);
            event.put("plane", plane);
            event.put("world", world);
            event.put("occurredAt", occurredAt);
            event.put("eventId", eventId);
            if (loot != null) {
                event.put("loot", loot);
            }
            if (accountKc != null) {
                event.put("accountKc", accountKc);
            }
            if (subKills != null) {
                event.put("subKills", subKills);
            }
            return event;
        }
    }

    public synchronized void onKill(String playerName, int npcId, String npcName, int worldX, int worldY, int plane, int world) {
        owner = playerName;
        PendingKill kill = new PendingKill(npcId, npcName, worldX, worldY, plane, world);
        // Claim the oldest same-name loot that arrived before this despawn, if any (see class doc).
        for (int i = 0; i < pendingUnmatchedLoot.size(); i++) {
            UnmatchedLoot loot = pendingUnmatchedLoot.get(i);
            if (loot.npcName.equals(npcName)) {
                kill.loot = loot.items;
                pendingUnmatchedLoot.remove(i);
                break;
            }
        }
        // Same correlation for a "Your X kill count is: N." chat line that beat the despawn.
        for (int i = 0; i < pendingUnmatchedKc.size(); i++) {
            UnmatchedKc kc = pendingUnmatchedKc.get(i);
            if (kc.npcName.equals(npcName)) {
                kill.accountKc = kc.accountKc;
                pendingUnmatchedKc.remove(i);
                break;
            }
        }
        pendingKills.add(kill);
    }

    /**
     * Like {@link #onKill}, but for a combo kill synthesized by {@link ComboKillEvents} once its
     * shared reward chest is looted (e.g. "Barrows", "Moons of Peril") - {@code npcName} is the
     * combo's own synthetic name, not a real despawned NPC, and {@code subKills} names whichever
     * sub-bosses were actually killed that run. The caller must immediately follow this with
     * {@link #onLoot} for the same {@code npcName} so the chest's loot attaches, exactly like an
     * ordinary kill.
     */
    public synchronized void onComboKill(String playerName, int npcId, String npcName, List<String> subKills,
                                          int worldX, int worldY, int plane, int world) {
        onKill(playerName, npcId, npcName, worldX, worldY, plane, world);
        pendingKills.get(pendingKills.size() - 1).subKills = subKills;
    }

    /**
     * Attaches loot to the most recently-queued pending kill for this NPC name, merging into
     * any loot already attached rather than requiring the kill to still be loot-less. Some
     * bosses (e.g. Vet'ion, whose mid-fight transform splits its reward across multiple loot
     * table rolls) fire {@code LootReceived} more than once for a single kill; requiring
     * {@code kill.loot == null} would route that second batch to {@link #pendingUnmatchedLoot}
     * as if it belonged to a future kill, where it would only be reclaimed by another kill of
     * the same NPC name - silently losing the loot (and the items in it) otherwise. Matching by
     * name (not id) since {@code LootReceived} exposes the NPC's composition name, not
     * necessarily the same id {@code NpcDespawned} reported for multi-form bosses.
     */
    public synchronized void onLoot(String npcName, List<Map<String, Object>> items) {
        for (int i = pendingKills.size() - 1; i >= 0; i--) {
            PendingKill kill = pendingKills.get(i);
            if (kill.npcName.equals(npcName)) {
                if (kill.loot == null) {
                    kill.loot = new ArrayList<>(items);
                } else {
                    kill.loot.addAll(items);
                }
                return;
            }
        }
        // No matching pending kill yet - buffer it for onKill to claim once the despawn fires.
        if (pendingUnmatchedLoot.size() >= MAX_UNMATCHED_LOOT) {
            pendingUnmatchedLoot.remove(0);
        }
        pendingUnmatchedLoot.add(new UnmatchedLoot(npcName, items));
    }

    /**
     * Attaches the account's real in-game kill count (parsed from the "Your X kill count is: N."
     * chat line) to the most recently-queued still-unattached pending kill for this NPC name -
     * this is the authoritative KC a Discord kill notification should show, not a count of kills
     * this server happened to see logged. Mirrors {@link #onLoot}'s correlation: the chat line
     * can arrive either before or after {@code NpcDespawned}, so an unmatched one is buffered for
     * {@link #onKill} to claim.
     */
    public synchronized void onKillCount(String npcName, int accountKc) {
        for (int i = pendingKills.size() - 1; i >= 0; i--) {
            PendingKill kill = pendingKills.get(i);
            if (kill.npcName.equals(npcName) && kill.accountKc == null) {
                kill.accountKc = accountKc;
                return;
            }
        }
        if (pendingUnmatchedKc.size() >= MAX_UNMATCHED_KC) {
            pendingUnmatchedKc.remove(0);
        }
        pendingUnmatchedKc.add(new UnmatchedKc(npcName, accountKc));
    }

    /**
     * Queues a standalone chest/instance reward or clue casket opening - unlike {@link #onKill}/
     * {@link #onLoot}, there's no correlation step: the {@code LootReceived} for one of these
     * sources is itself the terminal, complete event.
     *
     * @param sourceType "chest" or "clue"
     * @param clueTier   "beginner".."master" when {@code sourceType} is "clue", else {@code null}
     */
    public synchronized void onChestOrClueLoot(String playerName, String sourceType, String sourceName, String clueTier,
                                                int worldX, int worldY, int plane, int world,
                                                List<Map<String, Object>> items) {
        owner = playerName;
        Map<String, Object> loot = new HashMap<>();
        loot.put("type", "loot");
        loot.put("sourceType", sourceType);
        loot.put("sourceName", sourceName);
        if (clueTier != null) {
            loot.put("clueTier", clueTier);
        }
        loot.put("worldX", worldX);
        loot.put("worldY", worldY);
        loot.put("plane", plane);
        loot.put("world", world);
        loot.put("occurredAt", Instant.now().toString());
        loot.put("eventId", UUID.randomUUID().toString());
        loot.put("loot", items);
        pendingLoot.add(loot);
    }

    public synchronized void onDeath(String playerName, int worldX, int worldY, int plane, int world, String killerName) {
        owner = playerName;
        Map<String, Object> death = new HashMap<>();
        death.put("type", "death");
        death.put("worldX", worldX);
        death.put("worldY", worldY);
        death.put("plane", plane);
        death.put("world", world);
        death.put("occurredAt", Instant.now().toString());
        death.put("eventId", UUID.randomUUID().toString());
        if (killerName != null) {
            death.put("killerName", killerName);
        }
        pendingDeaths.add(death);
    }

    public synchronized void consumeState(Map<String, Object> output) {
        if (pendingKills.isEmpty() && pendingDeaths.isEmpty() && pendingLoot.isEmpty()) return;

        // If the owner doesn't match this flush's target, leave pending events untouched so
        // they're retried on a later consumeState() call instead of being silently dropped -
        // a mismatch here (e.g. a transient local-player-name hiccup around a death/teleport)
        // used to fall through to the clear below with nothing ever attached to output.
        String whoIsUpdating = (String) output.get("name");
        if (owner == null || !owner.equals(whoIsUpdating)) return;

        // Loot-less kills younger than LOOT_GRACE_MILLIS are held back (see class doc) rather
        // than shipped immediately, giving a late LootReceived a chance to still attach.
        long now = System.currentTimeMillis();
        List<PendingKill> readyKills = new ArrayList<>();
        List<PendingKill> stillWaiting = new ArrayList<>();
        for (PendingKill kill : pendingKills) {
            if (kill.loot != null || now - kill.createdAtMillis >= lootGraceMillisFor(kill.npcName)) {
                readyKills.add(kill);
            } else {
                stillWaiting.add(kill);
            }
        }

        if (readyKills.isEmpty() && pendingDeaths.isEmpty() && pendingLoot.isEmpty()) return;

        List<Map<String, Object>> events = new ArrayList<>();
        for (PendingKill kill : readyKills) {
            events.add(kill.toMap());
        }
        events.addAll(pendingDeaths);
        events.addAll(pendingLoot);
        output.put("events", events);

        consumedKills = readyKills.isEmpty() ? null : new ArrayList<>(readyKills);
        consumedDeaths = pendingDeaths.isEmpty() ? null : new ArrayList<>(pendingDeaths);
        consumedLoot = pendingLoot.isEmpty() ? null : new ArrayList<>(pendingLoot);
        consumedOwner = owner;
        pendingKills.clear();
        pendingKills.addAll(stillWaiting);
        pendingDeaths.clear();
        pendingLoot.clear();
        // Only release the owner gate once nothing of this player's is left pending - a still-
        // waiting kill needs owner to keep matching whoIsUpdating on the next consumeState call,
        // otherwise it would get stuck here forever (onKill is the only other thing that sets it).
        if (stillWaiting.isEmpty()) {
            owner = null;
        }
    }

    public synchronized void restoreState() {
        if (consumedKills == null && consumedDeaths == null && consumedLoot == null) return;

        if (consumedKills != null) {
            pendingKills.addAll(0, consumedKills);
        }
        if (consumedDeaths != null) {
            pendingDeaths.addAll(0, consumedDeaths);
        }
        if (consumedLoot != null) {
            pendingLoot.addAll(0, consumedLoot);
        }
        if (owner == null) {
            owner = consumedOwner;
        }
        consumedKills = null;
        consumedDeaths = null;
        consumedLoot = null;
        consumedOwner = null;
    }
}
