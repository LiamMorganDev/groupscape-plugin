package com.groupscape.roster;

import com.groupscape.GroupLinkListener;
import com.google.gson.Gson;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Real-time roster feed for the party overlay. Connects to the server's
 * `/api/characters/{accountHash}/ws` endpoint using the same API-key
 * `Authorization` header as the plugin's existing HTTP telemetry POSTs, gets
 * one `roster_snapshot` handshake, then applies `vitals_update` deltas as
 * they arrive. Auto-reconnects with a fixed delay on any close/failure.
 */
@Slf4j
public class RosterClient {
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final String ROSTER_SNAPSHOT = "roster_snapshot";
    private static final String VITALS_UPDATE = "vitals_update";
    private static final String KILL_EVENT = "kill_event";
    private static final String DROP_EVENT = "drop_event";
    private static final String COLOR_UPDATE = "color_update";
    private static final String PING_START = "ping_start";
    private static final String PING_UPDATE = "ping_update";
    private static final String PING_END = "ping_end";
    private static final String MARKER_START = "marker_start";
    private static final String MARKER_UPDATE = "marker_update";
    private static final String MARKER_END = "marker_end";
    private static final String CHAT_MESSAGE = "chat_message";

    /** Notified when another group member's kill arrives over the websocket. */
    public interface KillEventListener {
        void onKillEvent(String memberName, String npcName);
    }

    /** Notified when a group member's notable drop arrives over the websocket. */
    public interface DropEventListener {
        void onDropEvent(String memberName, String message);
    }

    /**
     * Notified as a group member's ping (right-click/hotkey on an NPC or tile) starts, moves
     * (live NPC tracking - see {@link com.groupscape.roster.PingManager}), or ends. Includes the
     * local player's own pings too, since the server relays everything it publishes back to every
     * connected overlay including the sender.
     */
    public interface PingEventListener {
        void onPingStart(RosterWireTypes.PingStartPayload payload);
        void onPingUpdate(RosterWireTypes.PingUpdatePayload payload);
        void onPingEnd(RosterWireTypes.PingEndPayload payload);
    }

    /**
     * Notified as a group member's raid marker (see {@link com.groupscape.roster.RaidMarkerManager})
     * starts, moves (live NPC tracking), or ends. Includes the local player's own markers too, same
     * as {@link PingEventListener}.
     */
    public interface RaidMarkerEventListener {
        void onMarkerStart(RosterWireTypes.RaidMarkerStartPayload payload);
        void onMarkerUpdate(RosterWireTypes.RaidMarkerUpdatePayload payload);
        void onMarkerEnd(RosterWireTypes.RaidMarkerEndPayload payload);
    }

