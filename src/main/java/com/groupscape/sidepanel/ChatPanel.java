package com.groupscape.sidepanel;

import com.groupscape.roster.ChatState;
import com.groupscape.roster.MemberMapIcons;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.FontManager;

/**
 * Group chat tab's own view inside {@code GroupScapePanel}: a scrolling message log (card rows -
 * helmet icon tinted to the member's color, name, text) plus an input box, matching the approved
 * side-panel chat mockup (spec §2/§6). Live receive comes from
 * {@code RosterClient.ChatEventListener} appending into {@link ChatState}; {@link #refresh} just
 * re-renders whatever's there now, same poll-a-shared-state pattern as {@link RosterListPanel}.
 */
public class ChatPanel extends JPanel {
    private static final int MAX_LEN = 150;
    private static final int ICON_HEIGHT_PX = 18;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final RosterState rosterState;
    private final Consumer<String> onSend;
    private final MemberMapIcons memberIcons = new MemberMapIcons();
    private final JPanel log = new JPanel();
    private final JScrollPane scrollPane;
    private final JTextField input = new JTextField();
    private final JLabel charCount = new JLabel();
    private long lastRenderedMessageId = -1;

    public ChatPanel(RosterState rosterState, Consumer<String> onSend) {
        this.rosterState = rosterState;
        this.onSend = onSend;

        setLayout(new BorderLayout());
        setBackground(SidePanelTheme.SLOT_BG);
        setBorder(new MatteBorder(1, 1, 1, 1, SidePanelTheme.BORDER));

        log.setLayout(new BoxLayout(log, BoxLayout.Y_AXIS));
        log.setOpaque(false);
        log.setBorder(new EmptyBorder(6, 6, 6, 6));

        scrollPane = new JScrollPane(log,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        add(buildInputArea(), BorderLayout.SOUTH);
    }

    private JPanel buildInputArea() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, SidePanelTheme.BORDER), new EmptyBorder(4, 6, 6, 6)));

        charCount.setFont(FontManager.getRunescapeSmallFont());
        charCount.setForeground(SidePanelTheme.MUTED_DIM);
        charCount.setHorizontalAlignment(JLabel.RIGHT);
        charCount.setText("0 / " + MAX_LEN);
        wrap.add(charCount, BorderLayout.NORTH);

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(3, 0, 0, 0));

        input.setFont(FontManager.getRunescapeSmallFont());
        input.setBackground(SidePanelTheme.CARD_BG);
        input.setForeground(SidePanelTheme.TEXT);
        input.setCaretColor(SidePanelTheme.TEXT);
        input.setBorder(new CompoundBorder(new MatteBorder(1, 1, 1, 1, SidePanelTheme.BORDER), new EmptyBorder(3, 5, 3, 5)));
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                enforceLimitAndUpdateCount();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateCount();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateCount();
            }
        });
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doSend();
                }
            }
        });
        row.add(input, BorderLayout.CENTER);

        JButton send = new JButton("Send");
        send.setFont(FontManager.getRunescapeSmallFont());
        send.setForeground(new Color(0x24, 0x13, 0x00));
        send.setBackground(SidePanelTheme.ACCENT);
        send.setFocusPainted(false);
        send.addActionListener((ActionEvent e) -> doSend());
        row.add(send, BorderLayout.EAST);

        wrap.add(row, BorderLayout.CENTER);
        return wrap;
    }

    /** Silent client-side truncation, matching the server's own truncate-not-reject behavior (spec §8). */
    private void enforceLimitAndUpdateCount() {
        SwingUtilities.invokeLater(() -> {
            String text = input.getText();
            if (text.length() > MAX_LEN) {
                input.setText(text.substring(0, MAX_LEN));
            }
            updateCount();
        });
    }

    private void updateCount() {
        charCount.setText(input.getText().length() + " / " + MAX_LEN);
    }

    private void doSend() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        onSend.accept(text);
        input.setText("");
    }

    /** Re-renders the log from {@link ChatState} - a no-op rebuild when nothing's new since last call. */
    public void refresh(ChatState chatState) {
        List<ChatState.Entry> messages = chatState.all();
        if (messages.isEmpty()) return;

        long newestId = messages.get(messages.size() - 1).messageId;
        if (newestId == lastRenderedMessageId) return;
        lastRenderedMessageId = newestId;

        boolean wasAtBottom = isScrolledToBottom();

        log.removeAll();
        for (ChatState.Entry entry : messages) {
            log.add(buildRow(entry));
            log.add(spacer());
        }
        log.revalidate();
        log.repaint();

        if (wasAtBottom) {
            SwingUtilities.invokeLater(this::scrollToBottom);
        }
    }

    private boolean isScrolledToBottom() {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        return !bar.isVisible() || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 12;
    }

    private void scrollToBottom() {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        bar.setValue(bar.getMaximum());
    }

    private JPanel buildRow(ChatState.Entry entry) {
        String displayName = entry.memberName != null ? entry.memberName : "Unknown";
        RosterMember member = rosterState.findByName(displayName);
        Color color = member != null ? SidePanelTheme.memberColor(member.color) : SidePanelTheme.ACCENT;
        String hex = member != null ? member.color : "#FF981F";

        JPanel row = new JPanel(new BorderLayout(6, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel icon = new JLabel(new ImageIcon(memberIcons.get(hex, ICON_HEIGHT_PX)));
        JPanel iconWrap = new JPanel(new BorderLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(icon, BorderLayout.NORTH);
        row.add(iconWrap, BorderLayout.WEST);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.X_AXIS));
        who.setAlignmentX(LEFT_ALIGNMENT);
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(color);
        who.add(nameLabel);
        who.add(javax.swing.Box.createHorizontalStrut(6));
        JLabel timeLabel = new JLabel(TIME_FORMAT.format(entry.createdAt));
        timeLabel.setFont(FontManager.getRunescapeSmallFont());
        timeLabel.setForeground(SidePanelTheme.MUTED_DIM);
        who.add(timeLabel);
        body.add(who);

        JLabel textLabel = new JLabel("<html><div style='width:170px'>" + escapeHtml(entry.text) + "</div></html>");
        textLabel.setFont(FontManager.getRunescapeSmallFont());
        textLabel.setForeground(SidePanelTheme.TEXT);
        textLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(textLabel);

        row.add(body, BorderLayout.CENTER);
        return row;
    }

    /** Chat text is plain-text-only and stored raw (spec §8) - this panel is the one place it's rendered, so it escapes for its own HTML-label output context here. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static JPanel spacer() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(1, 8));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        return panel;
    }
}
