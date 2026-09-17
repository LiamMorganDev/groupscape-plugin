package com.groupscape.sidepanel;

import com.groupscape.GroupScapeTrackerConfig;
import com.groupscape.roster.ChatState;
import com.groupscape.roster.RosterState;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;

/**
 * The floating chat surface (spec §2's third, opt-in surface) - an always-on-top, draggable
 * window over the game viewport, independent of the chatbox and sidepanel. Reuses {@link
 * ChatPanel} verbatim for the message log/input box, same as {@code GroupScapePanel}'s Chat tab,
 * so the two surfaces stay visually and behaviorally identical.
 *
 * <p>Unlike every other overlay in this plugin (all painted {@code Graphics2D} via RuneLite's
 * {@code OverlayManager}, which owns drag handling itself), this is a genuine {@link JWindow} -
 * there's no way to host an interactive text input inside a painted overlay. Dragging and
 * visibility are handled here directly rather than through {@code OverlayManager}, and the
 * dragged position is persisted via raw {@link ConfigManager} keys rather than a declared {@code
 * @ConfigItem}, since it's not something a user sets from the config panel.
 *
 * <p>Self-contained refresh, matching {@code GroupScapePanel}'s pattern: a {@link Timer} polls
 * {@code chatOverlayEnabled} to show/hide the window and, while visible, re-renders from {@link
 * ChatState}.
 */
public class ChatOverlayWindow extends JWindow {
    private static final int REFRESH_MS = 600;
    private static final String POSITION_X_KEY = "chatOverlayWindowX";
    private static final String POSITION_Y_KEY = "chatOverlayWindowY";
    private static final int DEFAULT_X = 200;
    private static final int DEFAULT_Y = 200;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 380;

    private final GroupScapeTrackerConfig config;
    private final ConfigManager configManager;
    private final ChatState chatState;
    private final ChatPanel chatPanel;
    private final Timer timer;
    private Point dragOrigin;
    private boolean shown = false;

    public ChatOverlayWindow(
            RosterState rosterState,
            ChatState chatState,
            Consumer<String> onSend,
            GroupScapeTrackerConfig config,
            ConfigManager configManager
    ) {
        this.config = config;
        this.configManager = configManager;
        this.chatState = chatState;

        setAlwaysOnTop(true);
        setSize(WIDTH, HEIGHT);
        setLocation(loadPosition());

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new MatteBorder(1, 1, 1, 1, SidePanelTheme.BORDER));
        root.add(buildHeader(), BorderLayout.NORTH);

        chatPanel = new ChatPanel(rosterState, onSend);
        root.add(chatPanel, BorderLayout.CENTER);
        setContentPane(root);

        timer = new Timer(REFRESH_MS, e -> tick());
        timer.start();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SidePanelTheme.CARD_BG);
        header.setBorder(new EmptyBorder(4, 8, 4, 4));

        JLabel title = new JLabel("GroupScape Chat");
        title.setFont(FontManager.getRunescapeSmallFont());
        title.setForeground(SidePanelTheme.MUTED);
        header.add(title, BorderLayout.WEST);

        JButton close = new JButton("x");
        close.setFont(FontManager.getRunescapeSmallFont());
        close.setForeground(SidePanelTheme.MUTED);
        close.setBackground(SidePanelTheme.CARD_BG);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setHorizontalAlignment(SwingConstants.CENTER);
        close.addActionListener(e -> configManager.setConfiguration("GroupScapeTracker", "chatOverlayEnabled", false));
        header.add(close, BorderLayout.EAST);

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOrigin = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOrigin = null;
                savePosition();
            }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin == null) return;
                Point current = getLocation();
                setLocation(current.x + e.getX() - dragOrigin.x, current.y + e.getY() - dragOrigin.y);
            }
        });

        return header;
    }

    private void tick() {
        boolean enabled = config.chatOverlayEnabled();
        if (enabled != shown) {
            shown = enabled;
            setVisible(enabled);
        }
        if (enabled) {
            chatPanel.refresh(chatState);
        }
    }

    private Point loadPosition() {
        Integer x = parseOrNull(configManager.getConfiguration("GroupScapeTracker", POSITION_X_KEY));
        Integer y = parseOrNull(configManager.getConfiguration("GroupScapeTracker", POSITION_Y_KEY));
        return new Point(x != null ? x : DEFAULT_X, y != null ? y : DEFAULT_Y);
    }

    private void savePosition() {
        Point location = getLocation();
        configManager.setConfiguration("GroupScapeTracker", POSITION_X_KEY, location.x);
        configManager.setConfiguration("GroupScapeTracker", POSITION_Y_KEY, location.y);
    }

    private static Integer parseOrNull(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void shutdown() {
        timer.stop();
        dispose();
    }
}
