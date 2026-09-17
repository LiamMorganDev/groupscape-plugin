package com.groupscape.roster;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.groupscape.HttpRequestService;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches {@code GET /api/characters/{accountHash}/get-chat-messages} once per connect (triggered
 * from {@code GroupLinkListener#onLinked()}, mirroring how the party overlay itself only becomes
 * live once linked) and merges the page into {@link ChatState}. See the "!gs" chat spec §6 - the
 * delivery cursor is tracked server-side per-account, not client-supplied, so a reconnect (even
 * from a different device) only backfills what arrived since the account's own last delivery.
 */
@Slf4j
public class ChatBackfillClient {
    private static final Type MESSAGE_LIST_TYPE = new TypeToken<List<ChatWireTypes.ChatMessageWire>>() {}.getType();

    private final HttpRequestService httpRequestService;
    private final Gson gson;
    private final ChatState state;

    public ChatBackfillClient(HttpRequestService httpRequestService, Gson gson, ChatState state) {
        this.httpRequestService = httpRequestService;
        this.gson = gson;
        this.state = state;
    }

    public void fetch(String baseUrl, String accountHash, String apiKey) {
        if (baseUrl == null || accountHash == null || apiKey == null || apiKey.trim().isEmpty()) return;

        String url = baseUrl + "/api/characters/" + accountHash + "/get-chat-messages";
        HttpRequestService.HttpResponse response = httpRequestService.get(url, apiKey);
        if (!response.isSuccessful()) {
            log.debug("get-chat-messages failed: {} {}", response.getCode(), response.getBody());
            return;
        }

        try {
            List<ChatWireTypes.ChatMessageWire> messages = gson.fromJson(response.getBody(), MESSAGE_LIST_TYPE);
            if (messages == null) return;
            for (ChatWireTypes.ChatMessageWire wire : messages) {
                state.add(new ChatState.Entry(wire.messageId, wire.memberName, wire.messageText,
                        Instant.parse(wire.createdAt)));
            }
        } catch (Exception e) {
            log.debug("get-chat-messages: failed to parse response", e);
        }
    }
}
