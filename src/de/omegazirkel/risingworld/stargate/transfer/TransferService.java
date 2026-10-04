package de.omegazirkel.risingworld.stargate.transfer;

import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.transfer.TransferStore.Base;
import de.omegazirkel.risingworld.stargate.transfer.TransferStore.Transfer;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.stargate.ui.StargatePlayerPluginSettings;
import de.omegazirkel.risingworld.stargate.ui.TravelScreenService;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Clothes;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

/** Server-thread-only transfer workflow. SQLite state is written before any inventory mutation. */
public final class TransferService {
    private final OZStargate plugin;
    private final TransferStore store;
    private final InventorySnapshotStore manualEscrow;
    private final LocalGateStore gates;
    private final GateNetworkClient network;
    private final I18n i18n;
    private final Gson gson = new Gson();
    private java.util.function.Consumer<Player> arrivalObserver = player -> { };
    private java.util.function.BiConsumer<Player, String> arrivalSoundObserver = (player, gateId) -> { };
    private java.util.function.BiConsumer<Player, String> arrivalPlayerSoundObserver = (player, gateId) -> { };
    private TravelScreenService travelScreen;
    private de.omegazirkel.risingworld.stargate.DiscordEvents discordEvents;
    public void setDiscordEvents(de.omegazirkel.risingworld.stargate.DiscordEvents events) { discordEvents = events; }

    public void setArrivalObserver(java.util.function.Consumer<Player> observer) { arrivalObserver = observer; }
    public void setArrivalSoundObserver(java.util.function.BiConsumer<Player, String> observer) { arrivalSoundObserver = observer; }
    public void setArrivalPlayerSoundObserver(java.util.function.BiConsumer<Player, String> observer) { arrivalPlayerSoundObserver = observer; }
    public void setTravelScreen(TravelScreenService travelScreen) { this.travelScreen = travelScreen; }

    public TransferService(OZStargate plugin, TransferStore store, InventorySnapshotStore manualEscrow,
            LocalGateStore gates, GateNetworkClient network, I18n i18n) {
        this.plugin = plugin;
        this.store = store;
        this.manualEscrow = manualEscrow;
        this.gates = gates;
        this.network = network;
        this.i18n = i18n;
    }

    public void resume() {
        try {
            for (Transfer transfer : store.active()) network.transfer("transferStatus", transfer.id(), Map.of("transferId", transfer.id()));
        } catch (SQLException ex) { log("Cannot resume transfer journal", ex); }
    }

    public boolean hasActive(String uid) {
        try {
            for (Transfer transfer : store.forPlayer(uid)) if (!terminal(transfer.state())) return true;
            return false;
        } catch (SQLException ex) { log("Cannot inspect transfer journal", ex); return true; }
    }

    public void warp(Player player, String targetGateId) {
        warp(player, targetGateId, false);
    }

    public boolean warp(Player player, String targetGateId, boolean passage) {
        if (!network.isReady()) { tell(player, "offline"); return false; }
        GateNetworkClient.DialWindow window = network.openWindow(targetGateId);
        if (window == null) { tell(player, "warp_not_open"); return false; }
        String uid = player.getUID();
        try {
            if (hasActive(uid) || manualEscrow.escrow(uid) != null || manualEscrow.legacyRecovery(uid) != null) {
                tell(player, "warp_pending"); return false;
            }
            Inventory inventory = player.getInventory();
            Clothes clothes = player.getClothes();
            if (inventory == null || clothes == null) { tell(player, "warp_unavailable"); return false; }
            Map<String, Object> data = capture(player);
            if (data == null) { tell(player, "warp_unavailable"); return false; }
            long startedAt = passage ? System.currentTimeMillis() : 0;
            boolean screenEnabled = passage && StargatePlayerPluginSettings.travelScreenEnabled(player);
            if (passage) {
                data.put("travelStartedAt", startedAt);
                data.put("travelScreenEnabled", screenEnabled);
            }
            String transferId = UUID.randomUUID().toString();
            Transfer transfer = new Transfer(transferId, uid, "OUT", "PREPARED", window.sourceGateId(),
                    targetGateId, gson.toJson(data), window.expiresAt());
            if (!store.insert(transfer)) { tell(player, "warp_pending"); return false; }
            Map<String, Object> payload = new HashMap<>();
            payload.put("transferId", transferId);
            payload.put("uid", uid);
            payload.put("sourceGateId", window.sourceGateId());
            payload.put("targetGateId", targetGateId);
            payload.put("data", data);
            if (!network.transfer("transferStart", transferId, payload)) {
                store.transition(transferId, "PREPARED", "CANCELLED");
                tell(player, "offline");
                return false;
            }
            if (passage) {
                if (travelScreen != null) travelScreen.show(player, startedAt, screenEnabled);
                player.setInvisible(true);
            }
            tell(player, "warp_preparing");
            return true;
        } catch (SQLException | RuntimeException ex) { log("Cannot prepare outgoing transfer", ex); tell(player, "warp_pending"); return false; }
    }

