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
    private static final int CHAT_MESSAGE_MAX_LEN = 150;

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

    /** Truncates silently to {@code CHAT_MESSAGE_MAX_LEN} (see the "Message formatting and length
     * limits" spec ticket §8) before sending - the server truncates too, but callers like {@link
     * ChatSuppressionSubscriber}'s in-game {@code !gs} capture never pass through the side panel's
     * input field, so this is the only client-side enforcement point that covers every caller. */
    public void send(String text, GroupScapeTrackerConfig config) {
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) {
            log.debug("Chat: not sending - apiKey empty={}, accountHash={}", apiKey.isEmpty(), accountHashValue);
            return;
        }

        SendChatMessageRequestBody body = new SendChatMessageRequestBody();
        body.text = text.length() > CHAT_MESSAGE_MAX_LEN ? text.substring(0, CHAT_MESSAGE_MAX_LEN) : text;

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
