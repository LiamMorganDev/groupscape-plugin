package com.groupscape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.groupscape.roster.ChatBackfillClient;
import com.groupscape.roster.ChatState;
import com.groupscape.roster.GroupSnapshotClient;
import com.groupscape.roster.GroupSnapshotMember;
import com.groupscape.roster.GroupSnapshotState;
import com.groupscape.roster.GroupWorldMapPoints;
import com.groupscape.roster.LocalRosterMemberFactory;
import com.groupscape.roster.MemberMapIcons;
import com.groupscape.roster.MinimapLocationOverlay;
import com.groupscape.roster.PartyFrameOverlay;
import com.groupscape.roster.PingArrowIcons;
import com.groupscape.roster.PingManager;
import com.groupscape.roster.PingMinimapOverlay;
import com.groupscape.roster.PingState;
import com.groupscape.roster.PingViewportOverlay;
import com.groupscape.roster.PingWorldMapPoints;
import com.groupscape.roster.RaidMarkerIcons;
import com.groupscape.roster.RaidMarkerManager;
import com.groupscape.roster.RaidMarkerMinimapOverlay;
import com.groupscape.roster.RaidMarkerState;
import com.groupscape.roster.RaidMarkerType;
import com.groupscape.roster.RaidMarkerViewportOverlay;
import com.groupscape.roster.RaidMarkerWorldMapPoints;
import com.groupscape.roster.RosterClient;
import com.groupscape.roster.RosterMember;
import com.groupscape.roster.RosterNotifier;
import com.groupscape.roster.RosterState;
import com.groupscape.roster.TileHighlightOverlay;
import com.groupscape.roster.WorldMapCoordinates;
import com.groupscape.sidepanel.LocalGroupSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = "GroupScape"
)
public class GroupScapeTrackerPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private DataManager dataManager;
    @Inject
    private ItemManager itemManager;
    @Inject
    private net.runelite.client.game.SpriteManager spriteManager;
    @Inject
    private net.runelite.client.game.SkillIconManager skillIconManager;
    @Inject
    private CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;
    @Inject
    private ChatSuppressionSubscriber chatSuppressionSubscriber;
    @Inject
    private ChatSendManager chatSendManager;
    @Inject
    private ChatMarkReadManager chatMarkReadManager;
    @Inject
    private PortraitCaptureManager portraitCaptureManager;
    @Inject
    private GroupScapeTrackerConfig config;
    @Inject
    private HttpRequestService httpRequestService;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager worldMapPointManager;
    @Inject
    private net.runelite.client.ui.overlay.tooltip.TooltipManager tooltipManager;
    @Inject
    private OkHttpClient okHttpClient;
    @Inject
    private Gson gson;
    @Inject
    private ChatMessageManager chatMessageManager;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private ConfigManager configManager;
    private NavigationButton navigationButton;
    private com.groupscape.sidepanel.ChatOverlayWindow chatOverlayWindow;
    private RosterState rosterState;
    private RosterClient rosterClient;
    private RosterNotifier rosterNotifier;
    private GroupSnapshotState groupSnapshotState;
    private GroupSnapshotClient groupSnapshotClient;
    private ChatState chatState;
    private ChatBackfillClient chatBackfillClient;
    private PartyFrameOverlay partyFrameOverlay;
    private TileHighlightOverlay tileHighlightOverlay;
    private MinimapLocationOverlay minimapLocationOverlay;
    private GroupWorldMapPoints groupWorldMapPoints;
    private final MemberMapIcons memberMapIcons = new MemberMapIcons();
    private PingState pingState;
    private PingManager pingManager;
    private PingViewportOverlay pingViewportOverlay;
    private PingMinimapOverlay pingMinimapOverlay;
    private PingWorldMapPoints pingWorldMapPoints;
    private final PingArrowIcons pingArrowIcons = new PingArrowIcons();
    private RaidMarkerState raidMarkerState;
    private RaidMarkerManager raidMarkerManager;
    private RaidMarkerViewportOverlay raidMarkerViewportOverlay;
    private RaidMarkerMinimapOverlay raidMarkerMinimapOverlay;
    private RaidMarkerWorldMapPoints raidMarkerWorldMapPoints;
    private final RaidMarkerIcons raidMarkerIcons = new RaidMarkerIcons();
    /**
     * The sidepanel's own "you" row, rebuilt on the client thread each
     * {@link #updateThingsThatDoChangeOften} tick and read from the Swing EDT via
     * {@link GroupScapePanel}'s refresh timer. RuneLite's {@link Client} is not safe to call from
     * the EDT directly (unlike {@link PartyFrameOverlay}, which reads it during client-thread
     * rendering) - these two fields are the hand-off point.
     */
    private volatile RosterMember localMember;
    private volatile GroupSnapshotMember localSnapshot;
    private int itemsDeposited = 0;
    /** Number of ticks {@link #onActorDeath} waits before trusting the hitpoints stat; see its javadoc. */
    private static final int DEATH_CONFIRM_TICKS = 2;
    private PendingDeathCandidate pendingDeathCandidate;
    private int pendingDeathConfirmTicks;
    private boolean dialogueEventEmitted = false;
    /**
     * Last NPC name shown in an open dialogue box (left-hand "chathead speaking" widget), kept
     * updated for as long as that widget stays visible - see
     * {@link #captureSlayerTaskMasterDialogue}. Unlike {@link NpcDialogueTracker} (which also
     * updates on plain combat interaction, not just dialogue) this is only ever set while an
     * actual NPC dialogue box is on screen, so it's a reliable source for "who was I just talking
     * to" at the exact moment a slayer task gets assigned.
     */
    private String lastDialogNpcName;
    /** Snapshotted from {@link #lastDialogNpcName} whenever a new slayer task is detected; see
     * {@link #checkSlayerTaskUpdate}. Persists across ticks until the next task change - there is
     * no varp/varbit that names the assigning master directly. */
    private String currentSlayerTaskMaster;
    /** Whichever slayer master's Rewards shop is currently open, snapshotted from
     * {@link #lastDialogNpcName} the moment the shop widget appears - see
     * {@link #captureSlayerRewardShopMaster}. The shop interface isn't itself a dialogue box, so
     * {@link #lastDialogNpcName} stops updating once it's open; this is the only way to attribute
     * a Reward Shop purchase made inside it (e.g. blocking a task type) to the right master, since
     * that master isn't necessarily whoever assigned the player's current task. */
    private String currentRewardShopMaster;
    /** Identity of the task currently being tracked for {@link SlayerTaskCloseEvents} (History/
     * Stats tabs) - `null` when no task is being tracked, i.e. either there's no active task or
     * the active one's master/task name hasn't resolved yet (see {@link #handleSlayerTaskTransition}).
     * Deliberately independent of {@code SlayerTaskState}'s own equality/dedup bookkeeping so a
     * task whose name only resolves a few ticks late (see {@link SlayerTaskState}'s javadoc)
     * still gets exactly one assignment event once it does, not zero. */
    private String currentSlayerTaskEventId;
    private int currentSlayerTaskId = -1;
    private String currentSlayerTaskAssignedAt;
    /** {@code SLAYER_POINTS} as of this task's assignment - the baseline {@link #closeSlayerTask}
     * diffs the final points reading against. Not {@code closingSnapshot.points()} (the last
     * push before the transition), because a completed task's points land the moment the final
     * kill lands - well before the master-dialogue transition that actually closes this task out
     * - so by then a push has already carried the reward into every state up to and including
     * {@code closingSnapshot}, making that comparison read a false 0 delta. */
    private int currentSlayerTaskPointsAtAssignment;
    /** Last confirmed {@link SlayerTaskState} for whichever task {@link #currentSlayerTaskEventId}
     * is tracking, refreshed on every {@link #handleSlayerTaskTransition} call where the task
     * identity hasn't changed. {@link #closeSlayerTask} reads from this instead of the `previous`
     * argument (last state handed to that method call, sourced from {@code DataState}'s own
     * bookkeeping) - the two are normally identical, but keeping a dedicated copy means a close
     * event still carries the real final kill count even on a call where `previous` doesn't line
     * up with {@link #currentSlayerTaskId} for some unrelated reason, rather than silently
     * dropping tracking and losing the close event outright (see the History tab showing a task
     * stuck "in progress" at 0 kills despite being finished in-game - a lost close event is the
     * only way that row was ever written that way). */
    private ClosingTaskSnapshot currentSlayerTaskLastSnapshot;
    /** Set by {@link #handleSlayerRewardShopConfirm} right after a block purchase is confirmed,
     * consumed by the very next {@link #handleSlayerTaskTransition} call that would otherwise
     * refresh {@link #currentSlayerTaskLastSnapshot}. Blocking your current task un-assigns it, but
     * the resulting {@code SLAYER_TARGET}/{@code SLAYER_COUNT} varp writes aren't guaranteed to
     * land in the same client tick - if {@code SLAYER_COUNT} resets ahead of {@code SLAYER_TARGET}
     * clearing, the intervening push would otherwise overwrite the snapshot with a bogus
     * amountRemaining of 0 (still the same, not-yet-cleared taskId) moments before the real close,
     * making a task blocked after only a few kills read back as "completed" at max kills once
     * {@link #closeSlayerTask} runs. Skipping just the one push this flags lets the transition/close
     * that follows fall back to the last snapshot from *before* the block purchase instead. */
    private boolean suppressNextSlayerTaskSnapshotRefresh = false;
    /** Task name (as matched from the Rewards Shop confirm text) and points cost of a block
     * purchase just confirmed for the *currently tracked* task, set by
     * {@link #handleSlayerRewardShopConfirm} and consumed by the very next {@link #closeSlayerTask}
     * call for that same task. Exists because {@link #suppressNextSlayerTaskSnapshotRefresh} only
     * protects {@code amountRemaining} from one corrupted push - it does nothing if the
     * {@code SLAYER_POINTS} varp write also hasn't landed by the time {@link #closeSlayerTask} reads
     * {@code afterClose.points()}, which still lets the amountRemaining==0 branch fall through to
     * "completed" (pointsDelta reading 0 instead of the real {@code -blockPrice}) - exactly how a
     * Hellhounds task blocked 23 seconds in got recorded as completing all 164 kills. Since the
     * confirm click is unambiguous proof this task was blocked, {@link #closeSlayerTask} trusts this
     * flag over its points-delta heuristic whenever it's set for the task being closed. */
    private String pendingCurrentTaskBlockName;
    private int pendingCurrentTaskBlockPrice;
    /** Guards {@link #restorePendingSlayerTaskCloseIfNeeded} to run at most once per plugin
     * session - it only has anything to do the first time a slayer-task push happens after
     * startup, since after that {@link #currentSlayerTaskEventId} is the source of truth again. */
    private boolean slayerTaskRestoreAttempted = false;
    private static final String SLAYER_TRACKER_CONFIG_GROUP = "GroupScapeTracker";
    /** Config key {@link #persistPendingSlayerTaskClose}/{@link #restorePendingSlayerTaskCloseIfNeeded}
     * use to survive a plugin/client restart mid-task - without this, a restart between a task
     * finishing (or being skipped/cancelled/blocked) and the plugin's own in-memory tracking
     * ({@link #currentSlayerTaskEventId} et al, reset to null on every plugin load) sending its
     * close event would strand that row open forever, later force-closed server-side as a generic
     * "superseded" guess instead of the real completed/reset/cancelled/blocked outcome. */
    private static final String SLAYER_PENDING_CLOSE_CONFIG_KEY = "pendingSlayerTaskClose";

    /** Minimal stand-in for {@link SlayerTaskState} carrying only what {@link #closeSlayerTask}
     * needs from the task being closed - decoupled from the full varp/varbit-reading class so a
     * snapshot restored from {@link #SLAYER_PENDING_CLOSE_CONFIG_KEY} (plain data, no {@link
     * net.runelite.api.Client} to read from) can stand in for a live one interchangeably. */
    private static final class ClosingTaskSnapshot {
        final String masterName;
        final String taskName;
        final int amountRemaining;
        final int initialAmount;

        ClosingTaskSnapshot(String masterName, String taskName, int amountRemaining, int initialAmount) {
            this.masterName = masterName;
            this.taskName = taskName;
            this.amountRemaining = amountRemaining;
            this.initialAmount = initialAmount;
        }

        static ClosingTaskSnapshot from(SlayerTaskState state) {
            return new ClosingTaskSnapshot(state.masterName(), state.taskName(), state.amountRemaining(), state.initialAmount());
        }
    }
    /** Guards {@link #checkSlayerTaskUpdate}'s {@code clientThread.invokeLater} against queuing
     * one redundant callback per underlying varp/varbit - a task assignment/turn-in flips several
     * of {@link #SLAYER_TASK_VARPS}/{@link #SLAYER_TASK_VARBITS} in the same tick, and RuneLite
     * fires a separate {@code VarbitChanged} for each, so without this a single transition was
     * re-running the DB-table lookups in {@link SlayerTaskState} and a full panel re-render
     * several times over - a noticeable hitch right at task start/finish. */
    private boolean slayerTaskPushPending = false;
    private boolean cachePotions = false;
    private Set<Integer> potionStoreVars;
    private boolean lowHpAlertArmed = true;
    private boolean linkRequiredWarningShown = false;
    private boolean wasInWilderness = false;
    /**
     * NPC index -> last-known name, refreshed on every sighting. {@code npc.getName()} can
     * return {@code null} by the time {@link NpcDespawned} fires (composition data already
     * cleared), which previously made {@link #onNpcDespawned} silently drop otherwise-trackable
     * kills with a {@code null} name. Trimmed on despawn so this doesn't grow unbounded.
     */
    private final Map<Integer, String> npcNamesByIndex = new HashMap<>();
    /**
     * NPC index -> last-known non-negative health ratio, refreshed on every hitsplat. The health
     * bar (and so {@code npc.getHealthRatio()}) frequently reverts to -1 before
     * {@link NpcDespawned} actually fires - e.g. a boss's death animation outlasting the health
     * bar's own hide timer - which made the live ratio check at despawn miss most boss kills.
     * Recording the ratio as damage lands lets despawn fall back to "did this NPC ever hit 0"
     * instead of "is it at 0 right now". Trimmed on despawn so this doesn't grow unbounded.
     */
    private final Map<Integer, Integer> npcHealthRatioByIndex = new HashMap<>();
    /**
     * Name of whichever NPC last landed a damaging hitsplat on the local player - see
     * {@link #recordHitsplatAttacker}. Used for death-event killer attribution instead of
     * {@code local.getInteracting()}, which reflects who the player is targeting, not who's
     * attacking them (wrong for ranged/AoE boss specials the player never targeted back).
     */
    private String lastHitsplatAttackerName;
    private static final double LOW_HP_ALERT_THRESHOLD = 0.25;
    private static final double LOW_HP_REARM_THRESHOLD = 0.5;
    private static final int SECONDS_BETWEEN_UPLOADS = 1;
    private static final int SECONDS_BETWEEN_GROUP_SNAPSHOT_POLL = 5;
    private static final int SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES = 60;
    private static final int SECONDS_BETWEEN_PORTRAIT_BACKSTOP = 300;
    private static final int DEPOSIT_ITEM = 12582914;
    private static final int DEPOSIT_INVENTORY = 12582916;
    private static final int DEPOSIT_EQUIPMENT = 12582918;
    private static final int CHATBOX_ENTERED = 681;
    private static final int GROUP_STORAGE_LOADER = 293;

    private static final String DEFAULTS_MIGRATION_1_8_14_KEY = "defaultsMigration_1_8_14";

    private void migrateDefaults_1_8_14()
    {
        if (configManager.getConfiguration("GroupScapeTracker", DEFAULTS_MIGRATION_1_8_14_KEY) != null)
        {
            return;
        }

        configManager.setConfiguration("GroupScapeTracker", "partyOverlayOfflineMembersLast", true);
        configManager.setConfiguration("GroupScapeTracker", "partyOverlayFadeOutOfVicinity", false);
        configManager.setConfiguration("GroupScapeTracker", "partyOverlayHideOutOfVicinity", false);
        configManager.setConfiguration("GroupScapeTracker", "raidMarkersEnabled", true);
        configManager.setConfiguration("GroupScapeTracker", "notifyMemberOnline", true);
        configManager.setConfiguration("GroupScapeTracker", "notifyMemberOffline", true);
        configManager.setConfiguration("GroupScapeTracker", "notifyLowHp", true);
        configManager.setConfiguration("GroupScapeTracker", "notifyBossKill", true);

        configManager.setConfiguration("GroupScapeTracker", DEFAULTS_MIGRATION_1_8_14_KEY, true);
    }

    @Override
    protected void startUp() throws Exception {
        migrateDefaults_1_8_14();
        collectionLogWidgetSubscriber.startUp();
        chatSuppressionSubscriber.startUp();

        rosterState = new RosterState();
        groupSnapshotState = new GroupSnapshotState();
        groupSnapshotClient = new GroupSnapshotClient(httpRequestService, gson, groupSnapshotState);
        chatState = new ChatState();
        chatBackfillClient = new ChatBackfillClient(httpRequestService, gson, chatState);

        GroupScapePanel panel = new GroupScapePanel(
                () -> LinkBrowser.browse(httpRequestService.getBaseUrl()),
                client, config, rosterState, groupSnapshotState, chatState,
                text -> chatSendManager.send(text, config),
                chatMarkReadManager,
                itemManager, skillIconManager, spriteManager,
                clientThread, () -> localMember, () -> localSnapshot);
        navigationButton = NavigationButton.builder()
            .tooltip("GroupScape")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navigationButton);

        chatOverlayWindow = new com.groupscape.sidepanel.ChatOverlayWindow(
                rosterState, chatState, text -> chatSendManager.send(text, config), config, configManager);

        GroupLinkListener groupLinkListener = new GroupLinkListener() {
            @Override
            public void onLinkRequired() {
                if (linkRequiredWarningShown) return;
                linkRequiredWarningShown = true;
                sendChatMessage("GroupScape: this character isn't linked to a group yet - link it on the website to enable the party overlay.");
            }

            @Override
            public void onLinked() {
                linkRequiredWarningShown = false;
                String apiKey = config.apiKey().trim();
                long accountHashValue = client.getAccountHash();
                if (!apiKey.isEmpty() && accountHashValue != -1) {
                    // Runs on the WebSocket's own callback thread (see RosterClient.onOpen) - offload
                    // the blocking backfill GET so it doesn't stall that connection's read loop.
                    String accountHash = String.valueOf(accountHashValue);
                    String baseUrl = httpRequestService.getBaseUrl();
                    new Thread(() -> chatBackfillClient.fetch(baseUrl, accountHash, apiKey), "groupscape-chat-backfill")
                            .start();
                }
            }
        };
        dataManager.setGroupLinkListener(groupLinkListener);

        pingState = new PingState();
        pingManager = new PingManager(client, httpRequestService);
        raidMarkerState = new RaidMarkerState();
        raidMarkerManager = new RaidMarkerManager(client, httpRequestService);
        rosterClient = new RosterClient(okHttpClient, gson, rosterState, this::onGroupKillEvent, this::onGroupDropEvent,
                new RosterClient.PingEventListener() {
                    @Override
                    public void onPingStart(com.groupscape.roster.RosterWireTypes.PingStartPayload payload) {
                        pingState.start(payload);
                        onGroupPingStart(payload);
                    }

                    @Override
                    public void onPingUpdate(com.groupscape.roster.RosterWireTypes.PingUpdatePayload payload) {
                        pingState.update(payload);
                    }

                    @Override
                    public void onPingEnd(com.groupscape.roster.RosterWireTypes.PingEndPayload payload) {
                        pingState.end(payload);
                    }
                },
                new RosterClient.RaidMarkerEventListener() {
                    @Override
                    public void onMarkerStart(com.groupscape.roster.RosterWireTypes.RaidMarkerStartPayload payload) {
                        raidMarkerState.start(payload);
                    }

                    @Override
                    public void onMarkerUpdate(com.groupscape.roster.RosterWireTypes.RaidMarkerUpdatePayload payload) {
                        raidMarkerState.update(payload);
                    }

                    @Override
                    public void onMarkerEnd(com.groupscape.roster.RosterWireTypes.RaidMarkerEndPayload payload) {
                        raidMarkerState.end(payload);
                    }
                },
                (payload, ts) -> {
                    chatState.add(new ChatState.Entry(payload.messageId, payload.memberName, payload.text,
                            parseTsOrNow(ts)));
                    printInlineChatMessage(payload);
                },
                panel::applyChatRead,
                groupLinkListener);
        rosterNotifier = new RosterNotifier();
        partyFrameOverlay = new PartyFrameOverlay(client, config, rosterState, dataManager.getNpcDialogueTracker(), spriteManager);
        overlayManager.add(partyFrameOverlay);
        tileHighlightOverlay = new TileHighlightOverlay(client, config, rosterState);
        overlayManager.add(tileHighlightOverlay);
        minimapLocationOverlay = new MinimapLocationOverlay(client, config, rosterState, tooltipManager, memberMapIcons);
        overlayManager.add(minimapLocationOverlay);
        groupWorldMapPoints = new GroupWorldMapPoints(client, config, rosterState, worldMapPointManager, memberMapIcons);

        pingViewportOverlay = new PingViewportOverlay(client, config, pingState, rosterState);
        overlayManager.add(pingViewportOverlay);
        pingMinimapOverlay = new PingMinimapOverlay(client, config, pingState, rosterState, tooltipManager, pingArrowIcons);
        overlayManager.add(pingMinimapOverlay);
        pingWorldMapPoints = new PingWorldMapPoints(config, pingState, rosterState, worldMapPointManager, pingArrowIcons);

        raidMarkerViewportOverlay = new RaidMarkerViewportOverlay(client, config, raidMarkerState, raidMarkerIcons);
        overlayManager.add(raidMarkerViewportOverlay);
        raidMarkerMinimapOverlay = new RaidMarkerMinimapOverlay(client, config, raidMarkerState, tooltipManager, raidMarkerIcons);
        overlayManager.add(raidMarkerMinimapOverlay);
        raidMarkerWorldMapPoints = new RaidMarkerWorldMapPoints(config, raidMarkerState, worldMapPointManager, raidMarkerIcons);

        log.info("GroupScape Tracker v{} started!", PluginVersion.get());
    }

    @Override
    protected void shutDown() throws Exception {
        collectionLogWidgetSubscriber.shutDown();
        chatSuppressionSubscriber.shutDown();
        chatSendManager.shutdown();
        chatMarkReadManager.shutdown();

        if (navigationButton != null) {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
        if (chatOverlayWindow != null) {
            chatOverlayWindow.shutdown();
            chatOverlayWindow = null;
        }
        cachePotions = false;
        potionStoreVars = null;

        if (partyFrameOverlay != null) {
            overlayManager.remove(partyFrameOverlay);
            partyFrameOverlay = null;
        }
        if (tileHighlightOverlay != null) {
            overlayManager.remove(tileHighlightOverlay);
            tileHighlightOverlay = null;
        }
        if (minimapLocationOverlay != null) {
            overlayManager.remove(minimapLocationOverlay);
            minimapLocationOverlay = null;
        }
        if (groupWorldMapPoints != null) {
            groupWorldMapPoints.clear();
            groupWorldMapPoints = null;
        }
        if (pingViewportOverlay != null) {
            overlayManager.remove(pingViewportOverlay);
            pingViewportOverlay = null;
        }
        if (pingMinimapOverlay != null) {
            overlayManager.remove(pingMinimapOverlay);
            pingMinimapOverlay = null;
        }
        if (pingWorldMapPoints != null) {
            pingWorldMapPoints.clear();
            pingWorldMapPoints = null;
        }
        if (pingManager != null) {
            pingManager.shutdown();
            pingManager = null;
        }
        if (pingState != null) {
            pingState.clear();
            pingState = null;
        }
        if (raidMarkerViewportOverlay != null) {
            overlayManager.remove(raidMarkerViewportOverlay);
            raidMarkerViewportOverlay = null;
        }
        if (raidMarkerMinimapOverlay != null) {
            overlayManager.remove(raidMarkerMinimapOverlay);
            raidMarkerMinimapOverlay = null;
        }
        if (raidMarkerWorldMapPoints != null) {
            raidMarkerWorldMapPoints.clear();
            raidMarkerWorldMapPoints = null;
        }
        if (raidMarkerManager != null) {
            raidMarkerManager.shutdown();
            raidMarkerManager = null;
        }
        if (raidMarkerState != null) {
            raidMarkerState.clear();
            raidMarkerState = null;
        }
        if (rosterClient != null) {
            rosterClient.shutdown();
            rosterClient = null;
        }
        groupSnapshotClient = null;
        if (groupSnapshotState != null) {
            groupSnapshotState.clear();
            groupSnapshotState = null;
        }
        localMember = null;
        localSnapshot = null;
        dataManager.setGroupLinkListener(null);

        log.info("GroupScape Tracker stopped!");
    }

    /**
     * Connects/reconnects the party overlay's WebSocket whenever the configured API key or the
     * client's account hash changes. The server only accepts this connection once the character
     * has been linked to a group via the website ({@code POST /account/characters/link-group}) -
     * an unlinked character gets a 403 (surfaced to the player via
     * {@link GroupLinkListener#onLinkRequired()}) and keeps retrying every 5s until that link
     * exists.
     */
    /**
     * Rebuilds the sidepanel's "you" row on the client thread - see {@link #localMember}'s doc
     * for why this can't just be called lazily from the panel's own Swing refresh timer.
     */
    private void updateLocalSidepanelSnapshot() {
        if (doNotUseThisData()) {
            localMember = null;
            localSnapshot = null;
            return;
        }
        localMember = LocalRosterMemberFactory.build(client, dataManager.getNpcDialogueTracker(), rosterState);
        localSnapshot = LocalGroupSnapshotFactory.build(client);
    }

    /** {@code envelope.ts} is normally a valid ISO-8601 instant, but a live chat message shouldn't
     * be dropped over a parse hiccup - falls back to "now", which is off by at most a network
     * round trip. */
    private static Instant parseTsOrNow(String ts) {
        try {
            return Instant.parse(ts);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private void reconcileRosterConnection() {
        dataManager.identify();

        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();

        if (apiKey.isEmpty() || accountHashValue == -1) {
            rosterClient.disconnect();
            return;
        }

        rosterClient.connect(httpRequestService.getBaseUrl(), String.valueOf(accountHashValue), apiKey);
    }

    @Schedule(
            period = SECONDS_BETWEEN_UPLOADS,
            unit = ChronoUnit.SECONDS,
            asynchronous = true
    )
    public void submitToApi() {
        dataManager.submitToApi();
    }

    @Schedule(
            period = SECONDS_BETWEEN_GROUP_SNAPSHOT_POLL,
            unit = ChronoUnit.SECONDS,
            asynchronous = true
    )
    public void pollGroupSnapshot() {
        String apiKey = config.apiKey().trim();
        long accountHashValue = client.getAccountHash();
        if (apiKey.isEmpty() || accountHashValue == -1) return;

        groupSnapshotClient.poll(httpRequestService.getBaseUrl(), String.valueOf(accountHashValue), apiKey);
    }

    @Schedule(
            period = SECONDS_BETWEEN_UPLOADS,
            unit = ChronoUnit.SECONDS
    )
    public void updateThingsThatDoChangeOften() {
        reconcileRosterConnection();
        updateLocalSidepanelSnapshot();

        if (doNotUseThisData())
            return;
        Player player = client.getLocalPlayer();
        String playerName = player.getName();
        rosterNotifier.check(rosterState.all(), playerName, config).forEach(this::sendChatMessage);
        dataManager.getResources().update(new ResourcesState(playerName, client));
        dataManager.getSpecialAttack().update(new SpecialAttackState(playerName, client));
        dataManager.getActivePrayers().update(new ActivePrayersState(playerName, client));
        dataManager.getRichPresence().update(new RichPresenceState(playerName, client, dataManager.getNpcDialogueTracker()));

        LocalPoint localPoint = player.getLocalLocation();
        WorldView worldView = player.getWorldView();
        int worldViewId = worldView.getId();
        boolean isOnBoat = worldViewId != WorldView.TOPLEVEL;
        WorldPoint worldPoint;
        if (isOnBoat) {
            WorldEntity worldEntity = client.getTopLevelWorldView().worldEntities().byIndex(worldViewId);
            worldPoint = WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
        } else {
            worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        }
        dataManager.getPosition().update(new LocationState(playerName, worldPoint, isOnBoat, client.getWorld()));

        dataManager.getRunePouch().update(new RunePouchState(playerName, client));
        dataManager.getQuiver().update(new QuiverState(playerName, client, itemManager));
    }

    @Schedule(
            period = SECONDS_BETWEEN_INFREQUENT_DATA_CHANGES,
            unit = ChronoUnit.SECONDS
    )
    public void updateThingsThatDoNotChangeOften() {
        if (doNotUseThisData())
            return;
        String playerName = client.getLocalPlayer().getName();
        dataManager.getQuests().update(new QuestState(playerName, client));
        dataManager.getAchievementDiary().update(new AchievementDiaryState(playerName, client));
        dataManager.getCombatAchievements().update(new CombatAchievementState(playerName, client));
        // Backstop for the reactive push in checkSlayerTaskUpdate() - covers the case where the
        // plugin/client (re)started mid-task, so nothing has fired a SLAYER_TARGET/etc varp change
        // yet. DataState#update() dedupes via SlayerTaskState#equals(), so this is a harmless no-op
        // most of the time.
        pushSlayerTaskState(playerName);
    }

    @Schedule(
            period = SECONDS_BETWEEN_PORTRAIT_BACKSTOP,
            unit = ChronoUnit.SECONDS
    )
    public void capturePortraitBackstop() {
        portraitCaptureManager.captureIfLoggedIn();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (doNotUseThisData()) return;

        final int varpId = event.getVarpId();
        if (varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO || varpId == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) {
            String playerName = client.getLocalPlayer().getName();
            dataManager.getQuiver().update(new QuiverState(playerName, client, itemManager));
        }

        if (potionStoreVars != null && potionStoreVars.contains(varpId)) {
            cachePotions = true;
        }

        checkSlayerTaskUpdate(varpId, event.getVarbitId());
        checkCombatAchievementUpdate(varpId, event.getVarbitId());
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == DOOM_OF_MOKHAIOTL_REWARD_WIDGET_GROUP) {
            captureDoomOfMokhaiotlDelveLevel();
            return;
        }

        if (event.getGroupId() != InterfaceID.CA_TASKS) return;
        if (doNotUseThisData()) return;

        String playerName = client.getLocalPlayer().getName();
        if (playerName == null) return;
        dataManager.getCombatAchievements().update(new CombatAchievementState(playerName, client));
    }

    /** Known slayer master names (lowercase), mirrored from the site's SLAYER_MASTER_ICONS list
     * (groupscape-web/site/src/data/slayer.js) - used to recognize a task-check/reassignment
     * dialogue as being with a master (as opposed to any other NPC) so the master name can be
     * refreshed immediately, see {@link #captureSlayerTaskMasterDialogue}. */
    private static final Set<String> SLAYER_MASTER_NAMES = Set.of(
            "turael", "spria", "mazchna", "vannaka", "chaeldar", "nieve", "steve", "duradel",
            "konar quo maten", "krystilia", "mortimer", "aya", "achtryn", "kuradal"
    );

    /** varps: task id/amounts/area, plus Mortimer's separate streak counter (itself a varp). */
    private static final int[] SLAYER_TASK_VARPS = {
            VarPlayerID.SLAYER_TARGET,
            VarPlayerID.SLAYER_COUNT,
            VarPlayerID.SLAYER_COUNT_ORIGINAL,
            VarPlayerID.SLAYER_AREA,
            VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED,
    };
    /** varbits: points, master ordinal (picks which streak counter applies), and the two
     * non-Mortimer streak counters. */
    private static final int[] SLAYER_TASK_VARBITS = {
            VarbitID.SLAYER_POINTS,
            VarbitID.SLAYER_MASTER,
            VarbitID.SLAYER_TASKS_COMPLETED,
            VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED,
            // Names which specific DT2 boss a BOSS_TASK_ID assignment refers to - see
            // SlayerTaskState#resolveBossTaskId. Not itself a new-task signal (SLAYER_TARGET
            // already flips for that); just needs to trigger a rebuild so the resolved name updates.
            VarbitID.SLAYER_TARGET_BOSSID,
    };

    /**
     * Slayer task fields are pushed reactively (rather than on the infrequent 60s schedule like
     * combat achievements/quests, see {@link #updateThingsThatDoNotChangeOften}) since a task
     * change is a meaningful, low-frequency event the sidepanel/website should reflect right away,
     * not up to a minute late. {@link DataState#update} already dedupes via
     * {@link SlayerTaskState#equals}, so it would be harmless (just wasteful) to rebuild+push on
     * every candidate varp/varbit change - {@link #slayerTaskPushPending} coalesces those into one
     * rebuild+push per tick instead, since a single task assignment/turn-in changes several of
     * them at once.
     */
    private void checkSlayerTaskUpdate(int varpId, int varbitId) {
        boolean isNewTask = varpId == VarPlayerID.SLAYER_TARGET || varpId == VarPlayerID.SLAYER_COUNT_ORIGINAL;

        boolean relevant = isNewTask;
        if (!relevant) {
            for (int v : SLAYER_TASK_VARPS) {
                if (varpId == v) {
                    relevant = true;
                    break;
                }
            }
        }
        if (!relevant) {
            for (int v : SLAYER_TASK_VARBITS) {
                if (varbitId == v) {
                    relevant = true;
                    break;
                }
            }
        }
        if (!relevant) return;

        if (isNewTask && lastDialogNpcName != null) {
            currentSlayerTaskMaster = lastDialogNpcName;
        }

        // Deferred to the next client-thread tick, same as RuneLite core's own SlayerPlugin
        // (see its updateTask()/clientThread.invokeLater() call) - the DBTableID.SlayerTaskSublist
        // row for a just-changed SLAYER_TARGET_BOSSID isn't guaranteed to be populated yet at the
        // moment this VarbitChanged handler runs mid-tick, so resolving synchronously here can miss
        // it and (for a brand-new task, with no previous state to fall back on) permanently strand
        // taskName at null - i.e. the site showing "Unknown task" forever for that assignment.
        //
        // Coalesced via slayerTaskPushPending: a task assignment/turn-in changes several of
        // SLAYER_TASK_VARPS/SLAYER_TASK_VARBITS at once, and RuneLite fires one VarbitChanged per
        // underlying varp/varbit, so without this guard one transition queued several of these
        // callbacks - each redoing SlayerTaskState's DB-table lookups and a full panel re-render.
        if (slayerTaskPushPending) return;
        slayerTaskPushPending = true;

        clientThread.invokeLater(() -> {
            slayerTaskPushPending = false;

            Player local = client.getLocalPlayer();
            if (local == null || local.getName() == null) return;

            pushSlayerTaskState(local.getName());
        });
    }

    /** Rebuilds and pushes {@link SlayerTaskState}, handing in the last-pushed state so a
     * transient DB-row resolution miss (see {@link SlayerTaskState}'s two-arg-plus-previous
     * constructor javadoc) falls back to the previously known task name/location instead of
     * clobbering it with null. Also feeds {@code next} to {@link #handleSlayerTaskTransition} for
     * the History/Stats tabs' event stream. */
    private void pushSlayerTaskState(String playerName) {
        restorePendingSlayerTaskCloseIfNeeded(playerName);

        DataState slayerTask = dataManager.getSlayerTask();
        SlayerTaskState previous = (SlayerTaskState) slayerTask.mostRecentState();
        SlayerTaskState next = new SlayerTaskState(playerName, client, currentSlayerTaskMaster, previous);
        slayerTask.update(next);
        handleSlayerTaskTransition(playerName, next);
    }

    /**
     * Restores {@link #currentSlayerTaskEventId} et al from {@link #SLAYER_PENDING_CLOSE_CONFIG_KEY}
     * the first time a slayer-task push happens after plugin startup - see that field's javadoc for
     * why this exists. Runs before {@link #handleSlayerTaskTransition} so a task that already
     * finished/turned over while the plugin was down gets closed out correctly (by comparing the
     * restored snapshot against whatever the live varps now show) instead of leaking a permanently
     * open row. Consumes the stored value either way (restore is at most once per session).
     */
    private void restorePendingSlayerTaskCloseIfNeeded(String playerName) {
        if (slayerTaskRestoreAttempted) return;
        slayerTaskRestoreAttempted = true;

        String raw = configManager.getConfiguration(SLAYER_TRACKER_CONFIG_GROUP, SLAYER_PENDING_CLOSE_CONFIG_KEY);
        if (raw == null) return;
        configManager.unsetConfiguration(SLAYER_TRACKER_CONFIG_GROUP, SLAYER_PENDING_CLOSE_CONFIG_KEY);

        try {
            Map<?, ?> blob = gson.fromJson(raw, Map.class);
            if (blob == null || !playerName.equals(blob.get("playerName"))) return;

            currentSlayerTaskEventId = (String) blob.get("eventId");
            currentSlayerTaskAssignedAt = (String) blob.get("assignedAt");
            currentSlayerTaskPointsAtAssignment = ((Number) blob.get("pointsAtAssignment")).intValue();
            currentSlayerTaskId = ((Number) blob.get("taskId")).intValue();
            currentSlayerTaskLastSnapshot = new ClosingTaskSnapshot(
                    (String) blob.get("masterName"),
                    (String) blob.get("taskName"),
                    ((Number) blob.get("amountRemaining")).intValue(),
                    ((Number) blob.get("initialAmount")).intValue());
        } catch (RuntimeException e) {
            log.warn("Failed to restore pending slayer task close state", e);
        }
    }

    /** Mirrors the in-memory {@link #currentSlayerTaskEventId} tracking state to {@link
     * #SLAYER_PENDING_CLOSE_CONFIG_KEY} after every {@link #handleSlayerTaskTransition} call, so a
     * plugin/client restart has something to pick back up - see {@link #restorePendingSlayerTaskCloseIfNeeded}. */
    private void persistPendingSlayerTaskClose(String playerName) {
        if (currentSlayerTaskEventId == null || currentSlayerTaskLastSnapshot == null) {
            configManager.unsetConfiguration(SLAYER_TRACKER_CONFIG_GROUP, SLAYER_PENDING_CLOSE_CONFIG_KEY);
            return;
        }

        Map<String, Object> blob = new HashMap<>();
        blob.put("playerName", playerName);
        blob.put("eventId", currentSlayerTaskEventId);
        blob.put("assignedAt", currentSlayerTaskAssignedAt);
        blob.put("pointsAtAssignment", currentSlayerTaskPointsAtAssignment);
        blob.put("taskId", currentSlayerTaskId);
        blob.put("masterName", currentSlayerTaskLastSnapshot.masterName);
        blob.put("taskName", currentSlayerTaskLastSnapshot.taskName);
        blob.put("amountRemaining", currentSlayerTaskLastSnapshot.amountRemaining);
        blob.put("initialAmount", currentSlayerTaskLastSnapshot.initialAmount);
        configManager.setConfiguration(SLAYER_TRACKER_CONFIG_GROUP, SLAYER_PENDING_CLOSE_CONFIG_KEY, gson.toJson(blob));
    }

    /** Slayer task block price (reward points), by lowercase master name - flat 30 for a cancel
     * regardless of master, this table is only consulted for the block case. Ported from the
     * OSRS Wiki's "Slayer Masters" article. */
    private static final Map<String, Integer> SLAYER_BLOCK_PRICE = Map.ofEntries(
            Map.entry("turael", 40), Map.entry("aya", 40), Map.entry("spria", 40),
            Map.entry("mazchna", 50), Map.entry("achtryn", 50),
            Map.entry("vannaka", 60),
            Map.entry("chaeldar", 70),
            Map.entry("konar quo maten", 80),
            Map.entry("nieve", 90), Map.entry("steve", 90),
            Map.entry("duradel", 100), Map.entry("kuradal", 100),
            Map.entry("krystilia", 100),
            Map.entry("mortimer", 120)
    );
    private static final int SLAYER_CANCEL_COST = 30;

    /** Masters who'll swap your current task for a new one at no points cost, at the price of
     * resetting your normal-bucket streak to 0 - the "Turael skip". Only these three (Turael, and
     * its While Guthix Sleeps/pre-quest reskins Aya/Spria - see {@code MASTER_GROUPS} in
     * slayer-history-tab.js) offer it; Mazchna/Achtryn and everyone above them don't. */
    private static final Set<String> SLAYER_RESET_MASTERS = Set.of("turael", "aya", "spria");

    /**
     * Detects a slayer task starting or ending by comparing consecutive {@link SlayerTaskState}
     * pushes, and feeds {@link SlayerTaskCloseEvents} for the site's slayer History/Stats tabs.
     * Runs on every {@link #pushSlayerTaskState} call (i.e. every relevant varp/varbit change,
     * including every kill's amount-remaining tick) but is a no-op unless {@code next}'s task
     * identity actually differs from what's currently being tracked - see
     * {@link #currentSlayerTaskEventId}'s javadoc for why that's tracked separately from
     * {@code previous}/{@code next} rather than solely off a prior push's task id. The close
     * itself is built from {@link #currentSlayerTaskLastSnapshot}, not the last {@code DataState}
     * push - see that field's javadoc.
     */
    private void handleSlayerTaskTransition(String playerName, SlayerTaskState next) {
        boolean hasTaskNow = next.hasTask();

        if (currentSlayerTaskEventId != null && (!hasTaskNow || next.taskId() != currentSlayerTaskId)) {
            if (currentSlayerTaskLastSnapshot != null) {
                closeSlayerTask(playerName, currentSlayerTaskLastSnapshot, next);
            }
            currentSlayerTaskEventId = null;
            currentSlayerTaskAssignedAt = null;
            currentSlayerTaskLastSnapshot = null;
        }

        if (hasTaskNow && currentSlayerTaskEventId == null && next.masterName() != null && next.taskName() != null) {
            currentSlayerTaskId = next.taskId();
            currentSlayerTaskEventId = SlayerTaskCloseEvents.newClientEventId();
            currentSlayerTaskAssignedAt = Instant.now().toString();
            currentSlayerTaskPointsAtAssignment = next.points();
            currentSlayerTaskLastSnapshot = ClosingTaskSnapshot.from(next);
            dataManager.getSlayerTaskCloseEvents().onTaskAssigned(
                    playerName, currentSlayerTaskEventId, next.taskName(), next.masterName(), next.initialAmount(),
                    next.modifierType(), next.modifierValue(), next.modifierNegative());
        } else if (hasTaskNow && currentSlayerTaskEventId != null && next.taskId() == currentSlayerTaskId) {
            if (suppressNextSlayerTaskSnapshotRefresh) {
                suppressNextSlayerTaskSnapshotRefresh = false;
            } else {
                currentSlayerTaskLastSnapshot = ClosingTaskSnapshot.from(next);
            }
        }

        persistPendingSlayerTaskClose(playerName);
    }

    /**
     * Classifies how the task tracked as {@link #currentSlayerTaskEventId} just closed and emits
     * its close event. {@code closingSnapshot} (last pushed state for that task) supplies the
     * task/master name and final kill count; {@code afterClose} (the just-built current state,
     * whatever it now represents - no task, or a freshly-assigned one) supplies the slayer points
     * reading *after* whatever this closure cost/paid, so the delta against
     * {@link #currentSlayerTaskPointsAtAssignment} classifies the close. Completed (the kill count
     * already reached 0 remaining) is checked first, but is no longer an unconditional win: a
     * player can finish a task's kill count and then block that *same* task via the Rewards Shop
     * before turning it in/getting reassigned (a common "grind it out, then block it" sequence),
     * and that's still this task's own outcome, not an unrelated purchase - so a completed task
     * whose points delta exactly matches its own closing master's block price is relabeled
     * "blocked" instead. The same applies to a delta of exactly {@code -SLAYER_CANCEL_COST}: the
     * {@code SLAYER_TARGET}/{@code SLAYER_COUNT} varp writes for an in-game cancel aren't
     * guaranteed to land in the same tick either (only the Rewards Shop's block-confirm click has
     * a hook to snapshot around that race, see {@link #suppressNextSlayerTaskSnapshotRefresh}), so
     * {@code closingSnapshot.amountRemaining} can read a corrupted 0 for a task cancelled after
     * only a few kills - that read back as "completed" at full kills despite the real -30 point
     * cost sitting right there in the delta, and is relabeled "cancelled" instead. Anything else at
     * 0 remaining forces "completed" with no points delta, because a kill-count task auto-completes
     * the instant its counter hits 0 with no other window to act on it, so any *other* points
     * movement measured across its lifetime is necessarily an unrelated reward-shop purchase (most
     * commonly blocking a *different*, newly-assigned task); misreading that as this task being
     * blocked/cancelled was exactly the bug that mislabeled a fully-killed Turoth task as "blocked
     * -120" when the points actually came from blocking an unrelated task. Otherwise (task did NOT
     * complete): cancelled (delta == -30) vs. blocked
     * (delta matches that master's known block price) vs. reset (delta == 0 and either the closing
     * task's own master, or whichever master {@code afterClose} shows a new task from, is one of
     * {@link #SLAYER_RESET_MASTERS} - a free Turael/Aya/Spria skip is granted by talking to one of
     * those three, not by the task *being closed* having come from one of them; checking only the
     * closing master missed the common case of skipping a higher-level master's task) vs. unknown
     * (anything else, e.g. a game update changing these prices). A block purchase made via the
     * Slayer Rewards shop is also independently logged as its own standalone row by
     * {@link #handleSlayerRewardShopConfirm} - that's the authoritative record of the points spend
     * itself; this method only classifies what happened to the *currently tracked* task.
     */
    private void closeSlayerTask(String playerName, ClosingTaskSnapshot closingSnapshot, SlayerTaskState afterClose) {
        currentSlayerTaskId = -1;
        String eventId = currentSlayerTaskEventId;
        String assignedAt = currentSlayerTaskAssignedAt;
        int pointsAtAssignment = currentSlayerTaskPointsAtAssignment;
        currentSlayerTaskEventId = null;
        currentSlayerTaskAssignedAt = null;
        if (eventId == null || closingSnapshot.masterName == null || closingSnapshot.taskName == null) return;

        int pointsDelta = afterClose.points() - pointsAtAssignment;
        String closingMasterKey = closingSnapshot.masterName.trim().toLowerCase();
        String incomingMasterKey = afterClose.hasTask() && afterClose.masterName() != null
                ? afterClose.masterName().trim().toLowerCase()
                : null;
        Integer blockPrice = SLAYER_BLOCK_PRICE.get(closingMasterKey);
        String status;
        boolean amountRemainingSuspectCorrupted = false;
        if (pendingCurrentTaskBlockName != null && pendingCurrentTaskBlockName.equalsIgnoreCase(closingSnapshot.taskName)) {
            status = "blocked";
            pointsDelta = -pendingCurrentTaskBlockPrice;
            pendingCurrentTaskBlockName = null;
            amountRemainingSuspectCorrupted = true;
        } else if (closingSnapshot.amountRemaining <= 0) {
            if (blockPrice != null && pointsDelta == -blockPrice) {
                status = "blocked";
            } else if (pointsDelta == -SLAYER_CANCEL_COST) {
                status = "cancelled";
            } else {
                status = "completed";
                pointsDelta = 0;
            }
        } else if (pointsDelta == -SLAYER_CANCEL_COST) {
            status = "cancelled";
        } else if (blockPrice != null && pointsDelta == -blockPrice) {
            status = "blocked";
        } else if (pointsDelta == 0 && (SLAYER_RESET_MASTERS.contains(closingMasterKey)
                || (incomingMasterKey != null && SLAYER_RESET_MASTERS.contains(incomingMasterKey)))) {
            status = "reset";
        } else {
            status = "unknown";
        }

        // A block confirmed via pendingCurrentTaskBlockName forces "blocked" precisely because
        // amountRemaining is suspected corrupted (see that field's javadoc) - reporting it as the
        // kill count would just reproduce the same bogus "completed at max kills" data this branch
        // exists to prevent, so amount_done is left unknown (0) rather than trusted.
        int amountDone = amountRemainingSuspectCorrupted
                ? 0
                : Math.max(0, closingSnapshot.initialAmount - closingSnapshot.amountRemaining);
        dataManager.getSlayerTaskCloseEvents().onTaskClosed(
                playerName,
                eventId,
                closingSnapshot.taskName,
                closingSnapshot.masterName,
                status,
                amountDone,
                closingSnapshot.initialAmount,
                pointsDelta != 0 ? pointsDelta : null,
                assignedAt);
    }

    private static final int[] COMBAT_ACHIEVEMENT_TIER_VARBITS = {
            Varbits.COMBAT_ACHIEVEMENT_TIER_EASY,
            Varbits.COMBAT_ACHIEVEMENT_TIER_MEDIUM,
            Varbits.COMBAT_ACHIEVEMENT_TIER_HARD,
            Varbits.COMBAT_ACHIEVEMENT_TIER_ELITE,
            Varbits.COMBAT_ACHIEVEMENT_TIER_MASTER,
            Varbits.COMBAT_ACHIEVEMENT_TIER_GRANDMASTER,
    };

    /**
     * Combat achievements are otherwise only refreshed on the 60s
     * {@link #updateThingsThatDoNotChangeOften} poll, so a task/tier completed mid-session could sit
     * stale on the site for up to a minute. Push reactively instead, same as
     * {@link #checkSlayerTaskUpdate}. {@link DataState#update} dedupes via
     * {@link CombatAchievementState#equals}, so it's harmless to rebuild+push on every candidate
     * varp/varbit change.
     */
    private void checkCombatAchievementUpdate(int varpId, int varbitId) {
        boolean relevant = false;
        for (int v : CombatAchievementState.CA_TASK_COMPLETED_VARPS) {
            if (varpId == v) {
                relevant = true;
                break;
            }
        }
        if (!relevant) {
            for (int v : COMBAT_ACHIEVEMENT_TIER_VARBITS) {
                if (varbitId == v) {
                    relevant = true;
                    break;
                }
            }
        }
        if (!relevant) return;

        String playerName = client.getLocalPlayer().getName();
        if (playerName == null) return;
        dataManager.getCombatAchievements().update(new CombatAchievementState(playerName, client));
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (cachePotions) {
            cachePotions = false;
            updatePotionStorage();

            Widget w = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
            if (w != null && potionStoreVars == null) {
                int[] trigger = w.getVarTransmitTrigger();
                potionStoreVars = new HashSet<>();
                for (int varId : trigger) {
                    potionStoreVars.add(varId);
                }
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        --itemsDeposited;
        updateInteracting();
        checkWildernessEntry();
        checkPendingDeathCandidate();
        if (groupWorldMapPoints != null) {
            groupWorldMapPoints.sync();
        }
        if (pingWorldMapPoints != null) {
            pingWorldMapPoints.sync();
        }
        if (pingManager != null) {
            pingManager.onGameTick(config);
        }
        if (raidMarkerWorldMapPoints != null) {
            raidMarkerWorldMapPoints.sync();
        }
        if (raidMarkerManager != null) {
            raidMarkerManager.onGameTick(config);
        }

        Widget groupStorageLoaderText = client.getWidget(GROUP_STORAGE_LOADER, 1);
        if (groupStorageLoaderText != null) {
            if (groupStorageLoaderText.getText().equalsIgnoreCase("saving...")) {
                dataManager.getSharedBank().commitTransaction();
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        if (doNotUseThisData())
            return;
        String playerName = client.getLocalPlayer().getName();
        dataManager.getSkills().update(new SkillState(playerName, client));

        if (statChanged.getSkill() == Skill.HITPOINTS) {
            checkLowHpAlert(playerName, statChanged.getBoostedLevel(), statChanged.getLevel());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            portraitCaptureManager.onLogin();
        } else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) {
            // Unlike a ping, a raid marker never auto-expires - clear all of the local player's
            // markers on logout/world-hop so they don't linger for the rest of the group while
            // this client is disconnected (the client stays the sole source of truth, same as pings).
            if (raidMarkerManager != null) {
                raidMarkerManager.clearAll(config);
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (doNotUseThisData())
            return;
        String playerName = client.getLocalPlayer().getName();
        final int id = event.getContainerId();
        ItemContainer container = event.getItemContainer();

        if (id == InventoryID.BANK) {
            dataManager.getDeposited().reset();
            dataManager.getBank().update(new ItemContainerState(playerName, container, itemManager));
        } else if (id == InventoryID.SEED_VAULT) {
            dataManager.getSeedVault().update(new ItemContainerState(playerName, container, itemManager));
        } else if (id == InventoryID.INV) {
            ItemContainerState newInventoryState = new ItemContainerState(playerName, container, itemManager, 28);
            if (itemsDeposited > 0) {
                updateDeposited(newInventoryState, (ItemContainerState) dataManager.getInventory().mostRecentState());
            }

            dataManager.getInventory().update(newInventoryState);
        } else if (id == InventoryID.WORN) {
            ItemContainerState newEquipmentState = new ItemContainerState(playerName, container, itemManager, 14);
            if (itemsDeposited > 0) {
                updateDeposited(newEquipmentState, (ItemContainerState) dataManager.getEquipment().mostRecentState());
            }

            dataManager.getEquipment().update(newEquipmentState);
            portraitCaptureManager.onEquipmentSynced();
        } else if (id == InventoryID.INV_GROUP_TEMP) {
            dataManager.getSharedBank().update(new ItemContainerState(playerName, container, itemManager));
        }
    }

    @Subscribe
    private void onScriptPreFired(ScriptPreFired event) {
        if (doNotUseThisData()) return;
        if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING) {
            cachePotions = true;
        }
    }

    @Subscribe
    private void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == CHATBOX_ENTERED && client.getWidget(InterfaceID.BankDepositbox.INVENTORY) != null) {
            itemsMayHaveBeenDeposited();
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        final int param1 = event.getParam1();
        final MenuAction menuAction = event.getMenuAction();
        if (menuAction == MenuAction.CC_OP) {
            if (param1 == DEPOSIT_ITEM || param1 == DEPOSIT_INVENTORY || param1 == DEPOSIT_EQUIPMENT) {
                itemsMayHaveBeenDeposited();
            } else if (event.getWidget() != null
                    && event.getWidget().getId() == InterfaceID.SlayerRewards.CONFIRM_BUTTON) {
                handleSlayerRewardShopConfirm();
            }
        } else if (isGameObjectAction(menuAction)) {
            recordObjectInteraction(event);
        }
    }

    /**
     * Adds a "Copy name" entry when right-clicking a group member's name in
     * {@link PartyFrameOverlay}, copying the name to the clipboard.
     */
    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        if (partyFrameOverlay != null) {
            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            RosterMember member = partyFrameOverlay.memberAt(mouse.getX(), mouse.getY());
            if (member != null) {
                client.createMenuEntry(-1)
                        .setOption("Copy name")
                        .setTarget(ColorUtil.wrapWithColorTag(member.name, Color.YELLOW))
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> handleCopyName(member.name));
            }
        }

        addPingMenuEntry(event);
        addRaidMarkerMenuEntries(event);
    }

    /**
     * Adds a "Ping" entry to: an NPC's own right-click menu (viewport), the world map's right-click
     * menu (via {@link WorldMapCoordinates}, since the world map has no native right-click menu of
     * its own to piggyback on), or a plain ground tile (viewport) - whichever one applies to this
     * particular menu, checked in that priority order. Mutually exclusive by construction: a given
     * right-click is over exactly one of these at a time. Only shown on shift-right-click, so a
     * plain right-click's menu stays uncluttered.
     */
    private void addPingMenuEntry(MenuOpened event) {
        if (!config.pingsEnabled() || pingManager == null || !isShiftHeld()) return;

        try {
            if (pingManager.hasOwnNpcPing()) {
                client.createMenuEntry(-1)
                        .setOption("Clear NPC ping")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> {
                            log.debug("Ping: 'Clear NPC ping' clicked");
                            pingManager.clearOwnNpcPing(config);
                        });
            }
            if (pingManager.hasOwnTilePing()) {
                client.createMenuEntry(-1)
                        .setOption("Clear tile ping")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> {
                            log.debug("Ping: 'Clear tile ping' clicked");
                            pingManager.clearOwnTilePing(config);
                        });
            }

            NPC npc = findMenuNpc(event.getMenuEntries());
            if (npc != null) {
                String label = npc.getName() != null ? npc.getName() : "NPC";
                log.debug("Ping: adding 'Ping' menu entry for NPC {}", label);
                client.createMenuEntry(-1)
                        .setOption("Ping")
                        .setTarget(ColorUtil.wrapWithColorTag(label, Color.CYAN))
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> {
                            log.debug("Ping: NPC 'Ping' entry clicked for {}", label);
                            pingManager.startNpcPing(npc, config);
                        });
                return;
            }

            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            Widget worldMapWidget = client.getWidget(WorldMapCoordinates.WORLD_MAP_VIEW_WIDGET_ID);
            if (worldMapWidget != null && !worldMapWidget.isHidden()
                    && worldMapWidget.getBounds().contains(mouse.getX(), mouse.getY())) {
                WorldPoint worldMapPoint = WorldMapCoordinates.canvasPointToWorldPoint(client, mouse);
                if (worldMapPoint != null) {
                    log.debug("Ping: adding world-map 'Ping' menu entry at {}", worldMapPoint);
                    client.createMenuEntry(-1)
                            .setOption("Ping")
                            .setTarget(ColorUtil.wrapWithColorTag("here", Color.CYAN))
                            .setType(MenuAction.RUNELITE)
                            .onClick(e -> pingManager.startTilePing(worldMapPoint, config));
                } else {
                    log.debug("Ping: over world-map widget but canvasPointToWorldPoint resolved null");
                }
                return;
            }

            Tile tile = client.getSelectedSceneTile();
            WorldPoint tileWorldPoint = tile != null ? tile.getWorldLocation() : null;
            if (tileWorldPoint != null) {
                log.debug("Ping: adding tile 'Ping' menu entry at {}", tileWorldPoint);
                client.createMenuEntry(-1)
                        .setOption("Ping")
                        .setTarget(ColorUtil.wrapWithColorTag("here", Color.CYAN))
                        .setType(MenuAction.RUNELITE)
                        .onClick(e -> pingManager.startTilePing(tileWorldPoint, config));
            } else {
                log.debug("Ping: no NPC/world-map/tile target found for this menu (tile={})", tile);
            }
        } catch (Exception e) {
            log.warn("Failed to add ping menu entry", e);
        }
    }

    /** RuneLite's Client.isKeyPressed uses its own internal key-state array, not
     * java.awt.event.KeyEvent codes - 81 is shift there (same value core plugins like
     * GroundItems/GroundMarkers/MenuEntrySwapper use for their own shift checks). */
    private static final int RUNELITE_VK_SHIFT = 81;

    private boolean isShiftHeld() {
        return client.isKeyPressed(RUNELITE_VK_SHIFT);
    }

    private NPC findMenuNpc(MenuEntry[] entries) {
        if (entries == null) return null;
        for (MenuEntry entry : entries) {
            if (entry.getNpc() != null) {
                return entry.getNpc();
            }
        }
        return null;
    }

    /**
     * Adds a "Raid Markers" submenu (one child entry per {@link RaidMarkerType}) to whichever
     * target applies to this menu - same NPC/world-map-widget/scene-tile priority order as
     * {@link #addPingMenuEntry} - plus a single "Clear my raid markers here" entry if the local
     * player already has any marker active on that exact target. Only shown on shift-right-click,
     * so a plain right-click's menu stays uncluttered.
     */
    private void addRaidMarkerMenuEntries(MenuOpened event) {
        if (!config.raidMarkersEnabled() || raidMarkerManager == null || !isShiftHeld()) return;

        try {
            NPC npc = findMenuNpc(event.getMenuEntries());
            if (npc != null) {
                addRaidMarkerEntriesForNpc(npc);
                return;
            }

            net.runelite.api.Point mouse = client.getMouseCanvasPosition();
            Widget worldMapWidget = client.getWidget(WorldMapCoordinates.WORLD_MAP_VIEW_WIDGET_ID);
            if (worldMapWidget != null && !worldMapWidget.isHidden()
                    && worldMapWidget.getBounds().contains(mouse.getX(), mouse.getY())) {
                WorldPoint worldMapPoint = WorldMapCoordinates.canvasPointToWorldPoint(client, mouse);
                if (worldMapPoint != null) {
                    addRaidMarkerEntriesForTile(worldMapPoint);
                }
                return;
            }

            Tile tile = client.getSelectedSceneTile();
            WorldPoint tileWorldPoint = tile != null ? tile.getWorldLocation() : null;
            if (tileWorldPoint != null) {
                addRaidMarkerEntriesForTile(tileWorldPoint);
            }
        } catch (Exception e) {
            log.warn("Failed to add raid marker menu entries", e);
        }
    }

    private void addRaidMarkerEntriesForNpc(NPC npc) {
        if (!raidMarkerManager.ownMarkerTypesOnNpc(npc).isEmpty()) {
            addClearRaidMarkersEntry(() -> raidMarkerManager.clearOwnMarkersOnNpc(npc, config));
        }

        String label = npc.getName() != null ? npc.getName() : "NPC";
        MenuEntry parent = client.createMenuEntry(-1)
                .setOption("Raid Markers")
                .setTarget(ColorUtil.wrapWithColorTag(label, Color.CYAN))
                .setType(MenuAction.RUNELITE);
        Menu submenu = parent.createSubMenu();
        RaidMarkerType[] types = RaidMarkerType.values();
        for (int i = types.length - 1; i >= 0; i--) {
            RaidMarkerType type = types[i];
            if (!isRaidMarkerMenuVisible(type)) continue;
            submenu.createMenuEntry(-1)
                    .setOption(type.displayName)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> raidMarkerManager.dropNpcMarker(type, npc, config));
        }
    }

    private void addRaidMarkerEntriesForTile(WorldPoint worldPoint) {
        if (!raidMarkerManager.ownMarkerTypesOnTile(worldPoint).isEmpty()) {
            addClearRaidMarkersEntry(() -> raidMarkerManager.clearOwnMarkersOnTile(worldPoint, config));
        }

        MenuEntry parent = client.createMenuEntry(-1)
                .setOption("Raid Markers")
                .setTarget(ColorUtil.wrapWithColorTag("here", Color.CYAN))
                .setType(MenuAction.RUNELITE);
        Menu submenu = parent.createSubMenu();
        RaidMarkerType[] types = RaidMarkerType.values();
        for (int i = types.length - 1; i >= 0; i--) {
            RaidMarkerType type = types[i];
            if (!isRaidMarkerMenuVisible(type)) continue;
            submenu.createMenuEntry(-1)
                    .setOption(type.displayName)
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> raidMarkerManager.dropTileMarker(type, worldPoint, config));
        }
    }

    private void addClearRaidMarkersEntry(Runnable onClear) {
        client.createMenuEntry(-1)
                .setOption("Clear my raid markers here")
                .setType(MenuAction.RUNELITE)
                .onClick(e -> onClear.run());
    }

    /**
     * Whether {@code type} should appear in the local player's own "Raid Markers" submenu, per the
     * per-type checkboxes in {@link GroupScapeTrackerConfig}'s "Raid Marker Types" sections. This
     * only ever hides entries from this player's own menu - it has no effect on rendering other
     * group members' markers of that type.
     */
    private boolean isRaidMarkerMenuVisible(RaidMarkerType type) {
        switch (type) {
            case DANGER: return config.showMenuDanger();
            case DEFEND: return config.showMenuDefend();
            case LOOT: return config.showMenuLoot();
            case FOCUS: return config.showMenuFocus();
            case ONE: return config.showMenuOne();
            case TWO: return config.showMenuTwo();
            case THREE: return config.showMenuThree();
            case FOUR: return config.showMenuFour();
            case A: return config.showMenuA();
            case B: return config.showMenuB();
            case C: return config.showMenuC();
            case D: return config.showMenuD();
            default: return true;
        }
    }

    /** Called by {@link RosterClient} when any group member's ping (including the local player's
     * own) starts - posts the chat line the ping spec calls for, regardless of who pinged. */
    private void onGroupPingStart(com.groupscape.roster.RosterWireTypes.PingStartPayload payload) {
        String where = payload.npcName != null ? payload.npcName : "(" + payload.x + ", " + payload.y + ")";
        sendChatMessage(payload.memberName + " pinged " + where);
    }

    private void handleCopyName(String name) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(name), null);
        sendChatMessage("Copied \"" + name + "\" to clipboard.");
    }

    private static boolean isGameObjectAction(MenuAction menuAction) {
        return menuAction == MenuAction.GAME_OBJECT_FIRST_OPTION
                || menuAction == MenuAction.GAME_OBJECT_SECOND_OPTION
                || menuAction == MenuAction.GAME_OBJECT_THIRD_OPTION
                || menuAction == MenuAction.GAME_OBJECT_FOURTH_OPTION
                || menuAction == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    /**
     * {@code Actor.getInteracting()} (used for NPC/combat presence, see {@link #updateInteracting})
     * never fires for clicking a game object - a Wintertodt brazier, a Guardians of the Rift
     * portal, a fishing spot - so this is the only signal available for object interactions.
     * Fires on the click itself rather than any confirmation the action completed, matching
     * this class's other best-effort event captures.
     */
    private void recordObjectInteraction(MenuOptionClicked event) {
        if (doNotUseThisData()) return;

        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null) return;

        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return;

        String objectName = Text.removeTags(event.getMenuTarget());
        dataManager.getObjectInteractionEvents().onObjectInteraction(
                local.getName(), event.getId(), objectName, event.getMenuOption(),
                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
    }

    /**
     * Called by {@link RosterClient} when another group member's kill arrives over the party
     * overlay WebSocket. The local player's own kills are skipped here since the game already
     * shows them a message.
     */
    private void onGroupKillEvent(String memberName, String npcName) {
        if (!config.notifyBossKill()) return;
        Player local = client.getLocalPlayer();
        if (local != null && memberName.equalsIgnoreCase(local.getName())) return;
        sendChatMessage(memberName + " killed " + npcName + "!");
    }

    private static final Color GROUPSCAPE_CHAT_COLOR = new Color(170, 0, 255);

    /**
     * Spec §2's "Inline in the Clan chat tab" surface: printed for every incoming
     * {@code !gs} message (including the sender's own, since the server is the single source
     * of truth and there's no optimistic local echo elsewhere). Live-only - no backfill here,
     * since {@link ChatMessageManager} has no GroupScape-owned scrollback to replay into.
     * Uses {@link ChatMessageType#CLAN_MESSAGE} (a system-style line routed to the Clan tab)
     * rather than an actual Clan Chat channel message, same as CONSOLE was used for the Game
     * tab - no real clan membership required.
     */
    private void printInlineChatMessage(com.groupscape.roster.RosterWireTypes.ChatMessagePayload payload) {
        if (!config.chatInlineEnabled()) return;

        String displayName = payload.memberName != null ? payload.memberName : "Unknown";
        RosterMember member = rosterState.findByName(displayName);
        Color nameColor = member != null
                ? com.groupscape.sidepanel.SidePanelTheme.memberColor(member.color)
                : GROUPSCAPE_CHAT_COLOR;

        String formatted = new ChatMessageBuilder()
                .append(GROUPSCAPE_CHAT_COLOR, "[GS] ")
                .append(nameColor, displayName)
                .append(ChatColorType.NORMAL)
                .append(": " + payload.text)
                .build();

        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CLAN_MESSAGE)
                .runeLiteFormattedMessage(formatted)
                .build());
    }

    private void sendChatMessage(String message) {
        String prefixed = ColorUtil.wrapWithColorTag("[gs] " + message, GROUPSCAPE_CHAT_COLOR);
        chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(prefixed)
                .build());
    }

    /**
     * Fires once when HP ratio drops below {@link #LOW_HP_ALERT_THRESHOLD}, then re-arms only
     * once it recovers past {@link #LOW_HP_REARM_THRESHOLD} - the gap between the two thresholds
     * is hysteresis so HP oscillating right at the alert line (poison, repeated small hits)
     * doesn't fire a fresh alert on every tick.
     */
    private void checkLowHpAlert(String playerName, int currentHp, int maxHp) {
        if (maxHp <= 0) return;
        double ratio = (double) currentHp / maxHp;

        if (lowHpAlertArmed && ratio < LOW_HP_ALERT_THRESHOLD) {
            lowHpAlertArmed = false;
            Player local = client.getLocalPlayer();
            WorldPoint wp = local == null ? null : local.getWorldLocation();
            int worldX = wp == null ? -1 : wp.getX();
            int worldY = wp == null ? -1 : wp.getY();
            int plane = wp == null ? -1 : wp.getPlane();
            dataManager.getAlertEvents().onLowHp(playerName, currentHp, maxHp, worldX, worldY, plane, client.getWorld());
        } else if (!lowHpAlertArmed && ratio >= LOW_HP_REARM_THRESHOLD) {
            lowHpAlertArmed = true;
        }
    }

    /**
     * {@code Varbits.IN_WILDERNESS} holds the current wilderness level (0 outside it) - the same
     * value the in-game wilderness widget displays - so polling it once per tick here is simpler
     * than filtering {@code VarbitChanged} for the one varbit we care about.
     */
    private void checkWildernessEntry() {
        if (doNotUseThisData()) return;

        int wildernessLevel = client.getVarbitValue(Varbits.IN_WILDERNESS);
        boolean nowInWilderness = wildernessLevel > 0;

        if (nowInWilderness && !wasInWilderness) {
            Player local = client.getLocalPlayer();
            WorldPoint wp = local == null ? null : local.getWorldLocation();
            if (local != null && local.getName() != null && wp != null) {
                dataManager.getAlertEvents().onWildernessEntry(
                        local.getName(), wildernessLevel, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
            }
        }
        wasInWilderness = nowInWilderness;
    }

    @Subscribe
    private void onInteractingChanged(InteractingChanged event) {
        if (event.getSource() != client.getLocalPlayer()) return;
        updateInteracting();
    }

    private void itemsMayHaveBeenDeposited() {
        // NOTE: In order to determine if an item has gone through the deposit box we first detect if any of the menu
        // actions were performed OR a custom amount was entered while the deposit box inventory widget was opened.
        // Then we allow up to two game ticks were an inventory changed event can occur and at that point we assume
        // it must have been caused by the action detected just before. We can't check the inventory at the time of
        // either interaction since the inventory may have not been updated yet. We also cannot just check that the deposit
        // box window is open in the item container event since it is possible for a player to close the widget before
        // the event handler is called.
        itemsDeposited = 2;
    }

    /**
     * No generic "NPC died" event exists in the RuneLite API - {@code getHealthRatio() == 0}
     * (live at despawn, or last recorded via {@link #npcHealthRatioByIndex} if the health bar
     * already hid by then) approximates it for every NPC, not just a curated boss list (that
     * allowlist was dropped: it silently discarded loot from any monster it didn't recognize,
     * which is most of normal play). Known gaps this doesn't special-case: gargoyles/rockslugs
     * (die above 0hp), KQ/Vet'ion (mid-fight transforms), Amoxliatl (non-standard health bar).
     * Dual-NPC encounters (Dusk/Dawn, Eldric the Ice King/Verak Lith) aren't combined either -
     * each half despawning at 0 health fires its own kill event.
     *
     * The Hunllef (both Gauntlet variants) is a confirmed instance of that "non-standard health
     * bar" gap - live testing showed its ratio never reads 0 here, at despawn or via any
     * hitsplat beforehand, so this silently drops every Gauntlet kill (and, since the reward
     * chest's loot is granted as this NPC's own {@code LootReceived} rather than a separate
     * chest event - see {@link #onLootReceived} - the reward loot along with it, with nothing
     * left to correlate it to). {@link #onChatMessage}'s {@code GAUNTLET_COMPLETE_PATTERN} handler
     * works around it by holding the "Your ... Gauntlet completion count is: N." line until the
     * reward chest's loot actually arrives, then synthesizing the kill via
     * {@link #claimPendingGauntletKill} right before it - the same trick
     * {@link RaidCompletionEvents} uses to sidestep unreliable despawn detection for CoX/ToA, just
     * triggered off loot arrival instead of the chat line itself, since unlike a raid's chest the
     * Gauntlet's reward chest may not be opened for a while after the completion line prints.
     */
    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        cacheNpcName(event.getNpc());
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        cacheNpcName(event.getNpc());
    }

    private void cacheNpcName(NPC npc) {
        String name = npc.getName();
        if (name != null) {
            npcNamesByIndex.put(npc.getIndex(), name);
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor actor = event.getActor();
        if (actor == client.getLocalPlayer()) {
            recordHitsplatAttacker(event.getHitsplat());
            return;
        }

        if (!(actor instanceof NPC)) return;
        NPC npc = (NPC) actor;
        int ratio = npc.getHealthRatio();
        if (ratio >= 0) {
            npcHealthRatioByIndex.put(npc.getIndex(), ratio);
        }
    }

    /**
     * Hitsplats carry no attacker field in the RuneLite API, so the closest available signal for
     * "what just hit me" is scanning nearby NPCs for one currently targeting the local player
     * ({@code npc.getInteracting() == local}) at the moment a damaging splat lands. Captured here
     * rather than continuously tracking {@code local.getInteracting()} (who the player is
     * attacking) so it still attributes correctly when the player never targeted back - e.g. a
     * ranged/AoE boss special. Consumed by {@link #onActorDeath}.
     */
    private void recordHitsplatAttacker(Hitsplat hitsplat) {
        if (hitsplat.getHitsplatType() == HitsplatID.HEAL || hitsplat.getAmount() <= 0) return;

        Player local = client.getLocalPlayer();
        if (local == null) return;

        for (NPC npc : client.getNpcs()) {
            if (npc.getInteracting() == local && npc.getName() != null) {
                lastHitsplatAttackerName = npc.getName();
                return;
            }
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (pingManager != null) {
            pingManager.onNpcDespawned(event.getNpc(), config);
        }
        if (raidMarkerManager != null) {
            raidMarkerManager.onNpcDespawned(event.getNpc(), config);
        }

        if (doNotUseThisData()) return;

        NPC npc = event.getNpc();
        String name = npc.getName();
        if (name == null) {
            name = npcNamesByIndex.get(npc.getIndex());
        }
        npcNamesByIndex.remove(npc.getIndex());
        Integer lastKnownRatio = npcHealthRatioByIndex.remove(npc.getIndex());
        boolean diedAtZeroHp = npc.getHealthRatio() == 0 || (lastKnownRatio != null && lastKnownRatio == 0);
        if (name == null || !diedAtZeroHp) return;
        // The Hunllef is handled exclusively by onGauntletCompletion (see that method's javadoc) -
        // falling through here too, on the rare chance diedAtZeroHp does fire for it, would double
        // up the kill.
        if ("Corrupted Hunllef".equals(name) || "Crystalline Hunllef".equals(name)) return;
        // The Hueycoatl is handled exclusively by onLootReceived's synthesized kill (see
        // HUEYCOATL_NPC_NAME javadoc below) - its main body shares the name "The Hueycoatl" across
        // all combat forms (normal/shielded/defeated) and, per production kill data (21 real kills
        // in one session logged only 1 despawn here), essentially never reads health ratio 0 at
        // true death - the same "non-standard health bar" gap as the Hunllef (see this method's
        // javadoc). Meanwhile its tail genuinely despawns at 0hp once per attempt when broken to
        // lift the shield, which isn't a kill at all - letting it fall through here would
        // misreport every attempt's mid-fight tail break as a loot-less kill.
        if (HUEYCOATL_NPC_NAME.equals(name) || name.startsWith("Hueycoatl tail")) return;
        // Duke Sucellus is handled exclusively by onLootReceived's synthesized kill (see
        // DUKE_SUCELLUS_NPC_NAME javadoc below) - same "non-standard health bar" gap as the
        // Hunllef/Hueycoatl: he keels over and his corpse just sits there rather than despawning
        // at 0hp, so this never fires for his real death.
        if (DUKE_SUCELLUS_NPC_NAME.equals(name)) return;
        // Doom of Mokhaiotl is handled exclusively by onLootReceived's synthesized kill (see
        // DOOM_OF_MOKHAIOTL_NPC_NAME javadoc below) - it despawns once per delve level, but only
        // the final "claim reward and exit" should ever be logged as a kill, not each level along
        // the way. Falling through here would log one loot-less kill per level.
        if (DOOM_OF_MOKHAIOTL_NPC_NAME.equals(name)) return;

        WorldPoint wp = npc.getWorldLocation();
        if (wp == null) return;

        String playerName = client.getLocalPlayer().getName();
        dataManager.getKillLootDeathEvents().onKill(playerName, npc.getId(), name, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
        // Alongside (not instead of) the ordinary kill above - see ComboKillEvents' class doc for
        // why Barrows brothers/Moons of Peril moons still get their own silent kill on top of the
        // combined entry this contributes to.
        dataManager.getComboKillEvents().onSubBossDespawned(name);
    }

    /**
     * {@code LootReceived} is RuneLite's own client-side wrapper around the authoritative
     * in-game loot-tracker script signal, preferred over a same-tick/same-tile ItemSpawned
     * correlation. NPC loot is also correlated to a pending kill best-effort by
     * {@link KillLootDeathEvents#onLoot}. Chest/instance rewards (raids, Barrows, Gauntlet, etc.
     * - {@link ChestLootSourceNames}) and clue scroll caskets ({@link ClueTier}) arrive as
     * {@code LootRecordType.EVENT}; unlike NPC loot there's no kill to correlate to, so they're
     * logged as their own standalone "loot" event via
     * {@link KillLootDeathEvents#onChestOrClueLoot}. PvP/pickpocket loot sources aren't logged at
     * all yet, but everything is still eligible for the notable-drop check below regardless.
     */
    /** The three raid reward-chest names - checked ahead of {@link ChestLootSourceNames}'s
     * broader chest handling in {@link #onLootReceived} so raid chest loot is claimed by
     * {@link RaidCompletionEvents} instead of also being logged as a standalone chest "loot"
     * event (would otherwise double-report the same gold once folded into a raid completion). */
    private static final Set<String> RAID_CHEST_NAMES = new HashSet<>(java.util.Arrays.asList(
            "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"));

    /** Short combo-entry label per brother, keyed by the varbit that flags them dead this run -
     * see {@link #barrowsVerifiedKillLabels} and {@link ComboKillEvents}' class doc for why this
     * replaces despawn-tracked detection for Barrows specifically. */
    private static final Map<Integer, String> BARROWS_KILLED_VARBIT_LABELS = new LinkedHashMap<>();
    static {
        BARROWS_KILLED_VARBIT_LABELS.put(VarbitID.BARROWS_KILLED_DHAROK, "Dharok");
        BARROWS_KILLED_VARBIT_LABELS.put(VarbitID.BARROWS_KILLED_AHRIM, "Ahrim");
        BARROWS_KILLED_VARBIT_LABELS.put(VarbitID.BARROWS_KILLED_GUTHAN, "Guthan");
        BARROWS_KILLED_VARBIT_LABELS.put(VarbitID.BARROWS_KILLED_KARIL, "Karil");
        BARROWS_KILLED_VARBIT_LABELS.put(VarbitID.BARROWS_KILLED_TORAG, "Torag");
        BARROWS_KILLED_VARBIT_LABELS.put(VarbitID.BARROWS_KILLED_VERAC, "Verac");
    }

    /** Which brothers the game itself says died this run, per {@code VarbitID.BARROWS_KILLED_*}
     * (each 0/1, cleared on entering the tunnels and set as each brother dies) - authoritative,
     * unlike {@link ComboKillEvents#onSubBossDespawned}'s despawn/health-ratio tracking, which
     * production data showed silently missing some brothers (e.g. a run logging only 3 of 6
     * killed) the same way it does for the Hunllef/Hueycoatl. Read at chest-loot time in
     * {@link #onLootReceived} rather than cached earlier, since that's the one moment all six are
     * guaranteed to have already been resolved for this run. */
    private List<String> barrowsVerifiedKillLabels() {
        List<String> labels = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : BARROWS_KILLED_VARBIT_LABELS.entrySet()) {
            if (client.getVarbitValue(entry.getKey()) != 0) labels.add(entry.getValue());
        }
        return labels;
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        boolean claimedByRaidCompletion = false;
        if (event.getType() == LootRecordType.NPC) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemStack item : event.getItems()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("itemId", itemManager.canonicalize(item.getId()));
                entry.put("quantity", item.getQuantity());
                items.add(entry);
            }
            claimPendingGauntletKill(event.getName());
            claimHueycoatlKill(event.getName());
            claimDukeSucellusKill(event.getName());
            claimDoomOfMokhaiotlKill(event.getName());
            dataManager.getKillLootDeathEvents().onLoot(event.getName(), items);
        } else if (event.getType() == LootRecordType.EVENT) {
            String clueTier = ClueTier.extractTier(event.getName());
            boolean isClue = clueTier != null;
            boolean isRaidChest = !isClue && RAID_CHEST_NAMES.contains(event.getName());
            // Checked ahead of ChestLootSourceNames' broader chest handling for the same reason as
            // isRaidChest above - a combo chest's loot is claimed by ComboKillEvents instead of
            // also being logged as a standalone chest "loot" event.
            boolean isComboChest = !isClue && !isRaidChest && ComboKillEvents.isComboChestName(event.getName());
            boolean isChest = !isClue && !isRaidChest && !isComboChest && ChestLootSourceNames.isTrackedChest(event.getName());
            if (isRaidChest || isClue || isComboChest || isChest) {
                Player local = client.getLocalPlayer();
                WorldPoint wp = local == null ? null : local.getWorldLocation();
                if (wp != null) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (ItemStack item : event.getItems()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("itemId", itemManager.canonicalize(item.getId()));
                        entry.put("quantity", item.getQuantity());
                        items.add(entry);
                    }
                    if (isRaidChest) {
                        claimedByRaidCompletion = dataManager.getRaidCompletionEvents().onRaidChestLoot(
                                local.getName(), event.getName(),
                                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld(), items);
                    } else if (isComboChest) {
                        List<String> verifiedLabels = "Barrows".equals(event.getName()) ? barrowsVerifiedKillLabels() : null;
                        dataManager.getComboKillEvents().onComboChestLoot(
                                local.getName(), event.getName(), wp.getX(), wp.getY(), wp.getPlane(), client.getWorld(),
                                items, dataManager.getKillLootDeathEvents(), verifiedLabels);
                    } else {
                        dataManager.getKillLootDeathEvents().onChestOrClueLoot(
                                local.getName(), isClue ? "clue" : "chest", event.getName(), clueTier,
                                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld(), items);
                    }
                }
            }
        }

        // Raid chest gold already gets its own total on the raid-completion entry - reporting it
        // again here would double-count the same drop across two activity feed entries.
        if (!claimedByRaidCompletion) {
            checkNotableDrop(event.getType(), event.getName(), event.getItems());
        }
    }

    /**
     * CoX and ToA print a chat line on reward-chest interaction; ToB doesn't (its completion is
     * inferred straight from the reward-chest {@code LootReceived} in {@link #onLootReceived}
     * instead - see {@link RaidCompletionEvents#onRaidChestLoot}).
     *
     * Wording/casing confirmed against live completion messages: regular and Challenge Mode CoX,
     * and regular ToA (Expert Mode wording assumed by analogy, not yet confirmed live).
     */
    private static final Pattern COX_COMPLETE_PATTERN = Pattern.compile(
            "^Your completed Chambers of Xeric(?: (?<mode>Challenge Mode))? count is: [\\d,]+\\.$");
    private static final Pattern TOA_COMPLETE_PATTERN = Pattern.compile(
            "^Your completed Tombs of Amascut(?: (?<mode>Expert Mode))? count is: [\\d,]+\\.$");

    /** e.g. "Your Zulrah kill count is: 45." - the account's real, authoritative KC, as opposed
     * to how many times this server has happened to see the boss's kill logged. */
    private static final Pattern KILL_COUNT_PATTERN = Pattern.compile(
            "^Your (?<boss>.+?) kill count is: (?<kc>[\\d,]+)\\.$");

    /** e.g. "Your Corrupted Gauntlet completion count is: 21." / "Your Gauntlet completion count
     * is: 8." - see the {@link #onNpcDespawned} javadoc for why the Hunllef needs this instead of
     * ordinary despawn detection. */
    private static final Pattern GAUNTLET_COMPLETE_PATTERN = Pattern.compile(
            "^Your (?<corrupted>Corrupted )?Gauntlet completion count is: (?<kc>[\\d,]+)\\.$");
    private static final int CRYSTALLINE_HUNLLEF_NPC_ID = 9021;
    private static final int CORRUPTED_HUNLLEF_NPC_ID = 9038;
    /** Generous on purpose - the reward chest sits in its own room and there's nothing forcing
     * you to open it immediately, unlike normal boss loot which lands within a second or two of
     * the kill. You can't re-enter/re-fight without opening it first though, so it's always
     * exactly one chest per completion - no risk of this matching the wrong run. */
    private static final long GAUNTLET_LOOT_CORRELATION_WINDOW_MILLIS = 10 * 60 * 1000L;
    private String pendingGauntletNpcName;
    private int pendingGauntletNpcId;
    private Integer pendingGauntletKc;
    private long pendingGauntletDetectedAtMillis;

    /** Shared across all of the Hueycoatl's main-body combat forms (normal/shielded/defeated) -
     * see {@link #onNpcDespawned}'s javadoc for why its kill can't be detected there. */
    private static final String HUEYCOATL_NPC_NAME = "The Hueycoatl";
    /** The "Defeated" composition id, used only as the kill event's recorded npcId - detection
     * itself doesn't depend on it. */
    private static final int HUEYCOATL_NPC_ID = 14012;
    /** A real kill is at least ~20s apart (fastest recorded full-team kill); Hueycoatl's unique +
     * tertiary loot rolls (see runelite/runelite#19813) fire as separate same-tick LootReceived
     * calls for one kill. Any "The Hueycoatl" loot arriving within this window of the last
     * synthesized kill is treated as another roll from that same kill, not a new one. */
    private static final long HUEYCOATL_KILL_DEBOUNCE_MILLIS = 5000L;
    private long lastHueycoatlKillMillis = 0;

    /** See {@link #onNpcDespawned}'s javadoc for why Duke Sucellus's kill can't be detected there -
     * he keels over on death rather than despawning at 0hp, so the corpse just sits there until
     * the room resets. */
    private static final String DUKE_SUCELLUS_NPC_NAME = "Duke Sucellus";
    /** Used only as the kill event's recorded npcId - detection itself doesn't depend on it. */
    private static final int DUKE_SUCELLUS_NPC_ID = 12191;
    /** Same reasoning as {@link #HUEYCOATL_KILL_DEBOUNCE_MILLIS} - guards against a multi-roll
     * loot drop being mistaken for a second kill. */
    private static final long DUKE_SUCELLUS_KILL_DEBOUNCE_MILLIS = 5000L;
    private long lastDukeSucellusKillMillis = 0;

    /** See {@link #onNpcDespawned}'s javadoc for why Doom of Mokhaiotl's kill can't be logged
     * there - it despawns once per delve level, but a delve run should only ever produce one
     * activity feed/loot log entry, for the level the player was at when they claimed rewards and
     * left. */
    private static final String DOOM_OF_MOKHAIOTL_NPC_NAME = "Doom of Mokhaiotl";
    /** Used only as the kill event's recorded npcId - detection itself doesn't depend on it. */
    private static final int DOOM_OF_MOKHAIOTL_NPC_ID = 14707;
    /** Same reasoning as {@link #HUEYCOATL_KILL_DEBOUNCE_MILLIS} - guards against a multi-roll
     * loot drop being mistaken for a second kill. */
    private static final long DOOM_OF_MOKHAIOTL_KILL_DEBOUNCE_MILLIS = 5000L;
    private long lastDoomOfMokhaiotlKillMillis = 0;

    /** Widget group for the "Burrow hole" reward screen (shown after every delve level's kill,
     * whether the player then claims or descends further) - the only place the current delve
     * level is ever displayed, since despawn detection is deliberately suppressed for this boss
     * (see {@link #onNpcDespawned}). Not an official RuneLite constant; identified by inspecting
     * the live widget tree. */
    private static final int DOOM_OF_MOKHAIOTL_REWARD_WIDGET_GROUP = 919;
    /** Matches this widget's level/depth text (exact wording unverified against a live client -
     * kept permissive across "Level"/"Depth" phrasing so a wording tweak doesn't silently break
     * capture). */
    private static final Pattern DOOM_OF_MOKHAIOTL_LEVEL_PATTERN = Pattern.compile(
            "(?i)(?:delve )?(?:level|depth)\\s*:?\\s*(\\d+)");
    /** Level captured off the reward widget the last time it loaded - carried into whichever kill
     * {@link #claimDoomOfMokhaiotlKill} next synthesizes, then cleared. {@code null} if the
     * player has claimed rewards before the widget loaded, or the text didn't match
     * {@link #DOOM_OF_MOKHAIOTL_LEVEL_PATTERN} - the kill still ships without a level rather than
     * being dropped. */
    private Integer lastObservedDoomOfMokhaiotlDelveLevel;

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        String message = Text.removeTags(event.getMessage());

        Matcher cox = COX_COMPLETE_PATTERN.matcher(message);
        if (cox.find()) {
            dataManager.getRaidCompletionEvents().onChatCompletion(
                    "cox", RaidCompletionEvents.RaidDifficulty.mode(cox.group("mode")));
            return;
        }

        Matcher toa = TOA_COMPLETE_PATTERN.matcher(message);
        if (toa.find()) {
            dataManager.getRaidCompletionEvents().onChatCompletion(
                    "toa", RaidCompletionEvents.RaidDifficulty.level(resolveToaInvocationLevel()));
            return;
        }

        Matcher gauntlet = GAUNTLET_COMPLETE_PATTERN.matcher(message);
        if (gauntlet.find()) {
            boolean corrupted = gauntlet.group("corrupted") != null;
            pendingGauntletNpcName = corrupted ? "Corrupted Hunllef" : "Crystalline Hunllef";
            pendingGauntletNpcId = corrupted ? CORRUPTED_HUNLLEF_NPC_ID : CRYSTALLINE_HUNLLEF_NPC_ID;
            pendingGauntletKc = Integer.parseInt(gauntlet.group("kc").replace(",", ""));
            pendingGauntletDetectedAtMillis = System.currentTimeMillis();
            return;
        }

        Matcher kc = KILL_COUNT_PATTERN.matcher(message);
        if (kc.find()) {
            int accountKc = Integer.parseInt(kc.group("kc").replace(",", ""));
            dataManager.getKillLootDeathEvents().onKillCount(kc.group("boss"), accountKc);
        }
    }

    /**
     * The Gauntlet's reward chest grants its loot as the Hunllef's own {@code LootReceived}
     * (verified live - see {@link #onNpcDespawned}'s javadoc), so this is really correlating a
     * kill to loot exactly like {@link KillLootDeathEvents#onLoot} always does - it just can't use
     * that class's normal pending-kill queue, since that drains every second
     * ({@link DataManager#submitToApi()}) while the chest might not be opened for minutes. Instead
     * the completion chat line is held here until this NPC's loot actually arrives, at which point
     * the kill is synthesized right before the matching {@link KillLootDeathEvents#onLoot} call
     * below so they land in the same upload with no drain race possible.
     *
     * @return true if {@code npcName} matched a pending completion (caller should still make its
     * normal {@code onLoot} call immediately after - this only creates the kill half)
     */
    private boolean claimPendingGauntletKill(String npcName) {
        if (pendingGauntletNpcName == null || !pendingGauntletNpcName.equals(npcName)) return false;
        if (System.currentTimeMillis() - pendingGauntletDetectedAtMillis > GAUNTLET_LOOT_CORRELATION_WINDOW_MILLIS) {
            pendingGauntletNpcName = null;
            pendingGauntletKc = null;
            return false;
        }

        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (local == null || local.getName() == null || wp == null) return false;

        dataManager.getKillLootDeathEvents().onKill(
                local.getName(), pendingGauntletNpcId, npcName, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
        if (pendingGauntletKc != null) {
            dataManager.getKillLootDeathEvents().onKillCount(npcName, pendingGauntletKc);
        }
        pendingGauntletNpcName = null;
        pendingGauntletKc = null;
        return true;
    }

    /**
     * Synthesizes a Hueycoatl kill the moment its loot arrives, since despawn detection can't
     * catch it (see {@link #onNpcDespawned}'s javadoc) - a no-op unless {@code npcName} is
     * {@link #HUEYCOATL_NPC_NAME}. Debounced against {@link #HUEYCOATL_KILL_DEBOUNCE_MILLIS} so
     * a kill's second/third loot roll (unique + tertiary tables can both fire) doesn't get
     * mistaken for a new kill; the caller's own {@code onLoot} call right after this one attaches
     * every roll to whichever kill (this one, or one already pending) it belongs to.
     */
    private void claimHueycoatlKill(String npcName) {
        if (!HUEYCOATL_NPC_NAME.equals(npcName)) return;
        long now = System.currentTimeMillis();
        if (now - lastHueycoatlKillMillis < HUEYCOATL_KILL_DEBOUNCE_MILLIS) return;
        lastHueycoatlKillMillis = now;

        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (local == null || local.getName() == null || wp == null) return;

        dataManager.getKillLootDeathEvents().onKill(
                local.getName(), HUEYCOATL_NPC_ID, npcName, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
    }

    /**
     * Synthesizes a Duke Sucellus kill the moment his loot arrives, since despawn detection can't
     * catch it (see {@link #onNpcDespawned}'s javadoc) - a no-op unless {@code npcName} is
     * {@link #DUKE_SUCELLUS_NPC_NAME}. Debounced against
     * {@link #DUKE_SUCELLUS_KILL_DEBOUNCE_MILLIS} for the same reason as
     * {@link #claimHueycoatlKill}.
     */
    private void claimDukeSucellusKill(String npcName) {
        if (!DUKE_SUCELLUS_NPC_NAME.equals(npcName)) return;
        long now = System.currentTimeMillis();
        if (now - lastDukeSucellusKillMillis < DUKE_SUCELLUS_KILL_DEBOUNCE_MILLIS) return;
        lastDukeSucellusKillMillis = now;

        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (local == null || local.getName() == null || wp == null) return;

        dataManager.getKillLootDeathEvents().onKill(
                local.getName(), DUKE_SUCELLUS_NPC_ID, npcName, wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
    }

    /**
     * Synthesizes the one Doom of Mokhaiotl kill for a delve run the moment its reward loot
     * arrives (i.e. the player chose "Claim reward and exit" rather than descending further) -
     * a no-op unless {@code npcName} is {@link #DOOM_OF_MOKHAIOTL_NPC_NAME}. Debounced against
     * {@link #DOOM_OF_MOKHAIOTL_KILL_DEBOUNCE_MILLIS} for the same reason as
     * {@link #claimHueycoatlKill}. Attaches whatever level {@link #captureDoomOfMokhaiotlDelveLevel}
     * last observed, then clears it so a stale level can't leak into a future run.
     */
    private void claimDoomOfMokhaiotlKill(String npcName) {
        if (!DOOM_OF_MOKHAIOTL_NPC_NAME.equals(npcName)) return;
        long now = System.currentTimeMillis();
        if (now - lastDoomOfMokhaiotlKillMillis < DOOM_OF_MOKHAIOTL_KILL_DEBOUNCE_MILLIS) return;
        lastDoomOfMokhaiotlKillMillis = now;

        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (local == null || local.getName() == null || wp == null) return;

        dataManager.getKillLootDeathEvents().onDoomOfMokhaiotlKill(
                local.getName(), DOOM_OF_MOKHAIOTL_NPC_ID, npcName, lastObservedDoomOfMokhaiotlDelveLevel,
                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
        lastObservedDoomOfMokhaiotlDelveLevel = null;
    }

    /**
     * Scans the Doom of Mokhaiotl reward widget's text for the current delve level, storing it in
     * {@link #lastObservedDoomOfMokhaiotlDelveLevel} for {@link #claimDoomOfMokhaiotlKill} to pick
     * up. This widget reloads at every level (whether the player then claims or descends further),
     * so the stored value always reflects the most recent level by the time a claim happens.
     */
    private void captureDoomOfMokhaiotlDelveLevel() {
        Widget root = client.getWidget(DOOM_OF_MOKHAIOTL_REWARD_WIDGET_GROUP, 0);
        if (root == null) return;
        Integer level = findDoomOfMokhaiotlLevelInWidgetTree(root);
        if (level != null) {
            lastObservedDoomOfMokhaiotlDelveLevel = level;
        }
    }

    private Integer findDoomOfMokhaiotlLevelInWidgetTree(Widget widget) {
        if (widget == null) return null;

        String text = widget.getText();
        if (text != null && !text.isEmpty()) {
            Matcher m = DOOM_OF_MOKHAIOTL_LEVEL_PATTERN.matcher(Text.removeTags(text));
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    // Fall through and keep searching the rest of the tree.
                }
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                Integer found = findDoomOfMokhaiotlLevelInWidgetTree(child);
                if (found != null) return found;
            }
        }
        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                Integer found = findDoomOfMokhaiotlLevelInWidgetTree(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * The numeric ToA invocation level isn't carried by the completion chat line (it only ever
     * names a coarse mode), so it's read straight from {@link Varbits#TOA_RAID_LEVEL} instead -
     * this tracks the party's raid level (base 100 + invocation points) for the raid just
     * finished and is still set when the completion chat line fires. Falls back to -1 ("unknown
     * level") if read outside a raid, where the varbit is 0.
     */
    private int resolveToaInvocationLevel() {
        int level = client.getVarbitValue(Varbits.TOA_RAID_LEVEL);
        return level > 0 ? level : -1;
    }

    /**
     * Flags any loot batch whose total GE value crosses
     * {@link GroupScapeTrackerConfig#notableDropThreshold()} for a group chat broadcast,
     * regardless of {@link LootRecordType} - unlike {@link KillLootDeathEvents#onLoot}, this
     * isn't limited to NPC kills. Sending is unconditional once the threshold is crossed
     * (matches {@link #onGroupKillEvent}'s "your own kills always get broadcast" behavior);
     * {@link GroupScapeTrackerConfig#notifyNotableDrop()} only gates whether incoming broadcasts
     * get displayed, in {@link #onGroupDropEvent}.
     */
    private void checkNotableDrop(LootRecordType type, String sourceName, Collection<ItemStack> items) {
        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null) return;

        long totalValue = 0;
        ItemStack highlight = null;
        long highlightValue = -1;
        for (ItemStack item : items) {
            long value = (long) itemManager.getItemPrice(itemManager.canonicalize(item.getId())) * item.getQuantity();
            totalValue += value;
            if (value > highlightValue) {
                highlightValue = value;
                highlight = item;
            }
        }
        if (highlight == null || totalValue < config.notableDropThreshold()) return;

        int canonicalItemId = itemManager.canonicalize(highlight.getId());
        String itemName = itemManager.getItemComposition(highlight.getId()).getName();
        dataManager.getNotableDropEvents().onNotableDrop(
                local.getName(), notableDropSourceType(type), sourceName, canonicalItemId, itemName, highlightValue, totalValue);
    }

    private static String notableDropSourceType(LootRecordType type) {
        switch (type) {
            case NPC:
                return "kill";
            case PLAYER:
                return "pvp";
            case EVENT:
                return "chest";
            case PICKPOCKET:
                return "pickpocket";
            default:
                return "unknown";
        }
    }

    /**
     * Called by {@link RosterClient} when a group member's notable drop arrives over the party
     * overlay WebSocket - unlike {@link #onGroupKillEvent}, this also fires for the dropper's
     * own drop (nothing else tells them their own drop was notable), so there's no "skip if it's
     * me" guard here. {@code message} is already fully formatted server-side.
     */
    private void onGroupDropEvent(String memberName, String message) {
        if (!config.notifyNotableDrop()) return;
        sendChatMessage(message);
    }

    /**
     * {@code ActorDeath} filtered to the local player. Killer attribution is best-effort: primarily
     * {@link #lastHitsplatAttackerName} (the NPC that landed the most recent damaging hit - see
     * {@link #recordHitsplatAttacker}), falling back to {@code Actor.getInteracting()} for damage
     * sources that don't show up as an NPC hitsplat (e.g. environmental). Ported from
     * groupscape-old, since improved to use hitsplat-derived attribution instead of only
     * {@code getInteracting()}, which reflects who the player is targeting rather than who's
     * attacking them - wrong for ranged/AoE specials the player never targeted back.
     *
     * RuneLite's {@code ActorDeath} is driven off death animation IDs client-side and can fire
     * without an actual death (anim ID reuse, desync). Corroborate with the real hitpoints stat
     * before reporting, since that's server-authoritative. The hitpoints stat can lag the death
     * animation by a tick (the killing blow's hitsplat and the ActorDeath event can land before
     * the stat update is applied), so an instantaneous read here false-negatives on real deaths -
     * defer the check a couple of ticks via {@link #onGameTick} instead of reading it inline.
     */
    @Subscribe
    public void onActorDeath(ActorDeath event) {
        Player local = client.getLocalPlayer();
        if (event.getActor() != local) return;

        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return;

        Actor interacting = local.getInteracting();
        String killerName = lastHitsplatAttackerName != null
                ? lastHitsplatAttackerName
                : (interacting != null ? interacting.getName() : null);
        lastHitsplatAttackerName = null;
        // Live current-delve-level varp, not the post-kill reward widget scrape used for
        // claimDoomOfMokhaiotlKill - a death mid-fight never reaches that widget, so this is the
        // only source for "what level were they on" here. Reads 0 outside a delve run.
        int doomLevel = client.getVarpValue(VarPlayerID.DOM_CURRENT_LEVEL_TEMP);
        Integer doomDelveLevel = doomLevel > 0 ? doomLevel : null;
        pendingDeathCandidate = new PendingDeathCandidate(
                local.getName(), wp.getX(), wp.getY(), wp.getPlane(), client.getWorld(), killerName, doomDelveLevel);
        pendingDeathConfirmTicks = DEATH_CONFIRM_TICKS;
    }

    private void checkPendingDeathCandidate() {
        if (pendingDeathCandidate == null) return;
        if (--pendingDeathConfirmTicks > 0) return;

        PendingDeathCandidate candidate = pendingDeathCandidate;
        pendingDeathCandidate = null;
        if (client.getBoostedSkillLevel(Skill.HITPOINTS) > 0) return;

        dataManager.getKillLootDeathEvents().onDeath(candidate.playerName, candidate.x, candidate.y, candidate.plane,
                candidate.world, candidate.killerName, candidate.doomDelveLevel);
    }

    private static final class PendingDeathCandidate {
        final String playerName;
        final int x;
        final int y;
        final int plane;
        final int world;
        final String killerName;
        final Integer doomDelveLevel;

        PendingDeathCandidate(String playerName, int x, int y, int plane, int world, String killerName,
                               Integer doomDelveLevel) {
            this.playerName = playerName;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.world = world;
            this.killerName = killerName;
            this.doomDelveLevel = doomDelveLevel;
        }
    }

    /**
     * How long {@code getInteracting()} must stay null (outside of dialogue) before the plugin
     * actually pushes a "not interacting" clear - see {@link #notInteractingSinceMillis}. Without
     * this, bouncing in and out of melee range on a boss (a hit lands, target briefly nulls,
     * then re-engages) would flicker the target bar off and back on every time.
     */
    private static final long INTERACTING_CLEAR_DEBOUNCE_MILLIS = 10_000;

    /**
     * When {@code getInteracting()} first went null this "not interacting" streak, or 0 if the
     * player is currently interacting (or mid-dialogue). Reset the instant a real target
     * reappears, so a brief gap never accumulates toward the debounce below.
     */
    private long notInteractingSinceMillis;

    private void updateInteracting() {
        Player player = client.getLocalPlayer();
        if (player == null) return;

        String playerName = player.getName();
        Actor actor = player.getInteracting();
        if (actor != null) {
            notInteractingSinceMillis = 0;
            dataManager.getInteracting().update(new InteractingState(playerName, actor, client));

            if (actor instanceof NPC) {
                NPC npc = (NPC) actor;
                dataManager.getNpcDialogueTracker().observe(npc.getId(), npc.getName(), npc.getCombatLevel());
            }
        } else if (!isDialogueOpen()) {
            // getInteracting() also goes null the instant a dialogue box opens (see
            // NpcDialogueTracker) even though the player is still "talking to" whoever they
            // targeted, so only start the clear debounce once that box is actually closed too -
            // otherwise the target bar would blank out mid-conversation.
            if (notInteractingSinceMillis == 0) {
                notInteractingSinceMillis = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - notInteractingSinceMillis >= INTERACTING_CLEAR_DEBOUNCE_MILLIS) {
                dataManager.getInteracting().update(InteractingState.notInteracting(playerName));
            }
        }

        updateNpcDialogueEvent(player);
        captureSlayerRewardShopMaster();
    }

    /**
     * Tracks which master's Slayer Rewards shop is currently open, so
     * {@link #handleSlayerRewardShopConfirm} can attribute a block purchase made inside it to the
     * right master - see {@link #currentRewardShopMaster}'s javadoc for why this can't just reuse
     * {@link #currentSlayerTaskMaster} (the shop can be opened for any unlocked master, not only
     * whoever assigned the player's current task).
     */
    private void captureSlayerRewardShopMaster() {
        Widget main = client.getWidget(InterfaceID.SlayerRewards.MAIN);
        if (main == null || main.isHidden()) {
            currentRewardShopMaster = null;
            return;
        }
        if (currentRewardShopMaster == null && lastDialogNpcName != null
                && SLAYER_MASTER_NAMES.contains(lastDialogNpcName.toLowerCase())) {
            currentRewardShopMaster = lastDialogNpcName;
        }
    }

    /** Matches the Slayer Rewards shop's block-confirmation popup text (captured from
     * {@link net.runelite.api.gameval.InterfaceID.SlayerRewards#CONFIRM_TEXT}) to pull out which
     * task type is being blocked. Wording ported from observed client text ("...block X as a
     * possible slayer task?") - the in-game text has "slayer" between "possible" and "task", so
     * both must be optional independently rather than only "possible" being optional in front of a
     * literal "task"; verify against a live client if the OSRS client text ever changes and this
     * stops matching (falls through to a no-op, not a crash, if it doesn't match). */
    private static final Pattern SLAYER_REWARD_SHOP_BLOCK_PATTERN = Pattern.compile(
            "block\\s+(.+?)\\s+as\\s+a?\\s*(?:possible\\s+)?(?:slayer\\s+)?task", Pattern.CASE_INSENSITIVE);

    /**
     * Fires when the player confirms a purchase in the Slayer Rewards shop's confirmation popup.
     * Only "block a task type" purchases are of interest here (see
     * {@link #SLAYER_REWARD_SHOP_BLOCK_PATTERN}) - unlocking or extending a task type costs points
     * too, but doesn't produce a bogus/misattributed row the way blocking does (see
     * {@link #closeSlayerTask}'s javadoc), so those are left alone. This purchase has no task
     * assignment lifecycle of its own - it's independent of whatever task the player currently has
     * active - so it's emitted as a standalone, already-closed history event rather than going
     * through {@link #handleSlayerTaskTransition}/{@link #closeSlayerTask}.
     */
    private void handleSlayerRewardShopConfirm() {
        Widget confirmText = client.getWidget(InterfaceID.SlayerRewards.CONFIRM_TEXT);
        if (confirmText == null || confirmText.getText() == null) return;

        Matcher matcher = SLAYER_REWARD_SHOP_BLOCK_PATTERN.matcher(Text.removeTags(confirmText.getText()));
        if (!matcher.find()) return;
        String taskName = matcher.group(1).trim();
        if (taskName.isEmpty() || currentRewardShopMaster == null) return;

        Integer blockPrice = SLAYER_BLOCK_PRICE.get(currentRewardShopMaster.trim().toLowerCase());
        if (blockPrice == null) return;

        // See suppressNextSlayerTaskSnapshotRefresh's javadoc - guards against a corrupted
        // amountRemaining snapshot if this block un-assigns the currently tracked task and the
        // resulting varp writes land a tick apart.
        suppressNextSlayerTaskSnapshotRefresh = true;

        // If this confirmed block is for the task currently being tracked, tell closeSlayerTask
        // directly instead of leaving it to infer "blocked" from amountRemaining/pointsDelta - see
        // pendingCurrentTaskBlockName's javadoc for why that inference is fragile.
        if (currentSlayerTaskEventId != null && currentSlayerTaskLastSnapshot != null
                && taskName.equalsIgnoreCase(currentSlayerTaskLastSnapshot.taskName)) {
            pendingCurrentTaskBlockName = currentSlayerTaskLastSnapshot.taskName;
            pendingCurrentTaskBlockPrice = blockPrice;
            return;
        }

        Player local = client.getLocalPlayer();
        if (local == null || local.getName() == null) return;

        String now = Instant.now().toString();
        dataManager.getSlayerTaskCloseEvents().onTaskClosed(
                local.getName(),
                SlayerTaskCloseEvents.newClientEventId(),
                taskName,
                currentRewardShopMaster,
                "blocked",
                0,
                0,
                -blockPrice,
                now);
    }

    /**
     * Emits one "dialogue" interaction event per dialogue session (start), not once per tick
     * while the box stays open - {@code dialogueEventEmitted} dedupes across ticks and is reset
     * as soon as the dialogue widgets close.
     */
    private void updateNpcDialogueEvent(Player player) {
        NpcDialogueTracker tracker = dataManager.getNpcDialogueTracker();

        if (!isDialogueOpen()) {
            tracker.clear();
            dialogueEventEmitted = false;
            return;
        }

        captureSlayerTaskMasterDialogue();

        Integer npcId = tracker.lastNpcId();
        if (npcId == null || dialogueEventEmitted) return;

        WorldPoint wp = player.getWorldLocation();
        if (wp == null) return;

        dataManager.getInteractionEvents().onDialogue(player.getName(), npcId, tracker.lastNpcName(), tracker.lastCombatLevel(),
                wp.getX(), wp.getY(), wp.getPlane(), client.getWorld());
        dialogueEventEmitted = true;
    }

    /**
     * Captures whichever NPC is currently "speaking" in the left-hand dialogue box into
     * {@link #lastDialogNpcName}, for as long as that widget stays open. This is the only signal
     * available for naming a slayer task's assigning master - see {@link SlayerTaskState}'s
     * javadoc for why no varp/varbit/DB row can do it directly. Consumed (not read live) by
     * {@link #checkSlayerTaskUpdate} at the moment a task actually changes.
     *
     * <p>Also doubles as an immediate resync: if the NPC being talked to is a known slayer master
     * ({@link #SLAYER_MASTER_NAMES}), the master name is pushed right away instead of waiting for
     * {@link #checkSlayerTaskUpdate}'s task-change gate. That gate never fires on a plain re-check
     * of an already-assigned task (talking to the master and picking "check assignment" doesn't
     * change {@code SLAYER_TARGET}/{@code SLAYER_COUNT_ORIGINAL}), which otherwise left the master
     * name stuck null after a plugin/client restart mid-task until the next real reassignment.
     */
    private void captureSlayerTaskMasterDialogue() {
        Widget nameWidget = client.getWidget(InterfaceID.ChatLeft.NAME);
        if (nameWidget == null || nameWidget.isHidden() || nameWidget.getText() == null) return;

        String name = Text.removeTags(nameWidget.getText()).trim();
        if (name.isEmpty()) return;

        lastDialogNpcName = name;

        if (SLAYER_MASTER_NAMES.contains(name.toLowerCase())) {
            currentSlayerTaskMaster = name;

            Player local = client.getLocalPlayer();
            if (local != null && local.getName() != null) {
                pushSlayerTaskState(local.getName());
            }
        }
    }

    /**
     * The chat-dialogue box (NPC text or the option-select menu) being open is the one signal
     * that actually distinguishes "talking to" from "fighting" - both fire the same
     * {@code getInteracting()} target. Ported from groupscape-old's equivalent check.
     */
    private boolean isDialogueOpen() {
        Widget npcText = client.getWidget(InterfaceID.ChatLeft.TEXT);
        if (npcText != null && !npcText.isHidden()) {
            return true;
        }
        Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        return options != null && !options.isHidden();
    }

    private void updateDeposited(ItemContainerState newState, ItemContainerState previousState) {
        ItemContainerState deposited = newState.whatGotRemoved(previousState);
        dataManager.getDeposited().update(deposited);
    }

    private void updatePotionStorage() {
        Player player = client.getLocalPlayer();

        if (player != null) {
            PotionStorageState potionStorageState = PotionStorageState.fromClient(player.getName(), client, itemManager);
            dataManager.getPotionStorage().update(potionStorageState);
        }
    }

    private boolean doNotUseThisData() {
        return client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null;
    }

    @Provides
    GroupScapeTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GroupScapeTrackerConfig.class);
    }
}