    public void incoming(JsonObject payload) {
        String id = payload.get("transferId").getAsString();
        String uid = payload.get("uid").getAsString();
        String gateId = payload.get("targetGateId").getAsString();
        try {
            if (gates.gate(gateId) == null || payload.get("expiresAt").getAsLong() <= System.currentTimeMillis()) {
                network.transfer("transferInterrupted", id, Map.of("transferId", id)); return;
            }
            Transfer existing = store.byId(id);
            if (existing == null) {
                if (manualEscrow.escrow(uid) != null || manualEscrow.legacyRecovery(uid) != null) {
                    network.transfer("transferInterrupted", id, Map.of("transferId", id)); return;
                }
                Base base = store.base(uid);
                if (base != null && base.state().equals("HELD")) {
                    network.transfer("transferInterrupted", id, Map.of("transferId", id)); return;
                }
                Player alreadyOnline = Server.getPlayerByUID(uid);
                if (alreadyOnline != null && alreadyOnline.isConnected()) {
                    network.transfer("transferInterrupted", id, Map.of("transferId", id)); return;
                }
                for (Transfer other : store.forPlayer(uid)) {
                    if (!terminal(other.state())) {
                        network.transfer("transferInterrupted", id, Map.of("transferId", id)); return;
                    }
                }
                JsonObject data = payload.getAsJsonObject("data");
                if (data == null || !store.insert(new Transfer(id, uid, "IN", "PREPARED", gateId,
                        payload.get("sourceGateId").getAsString(), data.toString(), payload.get("expiresAt").getAsLong()))) {
                    return;
                }
            } else if (!existing.uid().equals(uid) || !existing.direction().equals("IN")) {
                log("Conflicting incoming transfer", new IllegalStateException(id)); return;
            }
            Transfer current = store.byId(id);
            if (current.state().equals("PREPARED")) network.transfer("transferAccepted", id, Map.of("transferId", id));
            else if (current.state().equals("APPLIED")) network.transfer("transferDone", id, Map.of("transferId", id));
            Player online = Server.getPlayerByUID(uid);
            if (online != null && online.isConnected() && current.state().equals("PREPARED")) {
                online.setInvisible(true);
                plugin.executeDelayed(1f, () -> claim(online, id));
            }
        } catch (SQLException | RuntimeException ex) { log("Cannot persist incoming transfer", ex); }
    }

    public void accepted(JsonObject payload) {
        if (!payload.get("state").getAsString().equals("ACCEPTED")) return;
        String id = payload.get("transferId").getAsString();
        try {
            Transfer transfer = store.byId(id);
            if (transfer == null || !transfer.direction().equals("OUT") || !transfer.state().equals("PREPARED")) return;
            if (!payload.has("host") || !payload.has("port")) return;
            Player player = Server.getPlayerByUID(transfer.uid());
            if (player == null || !player.isConnected()) { abort(id); return; }
            if (!matches(player, data(transfer))) { abort(id); tell(player, "warp_changed"); return; }
            if (!store.transition(id, "PREPARED", "CLEARING")) return;
            player.setInvisible(true);
            clear(player);
            Server.savePlayers();
            if (!empty(player) || !store.transition(id, "CLEARING", "CLEARED")) {
                throw new IllegalStateException("Cannot clear outgoing inventory");
            }
            network.transfer("transferReleased", id, Map.of("transferId", id));
        } catch (SQLException | RuntimeException ex) { log("Cannot start accepted transfer", ex); }
    }

