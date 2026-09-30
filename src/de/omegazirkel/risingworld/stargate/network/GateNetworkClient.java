package de.omegazirkel.risingworld.stargate.network;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import de.omegazirkel.risingworld.stargate.sector.LocalDialService;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ServerThreadDispatcher;
import de.omegazirkel.risingworld.tools.WSClientEndpoint;
import de.omegazirkel.risingworld.tools.WebSocketHandler;
import net.risingworld.api.Plugin;
import net.risingworld.api.Server;
import net.risingworld.api.World;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

/** Gate commands and relay protocol. All game API work runs on the server thread. */
public final class GateNetworkClient implements WebSocketHandler {
    @FunctionalInterface public interface RegistrationHandler {
        void save(String gateId, Vector3f position, Quaternion rotation) throws SQLException;
    }
    private record Request(String type, String playerUid, Vector3f position, Quaternion rotation, String gateId,
            String sourceGateId, Consumer<List<String>> addresses, RegistrationHandler registration) { }
    public record DialWindow(String sourceGateId, String targetGateId, String host, int port, long expiresAt) { }

    private final OZStargate plugin;
    private final PluginSettings settings;
    private final I18n i18n;
    private final LocalGateStore gates;
    private final LocalSectorStore sectors;
    private final ServerThreadDispatcher dispatcher;
    private final Gson gson = new Gson();
    private final Map<String, Request> requests = new HashMap<>();
    private record GateState(String state, String direction, String connectionId, String peerGateId) { }
    public record GateView(String state, String direction, String peerGateId, int chevrons, boolean ready,
            int stepMillis, long stepStartedNanos, long openedNanos) {
        public GateView(String state, String direction, String peerGateId, int chevrons, boolean ready) {
            this(state, direction, peerGateId, chevrons, ready, 3500, System.nanoTime(), 0);
        }
        public GateView(String state, String direction, String peerGateId, int chevrons, boolean ready,
                int stepMillis, long stepStartedNanos) {
            this(state, direction, peerGateId, chevrons, ready, stepMillis, stepStartedNanos, 0);
        }
    }
    private final Map<String, Long> openedTimes = new HashMap<>();
    private record DialTiming(int stepMillis, long startedNanos) { }
    private final Map<String, DialTiming> dialTimings = new HashMap<>();
    private final Map<String, Integer> chevrons = new HashMap<>();
    private final Map<String, String> pendingDials = new HashMap<>();
    private Runnable stateObserver = () -> { };
    private Runnable visualStateObserver = () -> { };
    private Consumer<String> gateDeletedObserver = id -> { };
    private final Map<String, GateState> gateStates = new HashMap<>();
    private boolean dialSequenceSupported;
    private final Map<String, DialWindow> windows = new HashMap<>();
    private TransferService transfers;
    private LocalDialService localDial;
    private WSClientEndpoint socket;
    private String networkCode;

    public void setGateDeletedObserver(Consumer<String> observer) { gateDeletedObserver = observer; }

    public void setStateObserver(Runnable observer) { stateObserver = observer; }
    public void setVisualStateObserver(Runnable observer) { visualStateObserver = observer; }

    private void notifyStateObservers() {
        stateObserver.run();
        visualStateObserver.run();
    }

    public GateView gateView(String gateId) {
        GateView remote = remoteGateView(gateId);
        return localDial == null ? remote : localDial.view(gateId, remote);
    }

    public GateView remoteGateView(String gateId) {
        GateState state = gateStates.get(gateId);
        Request pending = requests.get(pendingDials.get(gateId));
        DialTiming timing = dialTimings.get(gateId);
        return new GateView(state == null ? (pending == null ? "IDLE" : "OUTGOING") : state.state(),
                state == null ? "OUTGOING" : state.direction(),
                state == null ? (pending == null ? "" : pending.gateId()) : state.peerGateId(),
                chevrons.getOrDefault(gateId, 0), isReady() && dialSequenceSupported,
                timing == null ? 3500 : timing.stepMillis(), timing == null ? 0 : timing.startedNanos(), openedTimes.getOrDefault(gateId, 0L));
    }

