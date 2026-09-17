package com.groupscape;

import com.groupscape.roster.ChatState;
import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.GroupSnapshotState;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterState;
import com.groupscape.roster.RosterWireTypes;
import com.groupscape.sidepanel.ChatPanel;
import com.groupscape.sidepanel.RosterListPanel;
import com.groupscape.sidepanel.SidePanelTheme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidepanel with two top-level tabs (spec §2/§6): Roster - the existing HP/prayer/run/spec +
 * per-member inventory/equipment/stats view - and Chat, the group's {@code !gs} chat log with its
 * own input box. A Swing {@link Timer} pulls from {@link RosterState}/{@link GroupSnapshotState}
 * (roster) and {@link ChatState} (chat) rather than being pushed to directly, since all three are
 * written from background threads.
 *
 * <p>The Chat tab's unread dot doubles as this session's read-cursor auto-advance trigger (spec
 * §6): {@link #updateChatUnreadDot} only advances {@link #lastSeenChatMessageId} - and pushes it
 * to the server via {@link ChatMarkReadManager} - while the tab is both the active one
 * ({@code visible}) and the RuneLite client window has OS focus ({@link #windowFocused}, tracked
 * via a {@link WindowFocusListener} attached in {@link #addNotify()}). {@link #applyChatRead}
 * mirrors the same clear from the other direction - an incoming {@code ChatRead} broadcast from
 * one of this account's *other* live sessions.
 */
class GroupScapePanel extends PluginPanel {
    private static final int REFRESH_MS = 600;
    private static final String TAB_ROSTER = "roster";
    private static final String TAB_CHAT = "chat";

    private final Client client;
    private final GroupScapeTrackerConfig config;
    private final ClientThread clientThread;
    private final ChatMarkReadManager chatMarkReadManager;
    private final RosterListPanel rosterListPanel;
    private final ChatPanel chatPanel;
    private final Timer refreshTimer;
    private final JLabel rosterTabButton;
    private final JLabel chatTabButton;
    private final JPanel content;

    private String activeTab = TAB_ROSTER;
    private volatile long lastSeenChatMessageId = -1;
    private volatile boolean windowFocused = true;
    private boolean focusListenerAttached = false;

    GroupScapePanel(
            Runnable onOpenGroupScape,
            Client client,
            GroupScapeTrackerConfig config,
            RosterState rosterState,
            GroupSnapshotState groupSnapshotState,
            ChatState chatState,
            Consumer<String> onSendChatMessage,
            ChatMarkReadManager chatMarkReadManager,
            ItemManager itemManager,
            SkillIconManager skillIconManager,
            SpriteManager spriteManager,
            ClientThread clientThread,
            Supplier<RosterMember> localMemberSupplier,
            Supplier<GroupSnapshotMember> localSnapshotSupplier
    ) {
        super(false);
        this.client = client;
        this.config = config;
        this.clientThread = clientThread;
        this.chatMarkReadManager = chatMarkReadManager;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton openButton = new JButton("Open GroupScape");
        openButton.setPreferredSize(new Dimension(0, 30));
        openButton.addActionListener(event -> onOpenGroupScape.run());
        add(openButton, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);

        rosterTabButton = tabButton("Roster", TAB_ROSTER);
        chatTabButton = tabButton("Chat", TAB_CHAT);
        JPanel tabStrip = new JPanel(new java.awt.GridLayout(1, 2));
        tabStrip.setOpaque(false);
        tabStrip.setBorder(new EmptyBorder(4, 0, 0, 0));
        tabStrip.add(rosterTabButton);
        tabStrip.add(chatTabButton);
        body.add(tabStrip, BorderLayout.NORTH);

        rosterListPanel = new RosterListPanel(client, config, itemManager, skillIconManager, spriteManager, clientThread);
        JScrollPane rosterScrollPane = new JScrollPane(rosterListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rosterScrollPane.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        rosterScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        // BLIT_SCROLL_MODE (the default) copies existing pixels around on scroll/resize instead
        // of repainting them - a card growing when a tab opens shifts everything below it, and
        // that blit copy was leaving stale fragments (a card's old header/vitals) behind in
        // whatever now-different content scrolled into that spot. Always repainting properly
        // avoids the stale copy entirely.
        rosterScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        chatPanel = new ChatPanel(rosterState, onSendChatMessage);

        content = new JPanel(new CardLayout());
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(6, 0, 0, 0));
        content.add(rosterScrollPane, TAB_ROSTER);
        content.add(chatPanel, TAB_CHAT);
        body.add(content, BorderLayout.CENTER);

        selectTab(TAB_ROSTER);

        refreshTimer = new Timer(REFRESH_MS, e -> {
            rosterListPanel.refresh(
                    rosterState.all(), groupSnapshotState, localMemberSupplier.get(), localSnapshotSupplier.get());
            chatPanel.refresh(chatState);
            updateChatUnreadDot(chatState);
        });
        refreshTimer.start();
    }

    private void selectTab(String tab) {
        activeTab = tab;
        ((CardLayout) content.getLayout()).show(content, tab);
        highlightTab(rosterTabButton, TAB_ROSTER.equals(tab));
        highlightTab(chatTabButton, TAB_CHAT.equals(tab));
        // Dot clears on the next refresh tick via updateChatUnreadDot, which sees activeTab ==
        // TAB_CHAT - but only if the window is also focused right now, per spec §6.
    }

    /** Attaches a {@link WindowFocusListener} to the RuneLite client window the first time this
     * panel is realized - {@link java.awt.Component#addNotify()} is the standard Swing hook for
     * "now part of a window hierarchy", since no ancestor window exists yet at construction time
     * (the panel isn't added to the sidebar's toolbar until after this constructor returns). */
    @Override
    public void addNotify() {
        super.addNotify();
        if (focusListenerAttached) return;
        Window window = javax.swing.SwingUtilities.getWindowAncestor(this);
        if (window == null) return;
        focusListenerAttached = true;
        windowFocused = window.isFocused();
        window.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                windowFocused = true;
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                windowFocused = false;
            }
        });
    }

    /** Plain unread dot (no count) on the Chat tab label when it isn't read - see spec §6.
     * "Read" means the tab is both the active one and the client window has OS focus; either
     * alone leaves the dot up. When both hold and new messages arrived, advances the local cursor
     * and pushes it to the server read cursor via {@link ChatMarkReadManager}. */
    private void updateChatUnreadDot(ChatState chatState) {
        long latest = chatState.latestMessageId();
        if (lastSeenChatMessageId < 0) {
            lastSeenChatMessageId = latest; // first tick: don't flag pre-existing backfilled history as unread
            chatTabButton.setText("Chat");
            return;
        }

        boolean readNow = TAB_CHAT.equals(activeTab) && windowFocused;
        if (readNow && latest > lastSeenChatMessageId) {
            lastSeenChatMessageId = latest;
            chatMarkReadManager.markRead(latest, config);
        }

        chatTabButton.setText(latest > lastSeenChatMessageId ? "Chat ●" : "Chat");
    }

    /** Mirrors {@link #updateChatUnreadDot}'s clear from the other direction - one of this
     * account's *other* live sessions (another RuneLite client, or a webapp tab) advanced the
     * server read cursor. {@code payload.memberName} carries no account id (see the server's
     * {@code ChatReadPayload} doc comment), so this compares it against the local player's own
     * name - reading {@link Client} state off the client thread, since this runs on the
     * WebSocket's own callback thread (see {@code RosterClient}). */
    void applyChatRead(RosterWireTypes.ChatReadPayload payload) {
        clientThread.invoke(() -> {
            Player local = client.getLocalPlayer();
            String localName = local != null ? local.getName() : null;
            if (localName == null || !localName.equalsIgnoreCase(payload.memberName)) return;
            if (payload.messageId > lastSeenChatMessageId) {
                lastSeenChatMessageId = payload.messageId;
            }
        });
    }

    private JLabel tabButton(String text, String tab) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setBackground(SidePanelTheme.CARD_BG);
        label.setForeground(SidePanelTheme.MUTED);
        label.setBorder(new MatteBorder(0, 0, 2, 0, SidePanelTheme.BORDER));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setPreferredSize(new Dimension(0, 26));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectTab(tab);
            }
        });
        return label;
    }

    private void highlightTab(JLabel label, boolean active) {
        label.setForeground(active ? SidePanelTheme.ACCENT : SidePanelTheme.MUTED);
        label.setBorder(new MatteBorder(0, 0, 2, 0, active ? SidePanelTheme.ACCENT : SidePanelTheme.BORDER));
    }
}
