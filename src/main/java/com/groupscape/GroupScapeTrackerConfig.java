package com.groupscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("GroupScapeTracker")
public interface GroupScapeTrackerConfig extends Config {
    @ConfigSection(
            name = "Account Config",
            description = "Enter the API key from your account page on the website here",
            position = 0
    )
    String groupSection = "GroupSection";

    @ConfigSection(
            name = "Self Hosted Config",
            description = "Configure your connection to a self hosted server",
            position = 1,
            closedByDefault = true
    )
    String connectionSection = "ConnectionSection";

    @ConfigItem(
            keyName = "apiKey",
            name = "API Key",
            description = "Your account's secret API key. Get it from your account page on the website; the plugin uses it to authenticate every request and automatically link this character.",
            secret = true,
            section = groupSection
    )
    default String apiKey() {
        return "";
    }

    @ConfigItem(
            keyName = "baseUrlOverride",
            name = "Server base URL override (leave blank to use public server)",
            description = "Overrides the public server URL used to send data. Only change this if you are hosting your own server.",
            section = connectionSection
    )
    default String baseUrlOverride() {
        return "";
    }

    @ConfigSection(
            name = "Party frame overlay",
            description = "Configure the in-game overlay showing your group's vitals",
            position = 2
    )
    String partyOverlaySection = "PartyOverlaySection";

    enum PartyOverlaySortOrder {
        JOIN_ORDER,
        JOIN_ORDER_REVERSED,
        ALPHABETICAL,
        LOWEST_HP_FIRST
    }