    public void requestAddresses(Player player, Consumer<List<String>> callback) {
        if (!settings.networkEnabled) { callback.accept(List.of()); return; }
        if (!isReady()) { callback.accept(null); return; }
        sendRequest("getAddressList", player, Map.of(), null, null, null, null, callback);
    }

    private void clearRequests() {
        List<Request> prior = new ArrayList<>(requests.values());
        requests.clear(); pendingDials.clear(); chevrons.clear(); dialTimings.clear();
        for (Request request : prior) if (request.addresses() != null) request.addresses().accept(null);
    }

    public boolean isReady() {
        return networkCode != null && socket != null && socket.isConnected();
    }

    /** Setup also waits for requests which have not yet received a relay state update. */
    public boolean isIdleForSetup(String gateId) {
        GateView view = gateView(gateId);
        return view.ready() && "IDLE".equals(view.state())
                && requests.values().stream().noneMatch(r -> gateId.equals(r.sourceGateId()) || gateId.equals(r.gateId()))
                && windows.values().stream().noneMatch(w -> gateId.equals(w.sourceGateId()) && w.expiresAt() > System.currentTimeMillis());
    }

    public boolean hasPendingUnregister(String gateId) {
        return requests.values().stream().anyMatch(r -> "unregisterGate".equals(r.type()) && gateId.equals(r.gateId()));
    }

    public GateNetworkClient(OZStargate plugin, PluginSettings settings, I18n i18n,
            LocalGateStore gates, LocalSectorStore sectors) {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.gates = gates;
        this.sectors = sectors;
        dispatcher = new ServerThreadDispatcher(plugin);
    }

    public void setTransfers(TransferService transfers) { this.transfers = transfers; }
    public void setLocalDial(LocalDialService localDial) { this.localDial = localDial; }
    public void refreshViews() { notifyStateObservers(); }

    public DialWindow openWindow(String targetGateId) {
        DialWindow window = windows.get(targetGateId);
        if (window == null || window.expiresAt() <= System.currentTimeMillis()) return null;
        return window;
    }

    public boolean transfer(String type, String requestId, Map<String, ?> payload) {
        return isReady() && send(type, requestId, payload, networkCode);
    }

    public void start() {
        String uri = settings.relayUrl;
        if (!uri.startsWith("wss://") && !uri.startsWith("ws://")) {
            OZStargate.logger().error("Invalid Stargate relay URL");
            return;
        }
        socket = new WSClientEndpoint(uri, this);
        socket.init();
    }

    public void reload() {
        stopSocket();
        networkCode = null;
        clearRequests();
        gateStates.clear(); openedTimes.clear();
        dialSequenceSupported = false;
        windows.clear();
        notifyStateObservers();
        start();
    }

    public void close() {
        dispatcher.close();
        stopSocket();
        clearRequests();
        gateStates.clear(); openedTimes.clear();
        dialSequenceSupported = false;
        windows.clear();
    }

    private void stopSocket() {
        if (socket != null) {
            socket.setHandler(null);
            socket.shutdown();
        }
        socket = null;
    }

    @Override public void onConnected(WSClientEndpoint endpoint) {
        dispatcher.dispatch(() -> {
            if (endpoint != socket) return;
            networkCode = null;
            Map<String, String> world = new HashMap<>();
            for (String key : List.of("World_GameMode", "World_OreAmount", "Settings_BlueprintsRequireResources",
                    "Settings_GameMode", "Settings_OreSmeltingDurationFactor")) {
                String value = Server.getOption(key);
                world.put(key, value == null || value.isBlank() ? "(unset)" : value.trim());
            }
            List<String> plugins = new ArrayList<>();
            for (Plugin candidate : plugin.getAllPlugins()) {
                plugins.add(candidate.getDescription("name") + ":" + candidate.getDescription("version"));
            }
            String host = settings.relayAdvertisedHost;
            if (host == null || host.isBlank() || host.contains("/") || host.contains(":")) {
                OZStargate.logger().error("Set relay.advertisedHost to the reachable game-server hostname or IP");
                return;
            }
            int port = Server.getPort();
            String identity = host + ":" + port + "/" + World.getName();
            Map<String, Object> payload = new HashMap<>();
            payload.put("dialSequenceVersion", 1);
            payload.put("travelEnabled", settings.networkEnabled);
            payload.put("serverId", UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString());
            payload.put("name", Server.getName());
            payload.put("host", host);
            payload.put("port", port);
            payload.put("world", world);
            payload.put("plugins", plugins);
            payload.put("forbiddenActions", Map.of("ChangeGameMode", settings.forbidChangeGameMode));
            payload.put("override", settings.networkCodeOverride);
            send("initNetwork", UUID.randomUUID().toString(), payload, null);
        });
    }

