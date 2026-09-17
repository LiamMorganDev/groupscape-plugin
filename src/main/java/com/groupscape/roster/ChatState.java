package com.groupscape.roster;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Thread-safe in-memory group chat log: written from both the backfill HTTP client and the
 * WebSocket callback thread (a live message can race the backfill response that would otherwise
 * have included it), read from the sidepanel's Swing refresh timer. Keyed by {@code messageId} so
 * either writer can land the same message without duplicating it - see the "!gs" chat spec §6.
 */
public class ChatState {
    private static final int MAX_MESSAGES = 200;

    public static class Entry {
        public final long messageId;
        public final String memberName;
        public final String text;
        public final Instant createdAt;

        public Entry(long messageId, String memberName, String text, Instant createdAt) {
            this.messageId = messageId;
            this.memberName = memberName;
            this.text = text;
            this.createdAt = createdAt;
        }
    }

    private final TreeMap<Long, Entry> messagesById = new TreeMap<>();

    public synchronized void add(Entry entry) {
        messagesById.put(entry.messageId, entry);
        while (messagesById.size() > MAX_MESSAGES) {
            messagesById.remove(messagesById.firstKey());
        }
    }

    public synchronized List<Entry> all() {
        return new ArrayList<>(messagesById.values());
    }

    /** The account's delivery cursor for backfill's {@code since} query param - see spec §6. */
    public synchronized long latestMessageId() {
        return messagesById.isEmpty() ? 0 : messagesById.lastKey();
    }

    public synchronized void clear() {
        messagesById.clear();
    }
}