    public void released(JsonObject payload) {
        if (!payload.get("state").getAsString().equals("RELEASED")) return;
        String id = payload.get("transferId").getAsString();
        try {
            Transfer transfer = store.byId(id);
            if (transfer == null || !transfer.direction().equals("OUT") || !transfer.state().equals("CLEARED")) return;
            if (!payload.has("host") || !payload.has("port")) return;
            Player player = Server.getPlayerByUID(transfer.uid());
            if (player == null || !player.isConnected()) { abort(id); return; }
            if (!empty(player)) { abort(id); return; }
            String address = payload.get("host").getAsString() + ":" + payload.get("port").getAsInt();
            if (!store.transition(id, "CLEARED", "PROMPTED")) throw new IllegalStateException("Cannot prompt transfer");
            tell(player, "warp_departing", "PH_GATE", transfer.peerGateId());
            player.connectToOtherServer(address, null, success -> plugin.executeDelayed(0f, () -> {
                try {
                    if (Boolean.TRUE.equals(success)) {
                        if (store.transition(id, "PROMPTED", "DEPARTING") && discordEvents != null)
                            discordEvents.externalDeparture(player, transfer.gateId(), transfer.peerGateId());
                    }
                    else if (store.transition(id, "PROMPTED", "ABORTING")) {
                        if (travelScreen != null) travelScreen.remove(player);
                        abort(id);
                    }
                } catch (SQLException ex) { log("Cannot record connect outcome", ex); }
            }));
        } catch (SQLException | RuntimeException ex) { log("Cannot prompt released transfer", ex); }
    }

    public void failed(JsonObject payload) {
        if (!payload.get("state").getAsString().equals("ABORTED")) return;
        String id = payload.get("transferId").getAsString();
        try {
            Transfer transfer = store.byId(id);
            if (transfer == null || !transfer.direction().equals("OUT") || terminal(transfer.state())) return;
            if (travelScreen != null) travelScreen.remove(Server.getPlayerByUID(transfer.uid()));
            if (transfer.state().equals("PREPARED")) {
                store.transition(id, "PREPARED", "CANCELLED");
                Player player = Server.getPlayerByUID(transfer.uid());
                if (player != null && player.isConnected()) restoreVisibility(player, data(transfer));
                tell(player, "warp_failed", "PH_REASON", "aborted");
                return;
            }
            if (transfer.state().equals("CLAIMED")) return;
            if (!transfer.state().equals("ABORTED")) store.transition(id, transfer.state(), "ABORTED");
            Player player = Server.getPlayerByUID(transfer.uid());
            if (player != null && player.isConnected()) restoreOutgoing(player, store.byId(id));
        } catch (SQLException | RuntimeException ex) { log("Cannot restore failed transfer", ex); }
    }

    public void done(JsonObject payload) {
        if (!payload.get("state").getAsString().equals("DONE")) return;
        String id = payload.get("transferId").getAsString();
        try {
            Transfer transfer = store.byId(id);
            if (transfer == null) return;
            if (transfer.direction().equals("IN")) { store.transition(id, "APPLIED", "DONE"); return; }
            if (terminal(transfer.state())) return;
            if (transfer.state().equals("PREPARED")) throw new IllegalStateException("Relay done before source inventory was cleared");
            store.completeOutgoing(transfer);
        } catch (SQLException | RuntimeException ex) { log("Cannot complete outgoing transfer", ex); }
    }

    public void cancelled(JsonObject payload) {
        String id = payload.get("transferId").getAsString();
        try {
            Transfer transfer = store.byId(id);
            if (transfer != null && transfer.direction().equals("IN") && transfer.state().equals("PREPARED")) {
                store.transition(id, "PREPARED", "CANCELLED");
                Player player = Server.getPlayerByUID(transfer.uid());
                if (travelScreen != null) travelScreen.remove(player);
                store.clearArrivalScreen(transfer.uid(), id);
                if (player != null && player.isConnected()) restoreVisibility(player, data(transfer));
            }
        } catch (SQLException ex) { log("Cannot cancel incoming transfer", ex); }
    }