    @Override public void onDisconnected() {
        dispatcher.dispatch(() -> { networkCode = null; dialSequenceSupported = false; clearRequests(); windows.clear(); gateStates.clear(); openedTimes.clear(); notifyStateObservers(); });
    }

    @Override public void onTextMessage(String text) {
        try {
            JsonObject message = JsonParser.parseString(text).getAsJsonObject();
            if (message.get("v").getAsInt() != 1 || !message.has("type") || !message.has("requestId")
                    || !message.has("payload") || !message.get("payload").isJsonObject()) return;
            dispatcher.dispatch(() -> handle(message));
        } catch (RuntimeException ex) {
            OZStargate.logger().warn("Invalid Stargate relay response: " + ex.getMessage());
        }
    }

    private void handle(JsonObject message) {
        String type = message.get("type").getAsString();
        String requestId = message.get("requestId").getAsString();
        JsonObject payload = message.getAsJsonObject("payload");
        if (type.equals("networkReady")) {
            networkCode = payload.get("networkCode").getAsString();
            dialSequenceSupported = payload.has("dialSequenceVersion") && payload.get("dialSequenceVersion").getAsInt() == 1;
            settings.trustNetworkCode(networkCode);
            OZStargate.logger().info("Stargate network ready: " + networkCode);
            for (Player online : Server.getAllPlayers()) updatePlayer(online);
            notifyStateObservers();
            if (transfers != null) transfers.resume();
            return;
        }
        if (type.equals("gateState")) { handleGateState(payload); return; }
        if (type.equals("dialProgress")) {
            Request pending = requests.get(requestId);
            if (pending != null && pending.type().equals("dialGate")) {
                Player player = Server.getPlayerByUID(pending.playerUid());
                int chevron = payload.get("chevron").getAsInt();
                chevrons.put(pending.sourceGateId(), chevron);
                int stepMs = payload.has("stepMs") ? payload.get("stepMs").getAsInt() : 3500;
                dialTimings.put(pending.sourceGateId(), new DialTiming(Math.max(1, Math.min(5000, stepMs)), System.nanoTime()));
                notifyStateObservers();
                if (chevron == 0) tell(player, "dial_started", "PH_GATE", pending.gateId());
                else tell(player, "dial_chevron", "PH_CHEVRON", Integer.toString(chevron));
            }
            return;
        }
        if (type.equals("dialIn")) { handleDialIn(payload); return; }
        if (transfers != null) {
            switch (type) {
                case "incomingTransfer" -> { transfers.incoming(payload); return; }
                case "transferAccepted" -> { transfers.accepted(payload); return; }
                case "transferReleased" -> { transfers.released(payload); return; }
                case "transferFailed", "transferAborted" -> { transfers.failed(payload); return; }
                case "transferDone", "transferDoneAck" -> { transfers.done(payload); return; }
                case "transferCancelled" -> { transfers.cancelled(payload); return; }
                case "transferClaimed" -> { transfers.claimed(payload); return; }
                case "transferStatus" -> { transfers.status(payload); return; }
                case "error" -> { transfers.error(requestId, payload.get("code").getAsString()); }
                default -> { }
            }
        }
        Request request = requests.remove(requestId);
        if (request == null) {
            if (type.equals("error")) OZStargate.logger().warn("Stargate relay error: " + payload);
            return;
        }
        pendingDials.remove(request.sourceGateId(), requestId);
        if (request.addresses() != null && !type.equals("addressList")) request.addresses().accept(null);
        Player player = Server.getPlayerByUID(request.playerUid());
        try {
            switch (type) {
                case "gateRegistered" -> {
                    String gateId = payload.get("gateId").getAsString();
                    try {
                        if (request.registration() == null) sectors.saveGate(gates, gateId, request.position(), request.rotation());
                        else request.registration().save(gateId, request.position(), request.rotation());
                    }
                    catch (SQLException | RuntimeException ex) {
                        send("unregisterGate", UUID.randomUUID().toString(), Map.of("gateId", gateId), networkCode);
                        throw ex;
                    }
                    tell(player, "registered", "PH_GATE", gateId);
                }
                case "gateUnregistered" -> { gates.delete(request.gateId()); gateDeletedObserver.accept(request.gateId()); tell(player, "unregistered", "PH_GATE", request.gateId()); }
                case "addressList" -> {
                    JsonArray list = payload.getAsJsonArray("gates");
                    List<String> ids = new ArrayList<>();
                    for (JsonElement row : list) ids.add(row.getAsJsonObject().get("gateId").getAsString());
                    if (request.addresses() != null) {
                        ids.removeAll(gates.ids());
                        ids.sort(String::compareTo);
                        request.addresses().accept(List.copyOf(ids));
                    } else tell(player, "list", "PH_GATES", ids.isEmpty() ? "-" : String.join(", ", ids));
                }
                case "gateFree" -> {
                    GateState state = gateStates.get(request.sourceGateId());
                    if (state == null || !state.state().equals("OPEN") || !state.direction().equals("OUTGOING")
                            || !state.connectionId().equals(payload.get("connectionId").getAsString())) return;
                    windows.put(request.gateId(), new DialWindow(request.sourceGateId(), request.gateId(),
                            payload.get("host").getAsString(), payload.get("port").getAsInt(),
                            System.currentTimeMillis() + Math.min(60_000L, payload.get("expiresInMs").getAsLong())));
                    tell(player, "dial_free", "PH_TARGET", payload.get("gateId").getAsString()
                            + " (" + payload.get("host").getAsString() + ":" + payload.get("port").getAsInt() + ")");
                }
                case "gateBlocked" -> tell(player, "dial_blocked", "PH_GATE", request.gateId());
                case "dialFail" -> tell(player, "dial_failed", "PH_REASON", dialReason(player, payload.get("reason").getAsString()));
                case "playerTrust" -> showTrust(player, payload);
                case "error" -> tell(player, "error", "PH_REASON", payload.get("code").getAsString());
                default -> OZStargate.logger().warn("Unknown Stargate response: " + type);
            }
        } catch (RuntimeException | SQLException ex) {
            OZStargate.logger().error("Cannot process Stargate response " + type + ": " + ex.getMessage());
            tell(player, "error", "PH_REASON", type);
            if (request.addresses() != null) request.addresses().accept(null);
        } finally { notifyStateObservers(); }
    }

