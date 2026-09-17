package com.groupscape;

import com.groupscape.roster.ChatState;
import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.GroupSnapshotState;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterState;
import com.groupscape.sidepanel.ChatPanel;
import com.groupscape.sidepanel.RosterListPanel;
import com.groupscape.sidepanel.SidePanelTheme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
 */
class GroupScapePanel extends PluginPanel {
    private static final int REFRESH_MS = 600;
    private static final String TAB_ROSTER = "roster";
    private static final String TAB_CHAT = "chat";

    private final RosterListPanel rosterListPanel;
    private final ChatPanel chatPanel;
    private final Timer refreshTimer;
    private final JLabel rosterTabButton;
    private final JLabel chatTabButton;
    private final JPanel content;

    private String activeTab = TAB_ROSTER;
    private long lastSeenChatMessageId = -1;

    GroupScapePanel(
            Runnable onOpenGroupScape,
            Client client,
            GroupScapeTrackerConfig config,
            RosterState rosterState,
            GroupSnapshotState groupSnapshotState,
            ChatState chatState,
            Consumer<String> onSendChatMessage,
            ItemManager itemManager,
            SkillIconManager skillIconManager,
            SpriteManager spriteManager,
            ClientThread clientThread,
            Supplier<RosterMember> localMemberSupplier,
            Supplier<GroupSnapshotMember> localSnapshotSupplier
    ) {
        super(false);

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
        // Dot clears on the next refresh tick via updateChatUnreadDot, which sees activeTab == TAB_CHAT.
    }

    /** Plain unread dot (no count) on the Chat tab label when it isn't the active tab - see spec §6. */
    private void updateChatUnreadDot(ChatState chatState) {
        long latest = chatState.latestMessageId();
        if (TAB_CHAT.equals(activeTab)) {
            lastSeenChatMessageId = latest;
            chatTabButton.setText("Chat");
            return;
        }
        if (lastSeenChatMessageId < 0) {
            lastSeenChatMessageId = latest; // first tick: don't flag pre-existing backfilled history as unread
            return;
        }
        chatTabButton.setText(latest > lastSeenChatMessageId ? "Chat ●" : "Chat");
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
