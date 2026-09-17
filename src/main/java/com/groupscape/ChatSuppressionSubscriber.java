package com.groupscape;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatboxInput;

/**
 * Captures {@code !gs}-prefixed chat, from any chat type/tab, before it reaches the OSRS server
 * and reroutes it to GroupScape instead - see the group chat spec, §1 (client-side capture and
 * suppression). Subscribes to {@link ChatboxInput}, RuneLite's pre-send hook (the same one {@code
 * ChatCommandManager} uses for {@code !kc}/{@code !pb}), rather than {@code ChatMessage} (which
 * only fires after the message has already gone out).
 *
 * <p>Suppression is client-side only, by design (see the spec's "Non-plugin !gs leak handling"
 * ticket) - a group member without the plugin, or with {@link GroupScapeTrackerConfig#chatEnabled()}
 * off, still leaks the literal {@code !gs ...} text into real OSRS chat. No detection/mitigation
 * for that is in scope.
 */
@Slf4j
@Singleton
public class ChatSuppressionSubscriber {
    private static final String COMMAND_PREFIX = "!gs";

    @Inject
    private EventBus eventBus;

    @Inject
    private GroupScapeTrackerConfig config;

    @Inject
    private ChatSendManager chatSendManager;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onChatboxInput(ChatboxInput event) {
        if (!config.chatEnabled()) {
            log.debug("!gs: chatEnabled is off, skipping");
            return;
        }

        String value = event.getValue();
        if (value == null || !isGsCommand(value)) return;

        // Captured regardless of chat type/tab (Public, Clan, Private, Trade, All, ...) - a
        // !gs-prefixed line always reroutes to GroupScape and never sends as real chat, no matter
        // which channel it was typed into.
        event.consume();

        String text = value.substring(COMMAND_PREFIX.length()).trim();
        if (text.isEmpty()) return;

        chatSendManager.send(text, config);
    }

    private static boolean isGsCommand(String value) {
        if (!value.regionMatches(true, 0, COMMAND_PREFIX, 0, COMMAND_PREFIX.length())) return false;
        // Reject "!gsomething" - the prefix must be the whole token, followed by whitespace or EOL.
        return value.length() == COMMAND_PREFIX.length()
                || Character.isWhitespace(value.charAt(COMMAND_PREFIX.length()));
    }
}
