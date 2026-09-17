package com.groupscape.sidepanel;

import java.awt.Color;

/**
 * Shared palette for the roster sidepanel, matching {@code PartyFrameOverlay}'s colors (and the
 * webapp's {@code --orange}) so the in-game overlay, this panel, and the website all read as the
 * same OSRS-flavoured product rather than three different UIs. Public (not just sidepanel-package
 * scoped) so {@code GroupScapePanel}'s top-level Roster/Chat tab strip, which sits a package up,
 * can match it too.
 */
public final class SidePanelTheme {
    public static final Color CARD_BG = new Color(46, 39, 24);
    public static final Color BORDER = new Color(74, 60, 38);
    public static final Color TEXT = new Color(255, 245, 220);
    public static final Color MUTED = new Color(190, 175, 150);
    public static final Color MUTED_DIM = new Color(140, 127, 104);
    public static final Color ACCENT = new Color(255, 152, 31);
    static final Color HP = new Color(198, 63, 58);
    static final Color PRAYER = new Color(58, 139, 214);
    static final Color RUN = new Color(76, 175, 80);
    static final Color SPEC = new Color(232, 197, 71);
    static final Color TRACK = new Color(255, 255, 255, 33);
    public static final Color SLOT_BG = new Color(28, 23, 15);
    static final Color OFFLINE = new Color(107, 107, 107);

    static final Color TARGET_COMBAT_FILL = new Color(164, 22, 35);
    static final Color TARGET_COMBAT_TRACK = new Color(164, 22, 35, 60);
    static final Color TARGET_NEUTRAL_FILL = new Color(140, 98, 18);
    static final Color TARGET_NEUTRAL_TRACK = new Color(140, 98, 18, 60);

    private SidePanelTheme() {
    }

    public static Color memberColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return ACCENT;
        }
    }
}
