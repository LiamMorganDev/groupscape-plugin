package com.groupscape;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Fire-and-forget push of the account's read cursor to
 * {@code POST /api/characters/{accountHash}/mark-chat-read} - see the "!gs" chat spec §6 (distinct
 * from the delivery cursor {@code ChatBackfillClient} backfills against). Mirrors {@link
 * ChatSendManager}'s send pattern: own daemon executor, never blocking the caller (the sidepanel's
 * Swing refresh timer, which decides *when* to call this based on the visible+focused rule - see
 * {@code GroupScapePanel}).
 */
@Slf4j
@Singleton
public class ChatMarkReadManager {
    private final Client client;
    private final HttpRequestService httpRequestService;
    private final ExecutorService markReadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "groupscape-chat-mark-read");
        t.setDaemon(true);
        return t;
    });
    private volatile long lastSyncedMessageId = 0;

    @Inject
    public ChatMarkReadManager(Client client, HttpRequestService httpRequestService) {
        this.client = client;
        this.httpRequestService = httpRequestService;
    }

    private static class MarkChatReadRequestBody {
        long messageId;
    }

    /** No-op when {@code messageId} isn't newer than what this session already synced - the panel
     * calls this on every refresh tick while the chat tab is visible and focused, so most calls
     * would otherwise be redundant round-trips. */
    public void markRead(long messageId, GroupScapeTrackerConfig config) {
        if (messageId <= lastSyncedMessageId) return;
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) return;

        lastSyncedMessageId = messageId;
        MarkChatReadRequestBody body = new MarkChatReadRequestBody();
        body.messageId = messageId;

        String url = httpRequestService.getBaseUrl() + "/api/characters/" + accountHashValue + "/mark-chat-read";
        markReadExecutor.submit(() -> {
            HttpRequestService.HttpResponse response = httpRequestService.post(url, apiKey, body);
            if (!response.isSuccessful()) {
                log.debug("mark-chat-read failed: {} {}", response.getCode(), response.getBody());
            }
        });
    }

    public void shutdown() {
        markReadExecutor.shutdownNow();
    }
}