    /** Notified as a group chat message (in-game {@code !gs} or webapp-sent) arrives - see the "!gs" chat spec §3. */
    public interface ChatEventListener {
        void onChatMessage(RosterWireTypes.ChatMessagePayload payload, String ts);
    }

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final RosterState rosterState;
    private final KillEventListener killEventListener;
    private final DropEventListener dropEventListener;
    private final PingEventListener pingEventListener;
    private final RaidMarkerEventListener raidMarkerEventListener;
    private final ChatEventListener chatEventListener;
    private final GroupLinkListener groupLinkListener;
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "groupscape-roster-reconnect");
                t.setDaemon(true);
                return t;
            });

    private WebSocket webSocket;
    private volatile boolean intentionallyClosed = true;
    private String connectedBaseUrl;
    private String connectedAccountHash;
    private String connectedApiKey;

    public RosterClient(OkHttpClient okHttpClient, Gson gson, RosterState rosterState, KillEventListener killEventListener,
                         DropEventListener dropEventListener, PingEventListener pingEventListener,
                         RaidMarkerEventListener raidMarkerEventListener, ChatEventListener chatEventListener,
                         GroupLinkListener groupLinkListener) {
        // Derived from the shared RuneLite client (never mutate that one - other plugins use it).
        // Without a ping interval, a half-open connection (e.g. the backend disappearing behind a
        // proxy/LB during a rebuild without sending a clean close) never fires onClosed/onFailure,
        // so scheduleReconnect() below never runs and the overlay stays dead until RuneLite restarts.
        this.okHttpClient = okHttpClient.newBuilder()
                .pingInterval(15, TimeUnit.SECONDS)
                .build();
        this.gson = gson;
        this.rosterState = rosterState;
        this.killEventListener = killEventListener;
        this.dropEventListener = dropEventListener;
        this.pingEventListener = pingEventListener;
        this.raidMarkerEventListener = raidMarkerEventListener;
        this.chatEventListener = chatEventListener;
        this.groupLinkListener = groupLinkListener;
    }

    /** No-op if already connected to this exact baseUrl+accountHash+apiKey. */
    public synchronized void connect(String baseUrl, String accountHash, String apiKey) {
        if (baseUrl == null || accountHash == null || apiKey == null || apiKey.trim().isEmpty()) {
            disconnect();
            return;
        }
        if (webSocket != null
                && baseUrl.equals(connectedBaseUrl)
                && accountHash.equals(connectedAccountHash)
                && apiKey.equals(connectedApiKey)) {
            return;
        }

        disconnect();
        intentionallyClosed = false;
        connectedBaseUrl = baseUrl;
        connectedAccountHash = accountHash;
        connectedApiKey = apiKey;
        openSocket(baseUrl, accountHash, apiKey);
    }

    public synchronized void disconnect() {
        intentionallyClosed = true;
        connectedBaseUrl = null;
        connectedAccountHash = null;
        connectedApiKey = null;
        if (webSocket != null) {
            webSocket.close(1000, "client disconnect");
            webSocket = null;
        }
        rosterState.clear();
    }

    public void shutdown() {
        disconnect();
        reconnectExecutor.shutdownNow();
    }

    private void openSocket(String baseUrl, String accountHash, String apiKey) {
        String wsUrl = toWebSocketUrl(baseUrl) + "/api/characters/" + accountHash + "/ws";
        Request request = new Request.Builder().url(wsUrl).header("Authorization", apiKey).build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("Party overlay WebSocket connected (accountHash={})", accountHash);
                groupLinkListener.onLinked();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("Party overlay WebSocket closed (accountHash={}, code={}, reason={})", accountHash, code, reason);
                scheduleReconnect(baseUrl, accountHash, apiKey);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.warn("Party overlay WebSocket failure (accountHash={}, httpStatus={}): {}",
                        accountHash, response != null ? response.code() : "n/a", t.toString());
                if (response != null && response.code() == 403) {
                    groupLinkListener.onLinkRequired();
                }
                scheduleReconnect(baseUrl, accountHash, apiKey);
            }
        });
    }

    private static String toWebSocketUrl(String baseUrl) {
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length());
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length());
        }
        return baseUrl;
    }

    private void handleMessage(String text) {
        try {
            RosterWireTypes.Envelope envelope = gson.fromJson(text, RosterWireTypes.Envelope.class);
            if (envelope == null || envelope.type == null || envelope.payload == null) {
                return;
            }

            if (ROSTER_SNAPSHOT.equals(envelope.type)) {
                RosterWireTypes.RosterSnapshotPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.RosterSnapshotPayload.class);
                rosterState.replaceAll(payload.roster);
            } else if (VITALS_UPDATE.equals(envelope.type)) {
                RosterWireTypes.VitalsUpdatePayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.VitalsUpdatePayload.class);
                if (payload.name != null) {
                    rosterState.getOrCreate(payload.name).applyVitals(payload.vitals);
                }
            } else if (KILL_EVENT.equals(envelope.type)) {
                RosterWireTypes.KillEventPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.KillEventPayload.class);
                if (payload.memberName != null && payload.npcName != null) {
                    killEventListener.onKillEvent(payload.memberName, payload.npcName);
                }
            } else if (DROP_EVENT.equals(envelope.type)) {
                RosterWireTypes.DropEventPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.DropEventPayload.class);
                if (payload.memberName != null && payload.message != null) {
                    dropEventListener.onDropEvent(payload.memberName, payload.message);
                }
            } else if (COLOR_UPDATE.equals(envelope.type)) {
                RosterWireTypes.ColorUpdatePayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.ColorUpdatePayload.class);
                if (payload.name != null && payload.color != null) {
                    rosterState.getOrCreate(payload.name).color = payload.color;
                }
            } else if (PING_START.equals(envelope.type)) {
                RosterWireTypes.PingStartPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.PingStartPayload.class);
                if (payload.pingId != null && payload.memberName != null) {
                    pingEventListener.onPingStart(payload);
                }
            } else if (PING_UPDATE.equals(envelope.type)) {
                RosterWireTypes.PingUpdatePayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.PingUpdatePayload.class);
                if (payload.pingId != null) {
                    pingEventListener.onPingUpdate(payload);
                }
            } else if (PING_END.equals(envelope.type)) {
                RosterWireTypes.PingEndPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.PingEndPayload.class);
                if (payload.pingId != null) {
                    pingEventListener.onPingEnd(payload);
                }
            } else if (MARKER_START.equals(envelope.type)) {
                RosterWireTypes.RaidMarkerStartPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.RaidMarkerStartPayload.class);
                if (payload.markerId != null && payload.memberName != null && payload.markerType != null) {
                    raidMarkerEventListener.onMarkerStart(payload);
                }
            } else if (MARKER_UPDATE.equals(envelope.type)) {
                RosterWireTypes.RaidMarkerUpdatePayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.RaidMarkerUpdatePayload.class);
                if (payload.markerId != null) {
                    raidMarkerEventListener.onMarkerUpdate(payload);
                }
            } else if (MARKER_END.equals(envelope.type)) {
                RosterWireTypes.RaidMarkerEndPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.RaidMarkerEndPayload.class);
                if (payload.markerId != null) {
                    raidMarkerEventListener.onMarkerEnd(payload);
                }
            } else if (CHAT_MESSAGE.equals(envelope.type)) {
                RosterWireTypes.ChatMessagePayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.ChatMessagePayload.class);
                if (payload.text != null) {
                    chatEventListener.onChatMessage(payload, envelope.ts);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse party overlay message: {}", e.toString());
        }
    }

    private synchronized void scheduleReconnect(String baseUrl, String accountHash, String apiKey) {
        if (intentionallyClosed) return;
        log.info("Party overlay WebSocket reconnect scheduled in {}s (accountHash={})", RECONNECT_DELAY_SECONDS, accountHash);
        reconnectExecutor.schedule(() -> {
            synchronized (this) {
                if (!intentionallyClosed
                        && accountHash.equals(connectedAccountHash)
                        && apiKey.equals(connectedApiKey)) {
                    log.info("Party overlay WebSocket reconnecting (accountHash={})", accountHash);
                    openSocket(baseUrl, accountHash, apiKey);
                }
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
