package com.groupscape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks "combo" encounters - Barrows' six brothers, the three Moons of Peril moons - where each
 * sub-boss despawns independently but the group only wants one combined activity-feed/toast/
 * webhook entry naming whichever ones actually died, emitted the moment the shared reward chest
 * (Barrows chest / Lunar Chest) is looted. Individual sub-boss kills still ship as their own
 * ordinary {@link KillLootDeathEvents#onKill} events untouched by this class - so per-NPC kill
 * count/stats tracking keeps working - they're just excluded from the server's notable-npc list
 * so they never surface on their own; this class only adds the extra combined entry on top.
 *
 * Each despawn of a tracked sub-boss ({@link #onSubBossDespawned}) appends its short display label
 * to a per-combo pending list, in kill order. {@link #onComboChestLoot} is called once the
 * matching chest's {@code LootReceived} arrives; it flushes that list into one synthesized
 * {@link KillLootDeathEvents#onComboKill} call carrying every label collected so far, then clears
 * it. A pending list idle for longer than {@link #STALE_MILLIS} (no sub-boss despawn and no chest
 * loot) is dropped rather than carried into a later, unrelated attempt - see
 * {@link #dropIfStale}. Must only be touched from the client thread.
 *
 * The despawn-tracked pending list is only a fallback, though: production data showed Barrows
 * brothers' despawns silently missing the general "died at 0hp" heuristic in
 * {@link GroupScapeTrackerPlugin#onNpcDespawned} for some brothers in a run (no despawn = no
 * {@link #onSubBossDespawned} call at all, same class of gap as the Hunllef/Hueycoatl), which
 * under-reported the combined kill. Barrows has an authoritative alternative - the
 * {@code VarbitID.BARROWS_KILLED_*} flags - so {@link #onComboChestLoot}'s caller reads those
 * directly and passes them in as {@code verifiedLabelsOverride} instead of trusting the pending
 * list for that combo. Moons of Peril has no equivalent varbit, so it still relies on the pending
 * list built from despawns.
 */
public class ComboKillEvents {
    /** No sub-boss death or chest loot arriving within 20 minutes of the last activity means the
     * attempt was abandoned (death, logout, giving up) - the reward chest, if ever opened after
     * that, shouldn't be attributed to a stale/unrelated set of kills. */
    private static final long STALE_MILLIS = 20 * 60 * 1000L;

    /** One combo's identity: the {@code LootReceived} source name that flushes it, the synthetic
     * npc name/id shipped on the combined kill, and the short display label for each tracked
     * sub-boss, keyed by that sub-boss's own {@code NpcDespawned} name. */
    private static final class ComboDefinition {
        final String chestLootName;
        final String combinedNpcName;
        final int sentinelNpcId;
        final Map<String, String> shortLabelsByNpcName;
        // True (Barrows) means an empty/stale pending label list is treated as "no verified kills
        // this attempt" and the chest loot is dropped rather than logged (see onComboChestLoot) -
        // Barrows brothers reliably despawn at 0hp so an empty list really does mean nothing died.
        // False (Moons of Peril) means the chest loot is logged unconditionally, labels or not:
        // the moons never reliably hit 0hp via onNpcDespawned (they finish through an "Enraged"
        // transform - see GroupScapeTrackerPlugin#onNpcDespawned's javadoc on mid-fight transforms),
        // so requiring a verified label first would mean the combo entry almost never fires at all.
        final boolean requireVerifiedSubKills;

        ComboDefinition(String chestLootName, String combinedNpcName, int sentinelNpcId,
                         Map<String, String> shortLabelsByNpcName, boolean requireVerifiedSubKills) {
            this.chestLootName = chestLootName;
            this.combinedNpcName = combinedNpcName;
            this.sentinelNpcId = sentinelNpcId;
            this.shortLabelsByNpcName = shortLabelsByNpcName;
            this.requireVerifiedSubKills = requireVerifiedSubKills;
        }
    }

    // Negative, made-up ids - there's no single real NPC id representing "Barrows" or "Moons of
    // Peril" as a whole, and these are never used to look up anything client-side; the server
    // resolves icon/wiki links from npcName instead (see notable_npcs::is_notable/boss_icon_url).
    private static final int BARROWS_SENTINEL_NPC_ID = -1;
    private static final int MOONS_OF_PERIL_SENTINEL_NPC_ID = -2;

    private static final ComboDefinition BARROWS = new ComboDefinition(
            "Barrows", "Barrows", BARROWS_SENTINEL_NPC_ID, mapOf(
                    "Dharok the Wretched", "Dharok",
                    "Ahrim the Blighted", "Ahrim",
                    "Guthan the Infested", "Guthan",
                    "Karil the Tainted", "Karil",
                    "Torag the Corrupted", "Torag",
                    "Verac the Defiled", "Verac"), true);

    private static final ComboDefinition MOONS_OF_PERIL = new ComboDefinition(
            "Lunar Chest", "Moons of Peril", MOONS_OF_PERIL_SENTINEL_NPC_ID, mapOf(
                    "Blue Moon", "Blue",
                    "Eclipse Moon", "Eclipse",
                    "Blood Moon", "Blood"), false);

    private static final ComboDefinition[] COMBOS = { BARROWS, MOONS_OF_PERIL };

    private static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static final class Pending {
        final List<String> labelsInKillOrder = new ArrayList<>();
        long lastActivityMillis;
    }

    private final Map<ComboDefinition, Pending> pendingByCombo = new LinkedHashMap<>();

    /** Called from {@link GroupScapeTrackerPlugin#onNpcDespawned} for every ordinary kill, in
     * addition to (not instead of) that method's normal {@code onKill} call - a no-op unless
     * {@code npcName} belongs to one of {@link #COMBOS}. */
    public synchronized void onSubBossDespawned(String npcName) {
        for (ComboDefinition combo : COMBOS) {
            String label = combo.shortLabelsByNpcName.get(npcName);
            if (label == null) continue;

            Pending pending = pendingByCombo.computeIfAbsent(combo, c -> new Pending());
            dropIfStale(pending);
            if (!pending.labelsInKillOrder.contains(label)) {
                pending.labelsInKillOrder.add(label);
            }
            pending.lastActivityMillis = System.currentTimeMillis();
            return;
        }
    }

    /** Whether {@code sourceName} is one of {@link #COMBOS}' chest names - checked by
     * {@link GroupScapeTrackerPlugin#onLootReceived} ahead of {@link ChestLootSourceNames}'
     * broader chest handling. */
    public static boolean isComboChestName(String sourceName) {
        for (ComboDefinition combo : COMBOS) {
            if (combo.chestLootName.equals(sourceName)) return true;
        }
        return false;
    }

    /**
     * Called from {@link GroupScapeTrackerPlugin#onLootReceived} when a {@code LootReceived}
     * {@code LootRecordType.EVENT} source name matches a combo's chest (see
     * {@link #isComboChestName}). Flushes whichever sub-bosses were tracked as killed since the
     * last flush into one combined kill with this loot attached, mirroring the Gauntlet's
     * synthesized-kill pattern ({@code claimPendingGauntletKill}) - unlike that one there's no
     * separate {@code onLoot} call for the caller to make, since an empty/stale pending set (see
     * below) means there may be no kill at all to attach the loot to.
     *
     * @param verifiedLabelsOverride when non-null, used in place of the despawn-tracked pending
     * list for this flush - e.g. Barrows brothers' deaths, read straight from their
     * {@code VarbitID.BARROWS_KILLED_*} flags in {@link GroupScapeTrackerPlugin#onLootReceived},
     * since despawn/health-ratio detection silently misses some brothers in production (see that
     * call site's javadoc) the same way it does for the Hunllef/Hueycoatl. Null keeps the
     * existing despawn-tracked behavior (Moons of Peril, which has no equivalent varbit).
     * @return true if {@code sourceName} matched a combo chest (caller should treat this loot as
     * claimed rather than falling back to a standalone chest "loot" event) - even when nothing
     * ends up logged (see below)
     */
    public synchronized boolean onComboChestLoot(String playerName, String sourceName, int worldX, int worldY,
                                                  int plane, int world, List<Map<String, Object>> items,
                                                  KillLootDeathEvents killLootDeathEvents,
                                                  List<String> verifiedLabelsOverride) {
        for (ComboDefinition combo : COMBOS) {
            if (!combo.chestLootName.equals(sourceName)) continue;

            Pending pending = pendingByCombo.remove(combo);
            List<String> labels;
            if (verifiedLabelsOverride != null) {
                labels = verifiedLabelsOverride;
            } else {
                if (pending != null) {
                    dropIfStale(pending);
                }
                labels = pending != null ? new ArrayList<>(pending.labelsInKillOrder) : new ArrayList<>();
            }
            // Empty/stale pending set on a combo that requires verified sub-kills (Barrows):
            // nothing verified as killed this run, so nothing is logged (no misleading empty-
            // bracket or guessed-full-run entry) - the chest loot itself is still consumed as
            // "claimed" so it doesn't also fall through to a standalone chest loot event with no
            // sub-boss context. A combo that doesn't require verification (Moons of Peril) logs
            // regardless, since its sub-bosses' despawns aren't reliably detectable to begin with
            // (see ComboDefinition#requireVerifiedSubKills) - an empty label list there just means
            // no per-moon breakdown, not "nothing happened".
            if (!labels.isEmpty() || !combo.requireVerifiedSubKills) {
                killLootDeathEvents.onComboKill(playerName, combo.sentinelNpcId, combo.combinedNpcName,
                        labels, worldX, worldY, plane, world);
                killLootDeathEvents.onLoot(combo.combinedNpcName, items);
            }
            return true;
        }
        return false;
    }

    private void dropIfStale(Pending pending) {
        if (pending.lastActivityMillis != 0 && System.currentTimeMillis() - pending.lastActivityMillis > STALE_MILLIS) {
            pending.labelsInKillOrder.clear();
        }
    }
}