    @ConfigItem(
            keyName = "partyOverlayHideOverlay",
            name = "Hide overlay",
            description = "Hide the entire party overlay",
            section = partyOverlaySection,
            position = 0
    )
    default boolean partyOverlayHideOverlay() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlaySortOrder",
            name = "Sort order",
            description = "How to order members in the party overlay",
            section = partyOverlaySection,
            position = 1
    )
    default PartyOverlaySortOrder partyOverlaySortOrder() {
        return PartyOverlaySortOrder.JOIN_ORDER;
    }

    @ConfigItem(
            keyName = "partyOverlayOfflineMembersLast",
            name = "Offline members last",
            description = "Always sort offline members to the bottom, regardless of sort order",
            section = partyOverlaySection,
            position = 2
    )
    default boolean partyOverlayOfflineMembersLast() {
        return true;
    }

    @ConfigItem(
            keyName = "partyOverlayHideOfflineMembers",
            name = "Hide offline members",
            description = "Hide members instead of showing them dimmed when offline",
            section = partyOverlaySection,
            position = 3
    )
    default boolean partyOverlayHideOfflineMembers() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayFadeOutOfVicinity",
            name = "Fade members out of vicinity",
            description = "Dim members whose tile can't be confirmed within the fade distance below (requires their actor to be loaded in your client)",
            section = partyOverlaySection,
            position = 4
    )
    default boolean partyOverlayFadeOutOfVicinity() {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "partyOverlayVicinityFadeTiles",
            name = "Vicinity fade distance (tiles)",
            description = "Members farther than this many tiles away (or whose position can't be confirmed) are dimmed",
            section = partyOverlaySection,
            position = 5
    )
    default int partyOverlayVicinityFadeTiles() {
        return 50;
    }

    @ConfigItem(
            keyName = "partyOverlayHideOutOfVicinity",
            name = "Hide members out of vicinity",
            description = "Hide members entirely once they're farther than the hide distance below (requires their actor to be loaded in your client)",
            section = partyOverlaySection,
            position = 6
    )
    default boolean partyOverlayHideOutOfVicinity() {
        return false;
    }

    @Range(min = 1)
    @ConfigItem(
            keyName = "partyOverlayVicinityHideTiles",
            name = "Vicinity hide distance (tiles)",
            description = "Members farther than this many tiles away (or whose position can't be confirmed) are hidden entirely",
            section = partyOverlaySection,
            position = 7
    )
    default int partyOverlayVicinityHideTiles() {
        return 100;
    }

    @ConfigItem(
            keyName = "partyOverlayHideSelf",
            name = "Hide own row",
            description = "Don't show your own character in the party overlay",
            section = partyOverlaySection,
            position = 8
    )
    default boolean partyOverlayHideSelf() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideWorld",
            name = "Hide world",
            description = "Hide the world number next to each member's name",
            section = partyOverlaySection,
            position = 9
    )
    default boolean partyOverlayHideWorld() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideHp",
            name = "Hide HP bar",
            description = "Hide the hitpoints bar",
            section = partyOverlaySection,
            position = 10
    )
    default boolean partyOverlayHideHp() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHidePrayer",
            name = "Hide prayer bar",
            description = "Hide the prayer bar and active prayer icons",
            section = partyOverlaySection,
            position = 11
    )
    default boolean partyOverlayHidePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHidePrayerIcons",
            name = "Hide active prayer icons",
            description = "Hide the active-prayer icon row, keeping the prayer bar itself visible",
            section = partyOverlaySection,
            position = 12
    )
    default boolean partyOverlayHidePrayerIcons() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideRun",
            name = "Hide run energy bar",
            description = "Hide the run energy bar",
            section = partyOverlaySection,
            position = 13
    )
    default boolean partyOverlayHideRun() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideSpec",
            name = "Hide special attack bar",
            description = "Hide the special attack bar",
            section = partyOverlaySection,
            position = 14
    )
    default boolean partyOverlayHideSpec() {
        return false;
    }

    @ConfigItem(
            keyName = "partyOverlayHideTarget",
            name = "Hide target bar",
            description = "Hide the target HP bar (combat target, bank, or NPC being talked to)",
            section = partyOverlaySection,
            position = 15
    )
    default boolean partyOverlayHideTarget() {
        return false;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "partyOverlayOpacity",
            name = "Overlay opacity",
            description = "Background opacity of the party overlay panel, from 0 (transparent) to 100 (solid)",
            section = partyOverlaySection,
            position = 16
    )
    default int partyOverlayOpacity() {
        return 85;
    }

    enum PartyOverlayScale {
        NORMAL,
        COMPACT,
        SUPER_COMPACT,
        MINIMAL,
        ORB_GRID,
        SCOREBOARD
    }

    // Renamed from "Scale" to "Layout" since every tier past Compact/Super Compact picks a
    // different visual arrangement, not just a size - keyName is unchanged so no one's saved
    // choice resets.
    @ConfigItem(
            keyName = "partyOverlayScale",
            name = "Layout",
            description = "Compact/Super compact tighten row/bar spacing; Minimal fuses HP/Prayer/Run/Spec into one bar strip; Orb grid tiles members as HP orbs; Scoreboard lays them out as side-by-side vertical HP meters",
            section = partyOverlaySection,
            position = 17
    )
    default PartyOverlayScale partyOverlayScale() {
        return PartyOverlayScale.NORMAL;
    }

    @Range(min = 0)
    @ConfigItem(
            keyName = "partyOverlayMaxMembers",
            name = "Max members shown",
            description = "Truncate the overlay to this many members (0 = unlimited)",
            section = partyOverlaySection,
            position = 18
    )
    default int partyOverlayMaxMembers() {
        return 5;
    }

    @ConfigSection(
            name = "Tile highlight",
            description = "Outline group members' tiles when they're visible in your game client",
            position = 3
    )
    String tileHighlightSection = "TileHighlightSection";

    @ConfigItem(
            keyName = "tileHighlightEnabled",
            name = "Enable tile highlight",
            description = "Outline a group member's tile whenever they're rendered on your screen",
            section = tileHighlightSection,
            position = 0
    )
    default boolean tileHighlightEnabled() {
        return true;
    }

    @Range(min = 1, max = 8)
    @ConfigItem(
            keyName = "tileHighlightStrokeWidth",
            name = "Stroke width",
            description = "Thickness of the tile outline",
            section = tileHighlightSection,
            position = 1
    )
    default int tileHighlightStrokeWidth() {
        return 2;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "tileHighlightOpacity",
            name = "Opacity",
            description = "Outline opacity, from 0 (transparent) to 100 (solid)",
            section = tileHighlightSection,
            position = 2
    )
    default int tileHighlightOpacity() {
        return 100;
    }

    @ConfigSection(
            name = "Map markers",
            description = "Show group members' positions on the world map and minimap",
            position = 5
    )
    String mapMarkersSection = "MapMarkersSection";

    @ConfigItem(
            keyName = "mapMarkersEnabled",
            name = "Enable map markers",
            description = "Show a group member's marker on the world map and minimap while they're in your world and on your plane",
            section = mapMarkersSection,
            position = 0
    )
    default boolean mapMarkersEnabled() {
        return true;
    }

    @ConfigSection(
            name = "Pings",
            description = "Ping a tile or NPC for your group to see, in-game and on the website map",
            position = 7
    )
    String pingSection = "PingSection";

    @ConfigItem(
            keyName = "pingsEnabled",
            name = "Enable pings",
            description = "Show the right-click \"Ping\" menu entries and other group members' ping markers",
            section = pingSection,
            position = 0
    )
    default boolean pingsEnabled() {
        return true;
    }

    @ConfigSection(
            name = "Raid Markers",
            description = "Drop persistent raid callout markers (Danger/Defend/Loot/Focus, or a 1-4/A-D ping) on a tile or NPC for your group to see",
            position = 8
    )
    String raidMarkerSection = "RaidMarkerSection";

    @ConfigItem(
            keyName = "raidMarkersEnabled",
            name = "Enable raid markers",
            description = "Show the right-click \"Raid Markers\" submenu and other group members' raid marker icons",
            section = raidMarkerSection,
            position = 0
    )
    default boolean raidMarkersEnabled() {
        return true;
    }

    @ConfigSection(
            name = "Raid Marker Types",
            description = "Toggle which raid marker types appear in your own \"Raid Markers\" menu - unchecking a type only removes it from your menu, it doesn't hide other members' markers of that type",
            position = 9
    )
    String raidMarkerTypesSection = "RaidMarkerTypesSection";

    @ConfigItem(
            keyName = "showMenuDanger",
            name = "Danger",
            description = "Show \"Danger\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 0
    )
    default boolean showMenuDanger() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuDefend",
            name = "Defend",
            description = "Show \"Defend\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 1
    )
    default boolean showMenuDefend() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuLoot",
            name = "Loot",
            description = "Show \"Loot\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 2
    )
    default boolean showMenuLoot() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuFocus",
            name = "Focus / Kill Target",
            description = "Show \"Focus / Kill Target\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 3
    )
    default boolean showMenuFocus() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuA",
            name = "A",
            description = "Show \"A\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 4
    )
    default boolean showMenuA() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuB",
            name = "B",
            description = "Show \"B\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 5
    )
    default boolean showMenuB() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuC",
            name = "C",
            description = "Show \"C\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 6
    )
    default boolean showMenuC() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuD",
            name = "D",
            description = "Show \"D\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 7
    )
    default boolean showMenuD() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuOne",
            name = "1",
            description = "Show \"1\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 8
    )
    default boolean showMenuOne() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuTwo",
            name = "2",
            description = "Show \"2\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 9
    )
    default boolean showMenuTwo() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuThree",
            name = "3",
            description = "Show \"3\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 10
    )
    default boolean showMenuThree() {
        return true;
    }

    @ConfigItem(
            keyName = "showMenuFour",
            name = "4",
            description = "Show \"4\" in your Raid Markers menu",
            section = raidMarkerTypesSection,
            position = 11
    )
    default boolean showMenuFour() {
        return true;
    }

    @ConfigSection(
            name = "Sidepanel roster",
            description = "Configure the group roster shown in the GroupScape sidepanel",
            position = 4
    )
    String sidepanelSection = "SidepanelSection";

    enum SidepanelSortOrder {
        JOIN_ORDER,
        JOIN_ORDER_REVERSED,
        ALPHABETICAL,
        LOWEST_HP_FIRST
    }

    @ConfigItem(
            keyName = "sidepanelHideSelf",
            name = "Hide own row",
            description = "Don't show your own character in the sidepanel roster",
            section = sidepanelSection,
            position = 0
    )
    default boolean sidepanelHideSelf() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHideOfflineMembers",
            name = "Hide offline members",
            description = "Hide members instead of showing them dimmed when offline",
            section = sidepanelSection,
            position = 1
    )
    default boolean sidepanelHideOfflineMembers() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelOfflineMembersLast",
            name = "Offline members last",
            description = "Always sort offline members to the bottom, regardless of sort order",
            section = sidepanelSection,
            position = 2
    )
    default boolean sidepanelOfflineMembersLast() {
        return true;
    }

    @ConfigItem(
            keyName = "sidepanelSortOrder",
            name = "Sort order",
            description = "How to order members in the sidepanel roster",
            section = sidepanelSection,
            position = 3
    )
    default SidepanelSortOrder sidepanelSortOrder() {
        return SidepanelSortOrder.JOIN_ORDER;
    }

    @Range(min = 0)
    @ConfigItem(
            keyName = "sidepanelMaxMembers",
            name = "Max members shown",
            description = "Truncate the sidepanel roster to this many members (0 = unlimited)",
            section = sidepanelSection,
            position = 4
    )
    default int sidepanelMaxMembers() {
        return 0;
    }

    @ConfigItem(
            keyName = "sidepanelHideWorld",
            name = "Hide world",
            description = "Hide the world number next to each member's name",
            section = sidepanelSection,
            position = 5
    )
    default boolean sidepanelHideWorld() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHideHp",
            name = "Hide HP bar",
            description = "Hide the hitpoints bar",
            section = sidepanelSection,
            position = 6
    )
    default boolean sidepanelHideHp() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHidePrayer",
            name = "Hide prayer bar",
            description = "Hide the prayer bar",
            section = sidepanelSection,
            position = 7
    )
    default boolean sidepanelHidePrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHidePrayerIcons",
            name = "Hide active prayer icons",
            description = "Hide the active-prayer icon row, keeping the prayer bar itself visible",
            section = sidepanelSection,
            position = 8
    )
    default boolean sidepanelHidePrayerIcons() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHideRun",
            name = "Hide run energy bar",
            description = "Hide the run energy bar",
            section = sidepanelSection,
            position = 9
    )
    default boolean sidepanelHideRun() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHideSpec",
            name = "Hide special attack bar",
            description = "Hide the special attack bar",
            section = sidepanelSection,
            position = 10
    )
    default boolean sidepanelHideSpec() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelHideTarget",
            name = "Hide target bar",
            description = "Hide the target HP bar (combat target, bank, or NPC being talked to)",
            section = sidepanelSection,
            position = 11
    )
    default boolean sidepanelHideTarget() {
        return false;
    }

    @ConfigItem(
            keyName = "sidepanelShowInventoryTab",
            name = "Show Bag tab",
            description = "Show the inventory (Bag) expand button on each member's card",
            section = sidepanelSection,
            position = 12
    )
    default boolean sidepanelShowInventoryTab() {
        return true;
    }

    @ConfigItem(
            keyName = "sidepanelShowEquipmentTab",
            name = "Show Gear tab",
            description = "Show the equipment (Gear) expand button on each member's card",
            section = sidepanelSection,
            position = 13
    )
    default boolean sidepanelShowEquipmentTab() {
        return true;
    }

    @ConfigItem(
            keyName = "sidepanelShowSkillsTab",
            name = "Show Stats tab",
            description = "Show the skills (Stats) expand button on each member's card",
            section = sidepanelSection,
            position = 14
    )
    default boolean sidepanelShowSkillsTab() {
        return true;
    }

    @ConfigSection(
            name = "Notifications",
            description = "Chat notifications for group events",
            position = 6
    )
    String notificationsSection = "NotificationsSection";

    @ConfigItem(
            keyName = "notifyMemberOnline",
            name = "Member came online",
            description = "Send a chat message when a group member comes online",
            section = notificationsSection
    )
    default boolean notifyMemberOnline() {
        return true;
    }

    @ConfigItem(
            keyName = "notifyMemberOffline",
            name = "Member went offline",
            description = "Send a chat message when a group member goes offline",
            section = notificationsSection
    )
    default boolean notifyMemberOffline() {
        return true;
    }

    @ConfigItem(
            keyName = "notifyLowHp",
            name = "Low HP warning",
            description = "Send a chat message when a group member's HP drops below the threshold below",
            section = notificationsSection
    )
    default boolean notifyLowHp() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "lowHpNotifyThreshold",
            name = "Low HP threshold %",
            description = "HP percentage that triggers the low HP warning",
            section = notificationsSection
    )
    default int lowHpNotifyThreshold() {
        return 25;
    }

    @ConfigItem(
            keyName = "notifyBossKill",
            name = "Boss kill",
            description = "Send a chat message when a group member kills a tracked boss",
            section = notificationsSection
    )
    default boolean notifyBossKill() {
        return true;
    }

    @ConfigItem(
            keyName = "notifyNotableDrop",
            name = "Notable drop chat message",
            description = "Send a chat message when a group member gets a drop worth more than the threshold below",
            section = notificationsSection
    )
    default boolean notifyNotableDrop() {
        return true;
    }

    @Range(min = 0)
    @ConfigItem(
            keyName = "notableDropThreshold",
            name = "Notable drop value threshold",
            description = "Total GE value a drop must reach to trigger the notable drop notification",
            section = notificationsSection
    )
    default int notableDropThreshold() {
        return 50000;
    }

    @ConfigSection(
            name = "Chat",
            description = "Group chat (!gs) settings",
            position = 10
    )
    String chatSection = "ChatSection";

    @ConfigItem(
            keyName = "chatEnabled",
            name = "Enable !gs chat",
            description = "Capture !gs-prefixed Public/Clan chat and relay it to your group instead of sending it as normal chat. Only works for group members who also have this on - if someone in your group doesn't, their !gs messages still go out as real public/clan chat.",
            section = chatSection
    )
    default boolean chatEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "chatInlineEnabled",
            name = "Show chat in Game/All tab",
            description = "Print group chat messages inline in the Game/All chat tab, tagged [GS]",
            section = chatSection
    )
    default boolean chatInlineEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "chatOverlayEnabled",
            name = "Show floating chat overlay",
            description = "Show an always-on-top, draggable group chat window over the game viewport, independent of the chatbox and sidepanel",
            section = chatSection
    )
    default boolean chatOverlayEnabled() {
        return false;
    }
}