    private String dialReason(Player player, String reason) {
        if (player == null) return reason;
        return switch (reason) {
            case "incoming_priority", "source_busy", "gate_unavailable", "gate_offline", "same_server",
                    "disconnected", "timeout", "dial_sequence_required", "dial_lookup_failed" ->
                i18n.get("tc.stargate.network.reason_" + reason, player);
            default -> reason;
        };
    }

    private void handleGateState(JsonObject payload) {
        String gateId = payload.get("gateId").getAsString();
        GateState next = new GateState(payload.get("state").getAsString(), payload.get("direction").getAsString(),
                payload.get("connectionId").getAsString(), payload.get("peerGateId").getAsString());
        GateState previous = gateStates.get(gateId);
        if (next.state().equals("IDLE")) {
            if (previous == null || !previous.connectionId().equals(next.connectionId())) return;
            gateStates.remove(gateId);
            openedTimes.remove(gateId);
            chevrons.remove(gateId); dialTimings.remove(gateId);
            windows.entrySet().removeIf(entry -> entry.getValue().sourceGateId().equals(gateId));
            if (previous.state().equals("OPEN")) {
                for (Player player : Server.getAllPlayers()) tell(player, "gate_closed", "PH_GATE", gateId);
            }
        } else {
            gateStates.put(gateId, next);
            if (next.state().equals("OPEN")) {
                if (previous == null || !previous.state().equals("OPEN")
                        || !previous.connectionId().equals(next.connectionId())) openedTimes.put(gateId, System.nanoTime());
            } else openedTimes.remove(gateId);
            if (next.state().equals("OPEN") && next.direction().equals("INCOMING") && !next.equals(previous)) {
                for (Player player : Server.getAllPlayers()) tell(player, "incoming", "PH_GATE", gateId);
            }
        }
        notifyStateObservers();
    }