    public void status(JsonObject payload) {
        String state = payload.get("state").getAsString();
        try {
            Transfer local = store.byId(payload.get("transferId").getAsString());
            if (local != null && local.direction().equals("IN")) {
                if (state.equals("DONE")) store.transition(local.id(), "APPLIED", "DONE");
                else if (state.equals("CLAIMED")) claimed(payload);
                else if (state.equals("RELEASED")) claim(Server.getPlayerByUID(local.uid()), local.id());
                else if (state.equals("PENDING")) network.transfer("transferAccepted", local.id(), Map.of("transferId", local.id()));
                else if (state.equals("ABORTED")) cancelled(payload);
                return;
            }
        } catch (SQLException ex) { log("Cannot reconcile incoming status", ex); return; }
        if (state.equals("DONE")) done(payload);
        else if (state.equals("ABORTED")) { failed(payload); cancelled(payload); }
        else if (state.equals("RELEASED")) released(payload);
        else if (state.equals("ACCEPTED")) {
            try {
                Transfer transfer = store.byId(payload.get("transferId").getAsString());
                if (transfer != null && transfer.direction().equals("OUT") && transfer.state().equals("CLEARED")) {
                    network.transfer("transferReleased", transfer.id(), Map.of("transferId", transfer.id()));
                } else if (payload.has("host")) accepted(payload);
            } catch (SQLException ex) { log("Cannot resume accepted transfer", ex); }
        }
    }

    public void claimed(JsonObject payload) {
        String id = payload.get("transferId").getAsString();
        if (!payload.get("state").getAsString().equals("CLAIMED")) return;
        try {
            Transfer transfer = store.byId(id);
            if (transfer == null || !transfer.direction().equals("IN")) return;
            Player player = Server.getPlayerByUID(transfer.uid());
            if (player == null || !player.isConnected()) return;
            if (transfer.state().equals("APPLIED")) {
                if (id.equals(store.pendingArrival(transfer.uid())))
                    finishVisibleArrival(player, transfer, data(transfer));
                network.transfer("transferDone", id, Map.of("transferId", id));
                return;
            }
            JsonObject incoming = data(transfer);
            player.setInvisible(true);
            showIncomingScreen(player, transfer, incoming);
            if (transfer.state().equals("APPLYING")) {
                if (matches(player, incoming)) {
                    store.markArrivalPending(transfer.uid(), id);
                    finishArrival(player, transfer, incoming);
                    if (store.transition(id, "APPLYING", "APPLIED")) {
                        completeAppliedArrival(player, transfer, incoming);
                        if (discordEvents != null) discordEvents.externalArrival(player, transfer.peerGateId(), transfer.gateId());
                    }
                    network.transfer("transferDone", id, Map.of("transferId", id));
                } else tell(player, "warp_manual");
                return;
            }
            if (!transfer.state().equals("PREPARED")) return;
            Base prior = store.base(transfer.uid());
            if (prior == null) {
                // An empty local character has nothing to restore after this visit.
                // Keep saving nonempty inventories before any incoming mutation.
                if (!empty(player)) {
                    Map<String, Object> snapshot = capture(player);
                    if (snapshot == null || !store.saveBase(transfer.uid(), decode((String) snapshot.get("inventory")),
                            decode((String) snapshot.get("clothes")))) throw new IllegalStateException("Cannot save target inventory");
                }
            } else if (prior.state().equals("HELD")) {
                JsonObject original = new JsonObject();
                original.addProperty("inventory", Base64.getEncoder().encodeToString(prior.inventory()));
                original.addProperty("clothes", Base64.getEncoder().encodeToString(prior.clothes()));
                if (!matches(player, original)) { tell(player, "warp_manual"); return; }
            } else if (!prior.state().equals("RESTORE_PENDING")) {
                tell(player, "warp_manual"); return;
            } else if (!empty(player)) {
                tell(player, "warp_manual"); return;
            } else store.baseState(transfer.uid(), "RESTORE_PENDING", "HELD");
            if (!store.transition(id, "PREPARED", "APPLYING")) return;
            apply(player, transfer, incoming);
        } catch (SQLException | RuntimeException ex) { log("Cannot apply incoming transfer", ex); tell(Server.getPlayerByUID(payload.get("uid").getAsString()), "warp_manual"); }
    }

