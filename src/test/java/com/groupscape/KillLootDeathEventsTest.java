package com.groupscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class KillLootDeathEventsTest {
    @Test
    public void consumeStateAttachesEventsWhenOwnerMatches() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath", null);

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        assertTrue(output.containsKey("events"));
        assertEquals(1, ((List<?>) output.get("events")).size());
    }

    @Test
    public void consumeStateRetriesInsteadOfDroppingOnOwnerMismatch() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath", null);

        // A flush for a different member's snapshot arrives first (e.g. a transient
        // local-player-name hiccup around a death/teleport) - the death must not be dropped.
        Map<String, Object> mismatched = new HashMap<>();
        mismatched.put("name", "SomeoneElse");
        events.consumeState(mismatched);

        assertFalse("event must not be attached to a mismatched flush", mismatched.containsKey("events"));

        // The next flush targeting the real owner must still be able to claim it.
        Map<String, Object> matched = new HashMap<>();
        matched.put("name", "Zezima");
        events.consumeState(matched);

        assertTrue("event must survive to be attached on a later matching flush", matched.containsKey("events"));
        assertEquals(1, ((List<?>) matched.get("events")).size());
    }

    @Test
    public void onDeathAttachesDoomDelveLevelWhenPresent() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Doom of Mokhaiotl", 5);

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        Map<?, ?> death = (Map<?, ?>) ((List<?>) output.get("events")).get(0);
        assertEquals(5, death.get("doomDelveLevel"));
    }

    @Test
    public void onDeathOmitsDoomDelveLevelWhenAbsent() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath", null);

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        Map<?, ?> death = (Map<?, ?>) ((List<?>) output.get("events")).get(0);
        assertFalse(death.containsKey("doomDelveLevel"));
    }

    @Test
    public void consumeStateHoldsLootlessKillForGraceLoot() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onKill("Zezima", 8288, "Vardorvis", 100, 200, 0, 301);

        // A drain landing before LootReceived fires (e.g. a boss with a longer death animation)
        // must not ship the kill loot-less - it should hold it back for the grace period instead.
        Map<String, Object> early = new HashMap<>();
        early.put("name", "Zezima");
        events.consumeState(early);
        assertFalse("kill must be held back, not shipped loot-less, while still within grace", early.containsKey("events"));

        events.onLoot("Vardorvis", List.of(Map.of("id", 28334, "quantity", 1)));

        Map<String, Object> later = new HashMap<>();
        later.put("name", "Zezima");
        events.consumeState(later);

        assertTrue(later.containsKey("events"));
        List<?> shipped = (List<?>) later.get("events");
        assertEquals(1, shipped.size());
        assertEquals(List.of(Map.of("id", 28334, "quantity", 1)), ((Map<?, ?>) shipped.get(0)).get("loot"));
    }

    @Test
    public void consumeStateShipsLootlessKillOnceGraceExpires() throws InterruptedException {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onKill("Zezima", 8288, "Vardorvis", 100, 200, 0, 301);

        Thread.sleep(3100);

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        assertTrue("kill must ship loot-less once the grace period elapses with no match", output.containsKey("events"));
        assertEquals(1, ((List<?>) output.get("events")).size());
    }

    @Test
    public void onDoomOfMokhaiotlKillShipsOneEntryWithDelveLevel() {
        // Individual delve-level despawns are never queued as their own kills (see
        // GroupScapeTrackerPlugin#onNpcDespawned) - only this one synthesized kill, created at
        // claim time, should ever be shipped for a delve run.
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDoomOfMokhaiotlKill("Zezima", 14707, "Doom of Mokhaiotl", 5, 100, 200, 0, 301);
        events.onLoot("Doom of Mokhaiotl", List.of(Map.of("id", 554, "quantity", 266)));

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        assertTrue(output.containsKey("events"));
        List<?> shipped = (List<?>) output.get("events");
        assertEquals(1, shipped.size());
        Map<?, ?> kill = (Map<?, ?>) shipped.get(0);
        assertEquals(5, kill.get("delveLevel"));
        assertEquals(List.of(Map.of("id", 554, "quantity", 266)), kill.get("loot"));
    }

    @Test
    public void onDoomOfMokhaiotlKillOmitsDelveLevelWhenUnobserved() {
        // The reward widget scrape can fail to find a level (unverified wording match) - the kill
        // must still ship rather than being dropped.
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDoomOfMokhaiotlKill("Zezima", 14707, "Doom of Mokhaiotl", null, 100, 200, 0, 301);
        events.onLoot("Doom of Mokhaiotl", List.of(Map.of("id", 554, "quantity", 266)));

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        assertTrue(output.containsKey("events"));
        Map<?, ?> kill = (Map<?, ?>) ((List<?>) output.get("events")).get(0);
        assertFalse("delveLevel must be absent, not null, when unobserved", kill.containsKey("delveLevel"));
    }
}