    private void handleDialIn(JsonObject payload) {
        String gateId = payload.get("gateId").getAsString();
        String dialId = payload.get("dialId").getAsString();
        try {
            GateState state = gateStates.get(gateId);
            if (!settings.networkEnabled || (localDial != null && !localDial.acceptRemoteIncoming(gateId))
                    || !gates.exists(gateId) || state == null || !state.state().equals("INCOMING")
                    || !state.connectionId().equals(dialId)) {
                send("gateBlocked", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
                for (Player player : Server.getAllPlayers()) if (player.isAdmin()) tell(player, "incoming_blocked", "PH_GATE", gateId);
                return;
            }
            send("gateFree", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
        } catch (SQLException ex) {
            OZStargate.logger().error("Gate availability check failed: " + ex.getMessage());
            send("gateBlocked", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
        }
    }

    public void register(Player player) {
        register(player, new Vector3f(player.getPosition()), new Quaternion(player.getRotation()), null);
    }

    public void register(Player player, Vector3f position, Quaternion rotation, RegistrationHandler registration) {
        if (!ready(player)) return;
        try {
            SectorAddress address = SectorAddress.fromWorld(position);
            if (sectors.gateAt(address) != null || requests.values().stream().anyMatch(request ->
                    "registerGate".equals(request.type()) && request.position() != null
                    && address.equals(SectorAddress.fromWorld(request.position())))) {
                tell(player, "sector_occupied", "PH_SECTOR", address.toString());
                return;
            }
        } catch (SQLException ex) { fail(player, ex); return;
        } catch (IllegalArgumentException ex) {
            tell(player, "invalid_sector", null, null); return;
        }
        sendRequest("registerGate", player, Map.of(), position, rotation, null, null, null, registration);
    }

    public void unregister(Player player, String gateId) {
        if (!ready(player)) return;
        try {
            if (!gates.exists(gateId)) { tell(player, "not_owned", "PH_GATE", gateId); return; }
            sendRequest("unregisterGate", player, Map.of("gateId", gateId), null, null, gateId, null);
        } catch (SQLException ex) { fail(player, ex); }
    }

    public void list(Player player) {
        if (!settings.networkEnabled) { tell(player, "travel_disabled", null, null); return; }
        if (ready(player)) sendRequest("getAddressList", player, Map.of(), null, null, null, null);
    }

    public void dial(Player player, String targetId, String originId) {
        if (!settings.networkEnabled) { tell(player, "travel_disabled", null, null); return; }
        if (!ready(player)) return;
        if (!dialSequenceSupported) { tell(player, "reason_dial_sequence_required", null, null); return; }
        try {
            String source = originId;
            if (source == null || source.isBlank()) {
                List<String> ids = gates.ids();
                if (ids.size() != 1) { tell(player, "choose_origin", "PH_COUNT", Integer.toString(ids.size())); return; }
                source = ids.get(0);
            }
            if (!gates.exists(source)) { tell(player, "not_owned", "PH_GATE", source); return; }
            if (!gateView(source).state().equals("IDLE") || hasPendingUnregister(source)) {
                tell(player, "reason_source_busy", null, null); return;
            }
            sendRequest("dialGate", player, Map.of("gateId", targetId, "originGateId", source), null, null, targetId, source);
        } catch (SQLException ex) { fail(player, ex); }
    }

    public void updatePlayer(Player player) {
        if (!settings.networkEnabled) return;
        if (player == null || networkCode == null || socket == null || !socket.isConnected()) return;
        if (player.getUID() == null || player.getUID().isBlank()
                || player.getName() == null || player.getName().isBlank()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("uid", player.getUID());
        payload.put("name", player.getName());
        payload.put("playTimeSeconds", Math.max(0, player.getTotalPlayTime()));
        String group = player.getPermissionGroup();
        payload.put("permissionGroup", group == null || group.isBlank() ? null : group);
        if (!send("updatePlayer", UUID.randomUUID().toString(), payload, networkCode)) {
            OZStargate.logger().warn("Cannot update player observation while relay is unavailable");
        }
    }

    public void trust(Player player, String uid) {
        if (!settings.networkEnabled) { tell(player, "travel_disabled", null, null); return; }
        if (ready(player)) sendRequest("playerTrust", player, Map.of("uid", uid), null, null, null, null);
    }

    private void showTrust(Player player, JsonObject payload) {
        if (player == null) return;
        JsonArray observations = payload.getAsJsonArray("observations");
        String uid = payload.get("uid").getAsString();
        if (observations == null || observations.isEmpty()) {
            tell(player, "trust_empty", "PH_UID", uid);
            return;
        }
        tell(player, "trust_header", "PH_UID", uid);
        for (JsonElement element : observations) {
            JsonObject row = element.getAsJsonObject();
            String group = row.get("permissionGroup").isJsonNull() ? "-" : row.get("permissionGroup").getAsString();
            player.sendTextMessage(i18n.get("tc.stargate.network.trust_row", player)
                    .replace("PH_SERVER", row.get("serverId").getAsString())
                    .replace("PH_SECONDS", row.get("playTimeSeconds").getAsString())
                    .replace("PH_GROUP", group));
        }
    }

    private void sendRequest(String type, Player player, Map<String, Object> payload, Vector3f position, Quaternion rotation,
            String gateId, String sourceGateId) {
        sendRequest(type, player, payload, position, rotation, gateId, sourceGateId, null);
    }

    private void sendRequest(String type, Player player, Map<String, Object> payload, Vector3f position, Quaternion rotation,
            String gateId, String sourceGateId, Consumer<List<String>> addresses) {
        sendRequest(type, player, payload, position, rotation, gateId, sourceGateId, addresses, null);
    }

    private void sendRequest(String type, Player player, Map<String, Object> payload, Vector3f position, Quaternion rotation,
            String gateId, String sourceGateId, Consumer<List<String>> addresses, RegistrationHandler registration) {
        String requestId = UUID.randomUUID().toString();
        Request request = new Request(type, player.getUID(), position, rotation, gateId, sourceGateId, addresses, registration);
        requests.put(requestId, request);
        if (type.equals("dialGate")) { pendingDials.put(sourceGateId, requestId); notifyStateObservers(); }
        if (!send(type, requestId, payload, networkCode)) {
            requests.remove(requestId); pendingDials.remove(sourceGateId, requestId);
            if (addresses != null) addresses.accept(null);
            notifyStateObservers(); tell(player, "offline", null, null); return;
        }
        plugin.executeDelayed(type.equals("dialGate") ? 60f : 15f, () -> {
            if (requests.remove(requestId) != null) {
                pendingDials.remove(sourceGateId, requestId);
                if (addresses != null) addresses.accept(null);
                notifyStateObservers(); tell(Server.getPlayerByUID(request.playerUid()), "timeout", null, null);
            }
        });
    }

    private boolean send(String type, String requestId, Map<String, ?> payload, String code) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("v", 1);
        envelope.put("type", type);
        envelope.put("requestId", requestId);
        if (code != null) envelope.put("networkCode", code);
        envelope.put("payload", payload);
        return socket != null && socket.send(gson.toJson(envelope));
    }

    private boolean ready(Player player) {
        if (networkCode != null && socket != null && socket.isConnected()) return true;
        tell(player, "offline", null, null);
        return false;
    }

    private void fail(Player player, SQLException ex) {
        OZStargate.logger().error("Stargate database error: " + ex.getMessage());
        tell(player, "error", "PH_REASON", "database");
    }

    private void tell(Player player, String key, String variable, String replacement) {
        if (player == null) return;
        String text = i18n.get("tc.stargate.network." + key, player);
        if (variable != null) text = text.replace(variable, replacement == null ? "" : replacement);
        player.sendTextMessage(text);
    }
}
