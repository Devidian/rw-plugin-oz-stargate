package de.omegazirkel.risingworld.stargate.network;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.addressbook.AddressBookService;
import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import de.omegazirkel.risingworld.stargate.sector.LocalDialService;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
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
            this(state, direction, peerGateId, chevrons, ready, GateAudioTiming.DIAL_STEP_MS, System.nanoTime(), 0);
        }
        public GateView(String state, String direction, String peerGateId, int chevrons, boolean ready,
                int stepMillis, long stepStartedNanos) {
            this(state, direction, peerGateId, chevrons, ready, stepMillis, stepStartedNanos, 0);
        }
    }
    private final Map<String, Long> openedTimes = new HashMap<>();
    private final Map<String, Long> closingTimes = new HashMap<>();
    private final Map<String, Long> incomingTimes = new HashMap<>();
    private boolean incomingAnimationTickQueued;
    private record DialTiming(int stepMillis, long startedNanos) { }
    private final Map<String, DialTiming> dialTimings = new HashMap<>();
    private final Map<String, Integer> chevrons = new HashMap<>();
    private final Map<String, String> pendingDials = new HashMap<>();
    private Runnable stateObserver = () -> { };
    private Runnable visualStateObserver = () -> { };
    private Runnable dhdVisualStateObserver = () -> { };
    private Consumer<String> gateDeletedObserver = id -> { };
    private de.omegazirkel.risingworld.stargate.DiscordEvents discordEvents;
    private final Map<String, GateState> gateStates = new HashMap<>();
    private boolean dialSequenceSupported;
    private final Map<String, DialWindow> windows = new HashMap<>();
    private TransferService transfers;
    private LocalDialService localDial;
    private AddressBookService addressBook;
    private Function<String, GateView> artificialView = id -> null;
    private Predicate<String> artificialReserved = id -> false;
    private Function<String, GateView> discoveryView = id -> null;
    private Predicate<String> discoveryReserved = id -> false;
    private WSClientEndpoint socket;
    private String networkCode;
    private boolean hostMissing;
    private String detectedHost;
    private String hostLookupRequestId;
    private static final String LOCAL_PREFIX = "LOCAL";

    public void setGateDeletedObserver(Consumer<String> observer) { gateDeletedObserver = observer; }
    public void setDiscordEvents(de.omegazirkel.risingworld.stargate.DiscordEvents events) { discordEvents = events; }

    public void setStateObserver(Runnable observer) { stateObserver = observer; }
    public void setVisualStateObserver(Runnable observer) { visualStateObserver = observer; }
    public void setDhdVisualStateObserver(Runnable observer) { dhdVisualStateObserver = observer; }

    private void notifyStateObservers() {
        stateObserver.run();
        dhdVisualStateObserver.run();
        visualStateObserver.run();
    }

    public GateView gateView(String gateId) {
        GateView artificial = artificialView.apply(gateId);
        if (artificial != null) return artificial;
        artificial = discoveryView.apply(gateId);
        if (artificial != null) return artificial;
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
                state != null && "INCOMING".equals(state.state())
                        ? incomingTimes.containsKey(gateId)
                                ? Math.min(7, 1 + (int) ((System.nanoTime() - incomingTimes.get(gateId))
                                        / (GateAudioTiming.INCOMING_CHEVRON_MS * 1_000_000L))) : 0
                        : chevrons.getOrDefault(gateId, 0), isReady() && dialSequenceSupported,
                timing == null ? GateAudioTiming.DIAL_STEP_MS : timing.stepMillis(), timing == null ? 0 : timing.startedNanos(), openedTimes.getOrDefault(gateId, 0L));
    }

    public void requestAddresses(Player player, Consumer<List<String>> callback) {
        callback.accept(addressBook == null ? List.of() : addressBook.knownAddresses(player));
    }

    public void refreshAddressBook(Player player) {
        if (addressBook != null) addressBook.sync(player);
    }

    public void syncAddressBook(Player player, List<String> pending, Consumer<List<String>> callback) {
        if (!isReady()) { callback.accept(null); return; }
        sendRequest("syncAddressBook", player, Map.of("uid", player.getUID(), "pending", pending),
                null, null, null, null, callback);
    }

    private void clearRequests() {
        List<Request> prior = new ArrayList<>(requests.values());
        requests.clear(); pendingDials.clear(); chevrons.clear(); dialTimings.clear();
        for (Request request : prior) if (request.addresses() != null) request.addresses().accept(null);
    }

    public boolean isReady() {
        return networkCode != null && socket != null && socket.isConnected();
    }
    public String networkCode() { return networkCode; }

    /** Setup also waits for requests which have not yet received a relay state update. */
    public boolean isIdleForSetup(String gateId) {
        GateView view = gateView(gateId);
        return view.ready() && isIdleForArtificialArrival(gateId);
    }

    public boolean isIdleForArtificialArrival(String gateId) {
        GateView view = gateView(gateId);
        return "IDLE".equals(view.state())
                && requests.values().stream().noneMatch(r -> gateId.equals(r.sourceGateId()) || gateId.equals(r.gateId()))
                && windows.values().stream().noneMatch(w -> gateId.equals(w.sourceGateId()) && w.expiresAt() > System.currentTimeMillis());
    }

    public boolean hasPendingUnregister(String gateId) {
        return requests.values().stream().anyMatch(r -> "unregisterGate".equals(r.type()) && gateId.equals(r.gateId()));
    }

    public boolean hasPendingRegistration(SectorAddress address) {
        return requests.values().stream().anyMatch(request ->
                "registerGate".equals(request.type()) && request.position() != null
                        && address.equals(SectorAddress.fromWorld(request.position())));
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
    public void setAddressBook(AddressBookService addressBook) { this.addressBook = addressBook; }
    public void setArtificialArrival(Function<String, GateView> view, Predicate<String> reserved) {
        artificialView = view;
        artificialReserved = reserved;
    }
    public void setArtificialDiscovery(Function<String, GateView> view, Predicate<String> reserved) {
        discoveryView = view;
        discoveryReserved = reserved;
    }
    public boolean isArtificialReserved(String gateId) {
        return artificialReserved.test(gateId) || discoveryReserved.test(gateId);
    }
    public void refreshViews() { notifyStateObservers(); }

    public DialWindow openWindow(String targetGateId) {
        DialWindow window = windows.get(targetGateId);
        if (window == null || window.expiresAt() <= System.currentTimeMillis()) return null;
        return window;
    }

    public long closingAtNanos(String gateId) {
        GateView artificial = artificialView.apply(gateId);
        if (artificial == null) artificial = discoveryView.apply(gateId);
        if (artificial != null) return "OPEN".equals(artificial.state())
                ? artificial.openedNanos() + 10_000_000_000L : 0L;
        Long local = localDial == null ? null : localDial.closingAtNanos(gateId);
        return local == null ? closingTimes.getOrDefault(gateId, 0L) : local;
    }

    public boolean transfer(String type, String requestId, Map<String, ?> payload) {
        return isReady() && send(type, requestId, payload, networkCode);
    }

    public void start() {
        if (!settings.networkEnabled) return;
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
        gateStates.clear(); openedTimes.clear(); closingTimes.clear(); incomingTimes.clear();
        dialSequenceSupported = false;
        hostMissing = false;
        detectedHost = null;
        hostLookupRequestId = null;
        windows.clear();
        notifyStateObservers();
        start();
    }

    public void close() {
        dispatcher.close();
        stopSocket();
        clearRequests();
        gateStates.clear(); openedTimes.clear(); closingTimes.clear(); incomingTimes.clear();
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
            if (host == null || host.isBlank()) {
                if (detectedHost == null) {
                    hostMissing = true;
                    hostLookupRequestId = UUID.randomUUID().toString();
                    if (!send("resolveHost", hostLookupRequestId, Map.of(), null))
                        OZStargate.logger().warn("Cannot request public game-server host from relay");
                    return;
                }
                host = detectedHost;
            }
            if (host == null || host.isBlank() || host.contains("/") || host.contains(":")) {
                hostMissing = true;
                OZStargate.logger().error("Cannot resolve game-server host; set relay.advertisedHost in Stargate settings");
                return;
            }
            hostMissing = false;
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
        dispatcher.dispatch(() -> { boolean wasReady = networkCode != null; networkCode = null; detectedHost = null; hostLookupRequestId = null; dialSequenceSupported = false; clearRequests(); windows.clear(); gateStates.clear(); openedTimes.clear(); closingTimes.clear(); incomingTimes.clear(); notifyStateObservers(); if (wasReady && discordEvents != null) discordEvents.networkDisconnected(); });
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
        if (type.equals("hostResolved")) {
            if (!requestId.equals(hostLookupRequestId) || !settings.relayAdvertisedHost.isBlank()) return;
            hostLookupRequestId = null;
            String host = payload.has("host") ? payload.get("host").getAsString() : "";
            if (!isPublicIpv4(host)) {
                OZStargate.logger().error("Relay did not provide a usable public game-server IP");
                return;
            }
            detectedHost = host;
            onConnected(socket);
            return;
        }
        if (type.equals("networkReady")) {
            boolean changed = payload.has("changed") && payload.get("changed").getAsBoolean();
            String previousCode = settings.networkCodeTrusted;
            networkCode = payload.get("networkCode").getAsString();
            dialSequenceSupported = payload.has("dialSequenceVersion") && payload.get("dialSequenceVersion").getAsInt() == 1;
            if (addressBook != null && previousCode != null && !previousCode.isBlank()
                    && !previousCode.equals(networkCode) && !addressBook.migrateLocalGates(previousCode, networkCode)) {
                OZStargate.logger().error("Stargate address book code migration failed; retrying relay connection");
                plugin.executeDelayed(5f, this::reload);
                networkCode = null;
                return;
            }
            settings.trustNetworkCode(networkCode);
            if (detectedHost != null) settings.setDetectedHost(detectedHost);
            detectedHost = null;
            OZStargate.logger().info("Stargate network ready: " + networkCode);
            if (discordEvents != null) {
                discordEvents.networkConnected();
                if (changed) discordEvents.networkCodeChanged(i18n.get(
                        settings.networkCodeOverride.isBlank() ? "tc.stargate.discord.events.reason_profile"
                                : "tc.stargate.discord.events.reason_override",
                        new de.omegazirkel.risingworld.stargate.DiscordBridge(plugin).getBotLanguage()));
            }
            syncLocalGates();
            syncLegacyLocalAddresses();
            syncAliases();
            for (Player online : Server.getAllPlayers()) updatePlayer(online);
            notifyStateObservers();
            if (transfers != null) transfers.resume();
            return;
        }
        if (type.equals("gateState")) { handleGateState(payload); return; }
        if (type.equals("addressRemoved")) {
            if (addressBook != null && payload.has("gateId")) addressBook.removed(payload.get("gateId").getAsString());
            return;
        }
        if (type.equals("dialProgress")) {
            Request pending = requests.get(requestId);
            if (pending != null && pending.type().equals("dialGate")) {
                Player player = Server.getPlayerByUID(pending.playerUid());
                int chevron = payload.get("chevron").getAsInt();
                chevrons.put(pending.sourceGateId(), chevron);
                int stepMs = payload.has("stepMs") ? payload.get("stepMs").getAsInt() : GateAudioTiming.DIAL_STEP_MS;
                dialTimings.put(pending.sourceGateId(), new DialTiming(Math.max(1, Math.min(10000, stepMs)), System.nanoTime()));
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
        if (request.addresses() != null && !type.equals("addressList") && !type.equals("addressBook")) request.addresses().accept(null);
        Player player = request.playerUid() == null ? null : Server.getPlayerByUID(request.playerUid());
        try {
            switch (type) {
                case "gateRegistered" -> {
                    String gateId = payload.get("gateId").getAsString();
                    if (request.type().equals("registerExistingGate")) {
                        if (!gates.exists(gateId)) {
                            send("unregisterGate", UUID.randomUUID().toString(), Map.of("gateId", gateId), networkCode);
                            break;
                        }
                        gates.setGlobalAddress(gateId, payload.has("address")
                                ? payload.get("address").getAsString() : gateId);
                        syncAlias(gateId);
                        if (addressBook != null) for (Player online : Server.getAllPlayers()) addressBook.sync(online);
                        break;
                    }
                    try {
                        if (request.registration() == null) sectors.saveGate(gates, gateId, request.position(), request.rotation());
                        else request.registration().save(gateId, request.position(), request.rotation());
                    }
                    catch (SQLException | RuntimeException ex) {
                        send("unregisterGate", UUID.randomUUID().toString(), Map.of("gateId", gateId), networkCode);
                        throw ex;
                    }
                    tell(player, "registered", "PH_GATE", gateId);
                    syncAlias(gateId);
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
                case "addressBook" -> {
                    JsonArray list = payload.getAsJsonArray("gates");
                    List<String> ids = new ArrayList<>();
                    for (JsonElement row : list) ids.add(row.getAsString());
                    if (addressBook != null && payload.has("details")) {
                        for (JsonElement row : payload.getAsJsonArray("details")) {
                            JsonObject detail = row.getAsJsonObject();
                            addressBook.setGateDetails(detail.get("gateId").getAsString(),
                                    detail.get("address").getAsString(),
                                    detail.has("localAddress") && !detail.get("localAddress").isJsonNull()
                                            ? detail.get("localAddress").getAsString() : null,
                                    detail.get("alias").getAsString());
                        }
                    }
                    if (request.addresses() != null) request.addresses().accept(List.copyOf(ids));
                }
                case "gateFree" -> {
                    GateState state = gateStates.get(request.sourceGateId());
                    if (state == null || !state.state().equals("OPEN") || !state.direction().equals("OUTGOING")
                            || !state.connectionId().equals(payload.get("connectionId").getAsString())) return;
                    String targetGateId = payload.get("gateId").getAsString();
                    windows.put(targetGateId, new DialWindow(request.sourceGateId(), targetGateId,
                            payload.get("host").getAsString(), payload.get("port").getAsInt(),
                            System.currentTimeMillis() + Math.min(60_000L, payload.get("expiresInMs").getAsLong())));
                    closingTimes.put(request.sourceGateId(), System.nanoTime()
                            + Math.min(60_000L, payload.get("expiresInMs").getAsLong()) * 1_000_000L);
                    tell(player, "dial_free", "PH_TARGET", payload.get("gateId").getAsString()
                            + " (" + payload.get("host").getAsString() + ":" + payload.get("port").getAsInt() + ")");
                    if (addressBook != null) addressBook.discover(player, targetGateId);
                }
                case "gateBlocked" -> tell(player, "dial_blocked", "PH_GATE", request.gateId());
                case "dialFail" -> tell(player, "dial_failed", "PH_REASON", dialReason(player, payload.get("reason").getAsString()));
                case "playerTrust" -> showTrust(player, payload);
                case "error" -> {
                    String code = payload.get("code").getAsString();
                    if (request.type().equals("registerExistingGate")) {
                        OZStargate.logger().warn("Stargate relay registration failed for " + request.gateId() + ": " + code);
                        if (!Set.of("invalid_gate_address", "gate_not_owned_or_missing").contains(code))
                            plugin.executeDelayed(30f, () -> registerLocalGate(request.gateId()));
                    } else tell(player, "error", "PH_REASON", code);
                }
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
            closingTimes.remove(gateId);
            incomingTimes.remove(gateId);
            chevrons.remove(gateId); dialTimings.remove(gateId);
            windows.entrySet().removeIf(entry -> entry.getValue().sourceGateId().equals(gateId));
            if (previous.state().equals("OPEN")) {
                for (Player player : Server.getAllPlayers()) tell(player, "gate_closed", "PH_GATE", gateId);
            }
        } else {
            gateStates.put(gateId, next);
            if (!"INCOMING".equals(next.state())) incomingTimes.remove(gateId);
            if (next.state().equals("OPEN")) {
                if (previous == null || !previous.state().equals("OPEN")
                        || !previous.connectionId().equals(next.connectionId())) openedTimes.put(gateId, System.nanoTime());
                if (next.direction().equals("INCOMING") && !closingTimes.containsKey(gateId))
                    closingTimes.put(gateId, System.nanoTime() + GateAudioTiming.OPEN_MS * 1_000_000L);
            } else openedTimes.remove(gateId);
            if (next.state().equals("OPEN") && next.direction().equals("INCOMING") && !next.equals(previous)) {
                StargateChat.incoming(gateId, sectors, i18n);
            }
        }
        notifyStateObservers();
    }

    private void scheduleIncomingAnimationTick() {
        if (incomingAnimationTickQueued) return;
        incomingAnimationTickQueued = true;
        plugin.executeDelayed(.1f, () -> {
            incomingAnimationTickQueued = false;
            if (incomingTimes.isEmpty()) return;
            dhdVisualStateObserver.run();
            visualStateObserver.run();
            scheduleIncomingAnimationTick();
        });
    }

    private void handleDialIn(JsonObject payload) {
        String gateId = payload.get("gateId").getAsString();
        String dialId = payload.get("dialId").getAsString();
        try {
            GateState state = gateStates.get(gateId);
            if (!settings.networkEnabled || !gates.exists(gateId) || state == null
                    || !state.state().equals("INCOMING") || !state.connectionId().equals(dialId)
                    || isArtificialReserved(gateId)
                    || (localDial != null && !localDial.acceptRemoteIncoming(gateId))) {
                send("gateBlocked", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
                for (Player player : Server.getAllPlayers()) if (player.isAdmin()) tell(player, "incoming_blocked", "PH_GATE", gateId);
                return;
            }
            incomingTimes.put(gateId, System.nanoTime());
            notifyStateObservers();
            scheduleIncomingAnimationTick();
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
        try {
            SectorAddress address = SectorAddress.fromWorld(position);
            if (sectors.gateAt(address) != null || hasPendingRegistration(address)) {
                tell(player, "sector_occupied", "PH_SECTOR", address.toString());
                return;
            }
        } catch (SQLException ex) { fail(player, ex); return;
        } catch (IllegalArgumentException ex) {
            tell(player, "invalid_sector", null, null); return;
        }
        try {
            String gateId;
            do { gateId = newLocalGateId(); }
            while (gates.exists(gateId));
            if (registration == null) sectors.saveGate(gates, gateId, position, rotation);
            else registration.save(gateId, position, rotation);
            tell(player, "registered_local", "PH_GATE", gateId);
            registerLocalGate(gateId);
        } catch (SQLException | RuntimeException ex) {
            OZStargate.logger().error("Cannot place local Stargate: " + ex.getMessage());
            tell(player, "error", "PH_REASON", "database");
        }
    }

    private void syncLocalGates() {
        try {
            for (String id : gates.ids()) if (isLocalGateId(id)) registerLocalGate(id);
        } catch (SQLException ex) { OZStargate.logger().error("Cannot sync local Stargates: " + ex.getMessage()); }
    }

    private void syncLegacyLocalAddresses() {
        if (!settings.networkEnabled) return;
        try {
            for (String id : gates.ids()) {
                if (isLocalGateId(id)) continue;
                String localAddress = gates.localAddress(id);
                if (localAddress != null) send("setGateLocalAddress", UUID.randomUUID().toString(),
                        Map.of("gateId", id, "localAddress", localAddress), networkCode);
            }
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot sync legacy local gate addresses: " + ex.getMessage());
        }
    }

    private void registerLocalGate(String id) {
        if (!isReady() || !settings.networkEnabled || !isLocalGateId(id)
                || requests.values().stream().anyMatch(r -> r.type().equals("registerExistingGate") && id.equals(r.gateId()))) return;
        String requestId = UUID.randomUUID().toString();
        requests.put(requestId, new Request("registerExistingGate", null, null, null, id, null, null, null));
        if (!send("registerGate", requestId, Map.of("gateId", id), networkCode)) {
            requests.remove(requestId);
            return;
        }
        plugin.executeDelayed(15f, () -> {
            if (requests.remove(requestId) != null) registerLocalGate(id);
        });
    }

    public void unregister(Player player, String gateId) {
        try {
            if (!gates.exists(gateId)) { tell(player, "not_owned", "PH_GATE", gateId); return; }
            if (gates.globalAddress(gateId) == null) {
                gates.delete(gateId);
                gateDeletedObserver.accept(gateId);
                tell(player, "unregistered", "PH_GATE", gateId);
                return;
            }
            if (!settings.networkEnabled) { tell(player, "global_gate_offline", null, null); return; }
            if (!ready(player)) return;
            sendRequest("unregisterGate", player, Map.of("gateId", gateId), null, null, gateId, null);
        } catch (SQLException ex) { fail(player, ex); }
    }

    public void list(Player player) {
        List<String> ids = addressBook == null ? List.of() : addressBook.knownAddresses(player);
        if (player != null && player.isConnected()) player.sendTextMessage(i18n.get("tc.stargate.network.list", player)
                .replace("PH_GATES", ids.isEmpty() ? "-" : String.join(", ", ids)));
    }

    public String gateAlias(String gateId) {
        try { return gates.alias(gateId); }
        catch (SQLException ex) { OZStargate.logger().error("Cannot read Stargate alias: " + ex.getMessage()); return null; }
    }

    public String localAddress(String gateId) {
        try {
            String address = gates.localAddress(gateId);
            return address == null ? gateId : address;
        }
        catch (SQLException ex) { OZStargate.logger().error("Cannot read local Stargate address: " + ex.getMessage()); return gateId; }
    }

    public String globalAddress(String gateId) {
        try { return gates.globalAddress(gateId); }
        catch (SQLException ex) { OZStargate.logger().error("Cannot read global Stargate address: " + ex.getMessage()); return null; }
    }

    public String addressAlias(String address) {
        try {
            String localGate = gates.gateByAddress(address);
            if (localGate != null) return gates.alias(localGate);
            return addressBook == null ? null : addressBook.aliasByAddress(address);
        } catch (SQLException ex) { OZStargate.logger().error("Cannot read Stargate address alias: " + ex.getMessage()); return null; }
    }

    public String displayAddress(String address, String mode) {
        try {
            String localGate = gates.gateByAddress(address);
            if (localGate != null) {
                String networkAddress = gates.globalAddress(localGate);
                return "NETWORK".equals(mode) && networkAddress != null ? networkAddress : gates.localAddress(localGate);
            }
            if (!"NETWORK".equals(mode) && addressBook != null) {
                String localAddress = addressBook.localByAddress(address);
                if (localAddress != null && !localAddress.isBlank()) return localAddress;
            }
            return address;
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot display Stargate address: " + ex.getMessage());
            return address;
        }
    }

    public void setGateAlias(Player player, String gateId, String alias) {
        if (player == null || !player.isAdmin()) return;
        String name = alias == null ? "" : alias.trim();
        if (gateId == null || !gateId.matches("[A-Z0-9]{16}")
                || (!name.equals("-") && !name.matches("[\\p{L}\\p{N} _.'-]{1,40}"))) {
            tell(player, "usage_alias", null, null);
            return;
        }
        try {
            if (!gates.exists(gateId)) { tell(player, "not_owned", "PH_GATE", gateId); return; }
            gates.setAlias(gateId, name.equals("-") ? null : name);
            syncAlias(gateId);
            tell(player, "alias_saved", "PH_GATE", gateId);
            notifyStateObservers();
        } catch (SQLException ex) { fail(player, ex); }
    }

    private void syncAliases() {
        try { for (String gateId : gates.ids()) syncAlias(gateId); }
        catch (SQLException ex) { OZStargate.logger().error("Cannot sync Stargate aliases: " + ex.getMessage()); }
    }

    private void syncAlias(String gateId) {
        if (!isReady()) return;
        try {
            if (gates.globalAddress(gateId) == null) return;
            String alias = gates.alias(gateId);
            send("setGateAlias", UUID.randomUUID().toString(), Map.of("gateId", gateId,
                    "alias", alias == null ? "" : alias), networkCode);
        } catch (SQLException ex) { OZStargate.logger().error("Cannot sync Stargate alias: " + ex.getMessage()); }
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
            if (gates.globalAddress(source) == null) {
                tell(player, "local_gate_only", null, null); return;
            }
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
        if (addressBook != null) addressBook.sync(player);
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
            StargateChat.debug(player, i18n.get("tc.stargate.network.trust_row", player)
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
        Request request = new Request(type, player == null ? null : player.getUID(), position, rotation,
                gateId, sourceGateId, addresses, registration);
        requests.put(requestId, request);
        if (type.equals("dialGate")) { pendingDials.put(sourceGateId, requestId); notifyStateObservers(); }
        if (!send(type, requestId, payload, networkCode)) {
            requests.remove(requestId); pendingDials.remove(sourceGateId, requestId);
            if (addresses != null) addresses.accept(null);
            notifyStateObservers(); tell(player, "offline", null, null); return;
        }
        // Seven 7s steps plus the target's 10s reply budget need a little network margin.
        plugin.executeDelayed(type.equals("dialGate") ? 90f : 15f, () -> {
            if (requests.remove(requestId) != null) {
                pendingDials.remove(sourceGateId, requestId);
                if (addresses != null) addresses.accept(null);
                notifyStateObservers();
                if (request.playerUid() == null) OZStargate.logger().warn("Stargate system request timed out: " + type);
                else tell(Server.getPlayerByUID(request.playerUid()), "timeout", null, null);
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
        tell(player, hostMissing ? "host_missing" : "offline", null, null);
        return false;
    }

    static String newLocalGateId() {
        return LOCAL_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 11).toUpperCase();
    }

    static boolean isLocalGateId(String value) {
        return value != null && value.matches("LOCAL[0-9A-F]{11}");
    }

    static boolean isPublicIpv4(String value) {
        if (value == null || !value.matches("[0-9.]+")) return false;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || Integer.parseInt(part) > 255) return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            byte[] bytes = address.getAddress();
            int first = bytes.length == 4 ? bytes[0] & 0xff : -1;
            int second = bytes.length == 4 ? bytes[1] & 0xff : -1;
            return address instanceof Inet4Address && first > 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !address.isAnyLocalAddress()
                    && !address.isLoopbackAddress() && !address.isSiteLocalAddress()
                    && !address.isLinkLocalAddress() && !address.isMulticastAddress();
        } catch (UnknownHostException ex) { return false; }
    }

    private void fail(Player player, SQLException ex) {
        OZStargate.logger().error("Stargate database error: " + ex.getMessage());
        tell(player, "error", "PH_REASON", "database");
    }

    private void tell(Player player, String key, String variable, String replacement) {
        if (player == null) return;
        String text = i18n.get("tc.stargate.network." + key, player);
        if (variable != null) text = text.replace(variable, replacement == null ? "" : replacement);
        StargateChat.debug(player, text);
    }
}
