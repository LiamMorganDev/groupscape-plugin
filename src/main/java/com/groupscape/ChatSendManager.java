package com.groupscape;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

/**
 * Fire-and-forget relay of a captured {@code !gs} chat line to
 * {@code POST /api/characters/{accountHash}/send-chat-message} - see the "!gs" group chat spec,
 * §1 (client-side capture/suppression) and §3 (wire protocol). Mirrors {@link
 * com.groupscape.roster.PingManager}'s send pattern: never called from the client thread's
 * blocking path, so the HTTP round-trip runs on its own daemon executor instead.
 */
@Slf4j
@Singleton
public class ChatSendManager {
    private final Client client;
    private final HttpRequestService httpRequestService;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "groupscape-chat-send");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public ChatSendManager(Client client, HttpRequestService httpRequestService) {
        this.client = client;
        this.httpRequestService = httpRequestService;
    }

    private static class SendChatMessageRequestBody {
        String text;
    }

    /** Truncation to {@code CHAT_MESSAGE_MAX_LEN} happens server-side (see the "Message
     * formatting and length limits" spec ticket) - this just forwards the raw captured text. */
    public void send(String text, GroupScapeTrackerConfig config) {
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) {
            log.debug("Chat: not sending - apiKey empty={}, accountHash={}", apiKey.isEmpty(), accountHashValue);
            return;
        }

        SendChatMessageRequestBody body = new SendChatMessageRequestBody();
        body.text = text;

        String url = httpRequestService.getBaseUrl() + "/api/characters/" + accountHashValue + "/send-chat-message";
        sendExecutor.submit(() -> {
            HttpRequestService.HttpResponse response = httpRequestService.post(url, apiKey, body);
            if (!response.isSuccessful()) {
                log.debug("chat send failed: {} {}", response.getCode(), response.getBody());
            } else {
                log.debug("chat send ok");
            }
        });
    }

    public void shutdown() {
        sendExecutor.shutdownNow();
    }
}