    public void onSpawn(Player player) {
        try {
            for (Transfer transfer : store.forPlayer(player.getUID())) {
                if (transfer.direction().equals("IN") && (transfer.state().equals("PREPARED") || transfer.state().equals("APPLYING"))) {
                    player.setInvisible(true);
                    showIncomingScreen(player, transfer, data(transfer));
                    claim(player, transfer.id()); return;
                }
            }
            for (Transfer transfer : store.forPlayer(player.getUID())) {
                if (transfer.direction().equals("OUT") && transfer.state().equals("ABORTED")) {
                    restoreOutgoing(player, transfer); return;
                }
                if (transfer.direction().equals("OUT") && !terminal(transfer.state())) {
                    network.transfer("transferStatus", transfer.id(), Map.of("transferId", transfer.id()));
                    tell(player, "warp_manual"); return;
                }
            }
            String pendingId = store.pendingArrival(player.getUID());
            if (pendingId != null) {
                Transfer pending = store.byId(pendingId);
                if (pending != null && pending.direction().equals("IN")
                        && (pending.state().equals("APPLIED") || pending.state().equals("DONE"))) {
                    player.setInvisible(true);
                    finishVisibleArrival(player, pending, data(pending));
                    return;
                }
            }
            restoreBase(player);
        } catch (SQLException | RuntimeException ex) { log("Cannot reconcile transfer on spawn", ex); tell(player, "warp_manual"); }
    }

    public void onConnect(Player player) {
        try {
            if (store.pendingArrival(player.getUID()) != null) {
                player.setInvisible(true);
                return;
            }
            for (Transfer transfer : store.forPlayer(player.getUID())) {
                if (transfer.direction().equals("IN") && (transfer.state().equals("PREPARED")
                        || transfer.state().equals("APPLYING"))) {
                    player.setInvisible(true);
                    return;
                }
            }
        } catch (SQLException ex) { log("Cannot inspect incoming transfer on connect", ex); }
    }

    public boolean hasIncoming(String uid) throws SQLException {
        if (store.pendingArrival(uid) != null) return true;
        for (Transfer transfer : store.forPlayer(uid)) {
            if (transfer.direction().equals("IN") && (transfer.state().equals("PREPARED")
                    || transfer.state().equals("APPLYING"))) return true;
        }
        return false;
    }

    public void error(String id, String reason) {
        try {
            Transfer transfer = store.byId(id);
            if (transfer != null && transfer.direction().equals("OUT") && transfer.state().equals("PREPARED")) {
                store.transition(id, "PREPARED", "CANCELLED");
                Player player = Server.getPlayerByUID(transfer.uid());
                if (travelScreen != null) travelScreen.remove(player);
                if (player != null && player.isConnected()) restoreVisibility(player, data(transfer));
                tell(player, "warp_failed", "PH_REASON", reason);
            }
        } catch (SQLException ex) { log("Cannot handle transfer error", ex); }
    }

    private void claim(Player player, String id) {
        if (player != null && player.isConnected()) network.transfer("transferClaim", id, Map.of("transferId", id));
    }

    private void abort(String id) { network.transfer("transferAbort", id, Map.of("transferId", id)); }

    private void restoreOutgoing(Player player, Transfer transfer) throws SQLException {
        if (travelScreen != null) travelScreen.remove(player);
        JsonObject original = data(transfer);
        if (matches(player, original)) {
            if (store.transition(transfer.id(), "ABORTED", "CANCELLED")) restoreVisibility(player, original);
            tell(player, "warp_restored"); return;
        }
        if (!empty(player)) { tell(player, "warp_manual"); return; }
        if (restore(player, original) && store.transition(transfer.id(), "ABORTED", "CANCELLED")) {
            restoreVisibility(player, original);
            tell(player, "warp_restored");
        }
        else tell(player, "warp_manual");
    }

