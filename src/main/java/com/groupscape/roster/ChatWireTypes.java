package com.groupscape.roster;

/**
 * DTO for {@code GET /get-chat-messages}'s response body (a {@code groupscape.chat_messages} row
 * as serialized by the server's {@code models::ChatMessage} - see `groupscape-web/server/src/models.rs`).
 * Distinct from {@link RosterWireTypes.ChatMessagePayload} (the WebSocket envelope), which uses
 * `text` rather than `messageText` and has no `createdAt`.
 */
public class ChatWireTypes {
    public static class ChatMessageWire {
        public long messageId;
        public String memberName;
        public String messageText;
        public String createdAt;
    }
}
