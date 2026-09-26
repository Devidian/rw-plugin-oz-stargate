package de.omegazirkel.risingworld.stargate.network;

import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
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
    private record Request(String type, String playerUid, Vector3f position, Quaternion rotation, String gateId, String sourceGateId) { }
    public record DialWindow(String sourceGateId, String targetGateId, String host, int port, long expiresAt) { }

    private final OZStargate plugin;
    private final PluginSettings settings;
    private final I18n i18n;
    private final LocalGateStore gates;
    private final ServerThreadDispatcher dispatcher;
    private final Gson gson = new Gson();
    private final Map<String, Request> requests = new HashMap<>();
    private final Map<String, Long> reservations = new HashMap<>();
    private final Map<String, DialWindow> windows = new HashMap<>();
    private TransferService transfers;
    private WSClientEndpoint socket;
    private String networkCode;

    public boolean isReady() {
        return networkCode != null && socket != null && socket.isConnected();
    }

    public GateNetworkClient(OZStargate plugin, PluginSettings settings, I18n i18n, LocalGateStore gates) {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.gates = gates;
        dispatcher = new ServerThreadDispatcher(plugin);
    }

    public void setTransfers(TransferService transfers) { this.transfers = transfers; }

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
        requests.clear();
        reservations.clear();
        windows.clear();
        start();
    }

    public void close() {
        dispatcher.close();
        stopSocket();
        requests.clear();
        reservations.clear();
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
        dispatcher.dispatch(() -> { networkCode = null; requests.clear(); windows.clear(); });
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
            settings.trustNetworkCode(networkCode);
            OZStargate.logger().info("Stargate network ready: " + networkCode);
            for (Player online : Server.getAllPlayers()) updatePlayer(online);
            if (transfers != null) transfers.resume();
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
        Player player = Server.getPlayerByUID(request.playerUid());
        try {
            switch (type) {
                case "gateRegistered" -> {
                    String gateId = payload.get("gateId").getAsString();
                    try { gates.save(gateId, request.position(), request.rotation()); }
                    catch (SQLException ex) {
                        send("unregisterGate", UUID.randomUUID().toString(), Map.of("gateId", gateId), networkCode);
                        throw ex;
                    }
                    tell(player, "registered", "PH_GATE", gateId);
                }
                case "gateUnregistered" -> { gates.delete(request.gateId()); tell(player, "unregistered", "PH_GATE", request.gateId()); }
                case "addressList" -> {
                    JsonArray list = payload.getAsJsonArray("gates");
                    List<String> ids = new ArrayList<>();
                    for (JsonElement row : list) ids.add(row.getAsJsonObject().get("gateId").getAsString());
                    tell(player, "list", "PH_GATES", ids.isEmpty() ? "-" : String.join(", ", ids));
                }
                case "gateFree" -> {
                    windows.put(request.gateId(), new DialWindow(request.sourceGateId(), request.gateId(),
                            payload.get("host").getAsString(), payload.get("port").getAsInt(),
                            System.currentTimeMillis() + 60_000L));
                    tell(player, "dial_free", "PH_TARGET", payload.get("gateId").getAsString()
                            + " (" + payload.get("host").getAsString() + ":" + payload.get("port").getAsInt() + ")");
                }
                case "gateBlocked" -> tell(player, "dial_blocked", "PH_GATE", request.gateId());
                case "dialFail" -> tell(player, "dial_failed", "PH_REASON", payload.get("reason").getAsString());
                case "playerTrust" -> showTrust(player, payload);
                case "error" -> tell(player, "error", "PH_REASON", payload.get("code").getAsString());
                default -> OZStargate.logger().warn("Unknown Stargate response: " + type);
            }
        } catch (RuntimeException | SQLException ex) {
            OZStargate.logger().error("Cannot process Stargate response " + type + ": " + ex.getMessage());
            tell(player, "error", "PH_REASON", type);
        }
    }

    private void handleDialIn(JsonObject payload) {
        String gateId = payload.get("gateId").getAsString();
        String dialId = payload.get("dialId").getAsString();
        long now = System.currentTimeMillis();
        reservations.entrySet().removeIf(entry -> entry.getValue() <= now);
        try {
            if (!gates.exists(gateId) || reservations.containsKey(gateId)) {
                send("gateBlocked", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
                for (Player player : Server.getAllPlayers()) if (player.isAdmin()) tell(player, "incoming_blocked", "PH_GATE", gateId);
                return;
            }
            reservations.put(gateId, now + 60_000L);
            send("gateFree", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
            for (Player player : Server.getAllPlayers()) {
                player.sendTextMessage(i18n.get("tc.stargate.network.incoming", player).replace("PH_GATE", gateId));
            }
        } catch (SQLException ex) {
            OZStargate.logger().error("Gate availability check failed: " + ex.getMessage());
            send("gateBlocked", UUID.randomUUID().toString(), Map.of("gateId", gateId, "dialId", dialId), networkCode);
        }
    }

    public void register(Player player) {
        if (!ready(player)) return;
        Vector3f position = new Vector3f(player.getPosition());
        Quaternion rotation = new Quaternion(player.getRotation());
        sendRequest("registerGate", player, Map.of(), position, rotation, null, null);
    }

    public void unregister(Player player, String gateId) {
        if (!ready(player)) return;
        try {
            if (!gates.exists(gateId)) { tell(player, "not_owned", "PH_GATE", gateId); return; }
            sendRequest("unregisterGate", player, Map.of("gateId", gateId), null, null, gateId, null);
        } catch (SQLException ex) { fail(player, ex); }
    }

    public void list(Player player) {
        if (ready(player)) sendRequest("getAddressList", player, Map.of(), null, null, null, null);
    }

    public void dial(Player player, String targetId, String originId) {
        if (!ready(player)) return;
        try {
            String source = originId;
            if (source == null || source.isBlank()) {
                List<String> ids = gates.ids();
                if (ids.size() != 1) { tell(player, "choose_origin", "PH_COUNT", Integer.toString(ids.size())); return; }
                source = ids.get(0);
            }
            if (!gates.exists(source)) { tell(player, "not_owned", "PH_GATE", source); return; }
            sendRequest("dialGate", player, Map.of("gateId", targetId, "originGateId", source), null, null, targetId, source);
        } catch (SQLException ex) { fail(player, ex); }
    }

    public void updatePlayer(Player player) {
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
        String requestId = UUID.randomUUID().toString();
        Request request = new Request(type, player.getUID(), position, rotation, gateId, sourceGateId);
        requests.put(requestId, request);
        if (!send(type, requestId, payload, networkCode)) { requests.remove(requestId); tell(player, "offline", null, null); return; }
        plugin.executeDelayed(15f, () -> {
            if (requests.remove(requestId) != null) tell(Server.getPlayerByUID(request.playerUid()), "timeout", null, null);
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