    private static void restoreVisibility(Player player, JsonObject original) {
        player.setInvisible(original.has("wasInvisible") && original.get("wasInvisible").getAsBoolean());
    }

    private void restoreBase(Player player) throws SQLException {
        Base base = store.base(player.getUID());
        if (base == null || !base.state().equals("RESTORE_PENDING")) return;
        if (!empty(player)) { tell(player, "warp_manual"); return; }
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("inventory", Base64.getEncoder().encodeToString(base.inventory()));
        snapshot.addProperty("clothes", Base64.getEncoder().encodeToString(base.clothes()));
        if (restore(player, snapshot) && store.consumeBase(player.getUID(), "RESTORE_PENDING")) tell(player, "warp_base_restored");
        else tell(player, "warp_manual");
    }

    private void apply(Player player, Transfer transfer, JsonObject incoming) throws SQLException {
        if (gates.gate(transfer.gateId()) == null) throw new IllegalStateException("Arrival gate removed");
        if (!restore(player, incoming)) { tell(player, "warp_manual"); return; }
        player.setInvisible(true);
        store.markArrivalPending(transfer.uid(), transfer.id());
        finishArrival(player, transfer, incoming);
        if (store.transition(transfer.id(), "APPLYING", "APPLIED")) {
            completeAppliedArrival(player, transfer, incoming);
            if (discordEvents != null) discordEvents.externalArrival(player, transfer.peerGateId(), transfer.gateId());
            network.transfer("transferDone", transfer.id(), Map.of("transferId", transfer.id()));
            tell(player, "warp_arrived", "PH_GATE", transfer.gateId());
        }
    }

    private void finishArrival(Player player, Transfer transfer, JsonObject incoming) throws SQLException {
        player.setHealth(Math.min(player.getMaxHealth(), incoming.get("health").getAsInt()));
        player.setHunger(Math.min(100, incoming.get("hunger").getAsInt()));
        player.setThirst(Math.min(100, incoming.get("thirst").getAsInt()));
        player.setStamina(Math.min(100, incoming.get("stamina").getAsInt()));
        LocalGateStore.Gate gate = gates.gate(transfer.gateId());
        if (gate == null) throw new IllegalStateException("Arrival gate removed");
        player.setPosition(gate.position());
        player.setRotation(gate.rotation());
        // Keep the arriving player hidden after the final position update.
        player.setInvisible(true);
        arrivalObserver.accept(player);
        Server.savePlayers();
    }

    private void completeAppliedArrival(Player player, Transfer transfer, JsonObject incoming) {
        finishVisibleArrival(player, transfer, incoming);
        arrivalSoundObserver.accept(player, transfer.gateId());
    }

    private void finishVisibleArrival(Player player, Transfer transfer, JsonObject incoming) {
        boolean screenEnabled = incoming.has("travelScreenEnabled") && incoming.get("travelScreenEnabled").getAsBoolean();
        if (travelScreen == null) {
            restoreVisibility(player, incoming);
            arrivalPlayerSoundObserver.accept(player, transfer.gateId());
            clearPendingArrival(transfer);
            return;
        }
        long startedAt = showIncomingScreen(player, transfer, incoming);
        travelScreen.finishArrival(player, startedAt, screenEnabled, () -> {
            restoreVisibility(player, incoming);
            if (player.isConnected()) arrivalPlayerSoundObserver.accept(player, transfer.gateId());
            clearPendingArrival(transfer);
            Server.savePlayers();
        });
    }

    private void clearPendingArrival(Transfer transfer) {
        try {
            store.clearPendingArrival(transfer.uid(), transfer.id());
            store.clearArrivalScreen(transfer.uid(), transfer.id());
        }
        catch (SQLException ex) { log("Cannot clear completed arrival", ex); }
    }

