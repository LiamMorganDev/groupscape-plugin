package com.groupscape.roster;

import java.util.List;

/**
 * DTOs mirroring the server's WebSocket wire format
 * (`groupscape-web/server/src/websocket.rs`). Field names match the JSON
 * keys exactly (server serializes camelCase) so Gson's default reflection
 * can deserialize without extra annotations.
 */
public class RosterWireTypes {
    public static class Envelope {
        public String type;
        public com.google.gson.JsonObject payload;
        public String ts;
    }

    public static class RosterSnapshotPayload {
        public List<RosterMemberEntry> roster;
    }

    public static class RosterMemberEntry {
        public String name;
        public String color;
        public WireVitals vitals;
    }

    public static class VitalsUpdatePayload {
        public String name;
        public WireVitals vitals;
    }

    public static class KillEventPayload {
        public String memberName;
        public String npcName;
    }

    public static class DropEventPayload {
        public String memberName;
        public String message;
    }

    public static class ColorUpdatePayload {
        public String name;
        public String color;
    }

    public static class PingStartPayload {
        public String pingId;
        public String memberName;
        /** {@code "tile"} or {@code "npc"} - see the server's {@code websocket::PingKind}. */
        public String kind;
        public int x;
        public int y;
        public int plane;
        public String npcName;
    }

    public static class PingUpdatePayload {
        public String pingId;
        public int x;
        public int y;
        public int plane;
    }

    public static class PingEndPayload {
        public String pingId;
    }

    public static class RaidMarkerStartPayload {
        public String markerId;
        public String memberName;
        /** e.g. {@code "danger"}, {@code "defend"}, {@code "loot"}, {@code "focus"}, {@code "one"}..{@code "four"}, {@code "a"}..{@code "d"} - see the server's {@code websocket::MarkerType}. */
        public String markerType;
        /** {@code "tile"} or {@code "npc"} - see the server's {@code websocket::PingKind}. */
        public String kind;
        public int x;
        public int y;
        public int plane;
        public String npcName;
    }

    public static class RaidMarkerUpdatePayload {
        public String markerId;
        public int x;
        public int y;
        public int plane;
    }

    public static class RaidMarkerEndPayload {
        public String markerId;
    }

    public static class ChatMessagePayload {
        public long messageId;
        /** Nullable - an account with no resolved character can still send. */
        public String memberName;
        public String text;
    }

    /** {@code chat_read} envelope - fires when any of the account's sessions advances its
     * server-side read cursor (spec §6). {@code memberName} identifies whose cursor advanced, the
     * same self-filtering pattern as {@code ChatRateLimitedPayload} on the server side - the
     * receiver checks it against the local player's own name to tell "one of my other sessions
     * just read chat" apart from "someone else in the group did". */
    public static class ChatReadPayload {
        public String memberName;
        public long messageId;
    }

    public static class WireVitals {
        public Integer hp;
        public Integer maxHp;
        public Integer prayer;
        public Integer maxPrayer;
        public Integer runEnergy;
        public Integer specEnergy;
        public Integer world;
        public String lastHeartbeatAt;
        public String targetName;
        public Integer targetHealthRatio;
        public Integer targetHealthScale;
        public List<String> activePrayers;
        public String richPresence;
        /** {@code [x, y, plane, ...]} - see the server's {@code WireVitals.coordinates}. */
        public List<Integer> coordinates;
    }
}
