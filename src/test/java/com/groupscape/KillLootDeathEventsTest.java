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
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath");

        Map<String, Object> output = new HashMap<>();
        output.put("name", "Zezima");
        events.consumeState(output);

        assertTrue(output.containsKey("events"));
        assertEquals(1, ((List<?>) output.get("events")).size());
    }

    @Test
    public void consumeStateRetriesInsteadOfDroppingOnOwnerMismatch() {
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onDeath("Zezima", 100, 200, 0, 301, "Vorkath");

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
    public void consumeStateHoldsDoomOfMokhaiotlPastTheOrdinaryGraceWindow() throws InterruptedException {
        // Doom of Mokhaiotl's reward is claimed via an interface ("Investigate" the burrow hole,
        // then "Claim reward and exit"), not a same-tick ground drop, so it routinely lands well
        // after the ordinary 3s LOOT_GRACE_MILLIS - the kill must still be held open for it.
        KillLootDeathEvents events = new KillLootDeathEvents();
        events.onKill("Zezima", 14707, "Doom of Mokhaiotl", 100, 200, 0, 301);

        Thread.sleep(3100);

        Map<String, Object> early = new HashMap<>();
        early.put("name", "Zezima");
        events.consumeState(early);
        assertFalse("Doom of Mokhaiotl kill must outlive the ordinary 3s grace window", early.containsKey("events"));

        events.onLoot("Doom of Mokhaiotl", List.of(Map.of("id", 554, "quantity", 266)));

        Map<String, Object> later = new HashMap<>();
        later.put("name", "Zezima");
        events.consumeState(later);

        assertTrue(later.containsKey("events"));
        List<?> shipped = (List<?>) later.get("events");
        assertEquals(1, shipped.size());
        assertEquals(List.of(Map.of("id", 554, "quantity", 266)), ((Map<?, ?>) shipped.get(0)).get("loot"));
    }
}