    private long showIncomingScreen(Player player, Transfer transfer, JsonObject incoming) {
        if (travelScreen == null || !incoming.has("travelScreenEnabled")
                || !incoming.get("travelScreenEnabled").getAsBoolean()) return 0;
        try {
            long startedAt = store.arrivalScreenStart(transfer.uid(), transfer.id(), System.currentTimeMillis());
            travelScreen.show(player, startedAt, true);
            return startedAt;
        } catch (SQLException ex) {
            log("Cannot start incoming travel screen", ex);
            return 0;
        }
    }

    private Map<String, Object> capture(Player player) {
        Inventory inventory = player.getInventory();
        Clothes clothes = player.getClothes();
        byte[] inv = inventory.serialize();
        byte[] worn = clothes.serialize();
        if (inv == null) inv = emptyInventory(inventory) ? new byte[0] : null;
        if (worn == null) worn = emptyClothes(clothes) ? new byte[0] : null;
        if (inv == null || worn == null) return null;
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("inventory", Base64.getEncoder().encodeToString(inv));
        snapshot.put("clothes", Base64.getEncoder().encodeToString(worn));
        snapshot.put("health", player.getHealth());
        snapshot.put("hunger", player.getHunger());
        snapshot.put("thirst", player.getThirst());
        snapshot.put("stamina", player.getStamina());
        snapshot.put("wasInvisible", player.isInvisible());
        return snapshot;
    }

    private static JsonObject data(Transfer transfer) { return JsonParser.parseString(transfer.data()).getAsJsonObject(); }

    private static byte[] decode(String value) { return Base64.getDecoder().decode(value); }

    private static boolean matches(Player player, JsonObject snapshot) {
        byte[] inv = decode(snapshot.get("inventory").getAsString());
        byte[] worn = decode(snapshot.get("clothes").getAsString());
        byte[] currentInv = player.getInventory().serialize();
        byte[] currentWorn = player.getClothes().serialize();
        if (currentInv == null && emptyInventory(player.getInventory())) currentInv = new byte[0];
        if (currentWorn == null && emptyClothes(player.getClothes())) currentWorn = new byte[0];
        return java.util.Arrays.equals(inv, currentInv) && java.util.Arrays.equals(worn, currentWorn);
    }

    private static boolean restore(Player player, JsonObject snapshot) {
        Inventory inventory = player.getInventory();
        Clothes clothes = player.getClothes();
        byte[] inv = decode(snapshot.get("inventory").getAsString());
        byte[] worn = decode(snapshot.get("clothes").getAsString());
        inventory.clear();
        clothes.removeAll();
        boolean good = (inv.length == 0 || inventory.deserialize(inv)) && (worn.length == 0 || clothes.deserialize(worn));
        inventory.syncWithClient();
        Server.savePlayers();
        return good;
    }

    private static void clear(Player player) {
        player.getInventory().clear();
        player.getClothes().removeAll();
        player.getInventory().syncWithClient();
    }

    private static boolean empty(Player player) { return emptyInventory(player.getInventory()) && emptyClothes(player.getClothes()); }
    private static boolean emptyInventory(Inventory inventory) {
        Item[] items = inventory.getAllItems();
        if (items == null) return true;
        for (Item item : items) if (item != null) return false;
        return true;
    }
    private static boolean emptyClothes(Clothes clothes) {
        Clothes.Garment[] garments = clothes.getAll();
        if (garments == null) return true;
        for (Clothes.Garment garment : garments) if (garment != null) return false;
        return true;
    }
    private static boolean terminal(String state) { return state.equals("DONE") || state.equals("CANCELLED") || state.equals("APPLIED"); }

    private void tell(Player player, String key) { tell(player, key, null, null); }
    private void tell(Player player, String key, String variable, String value) {
        if (player == null) return;
        String message = i18n.get("tc.stargate.network." + key, player);
        if (variable != null) message = message.replace(variable, value);
        if (key.equals("warp_manual")) player.kick(message);
        else StargateChat.debug(player, message);
    }
    private static void log(String context, Exception ex) { OZStargate.logger().error(context + ": " + ex.getMessage()); }
}
