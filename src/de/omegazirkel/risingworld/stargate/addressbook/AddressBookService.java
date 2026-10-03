package de.omegazirkel.risingworld.stargate.addressbook;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelPlacement;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Server;
import net.risingworld.api.World;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.world.ObjectElement;
import net.risingworld.api.utils.Utils.ChunkUtils;
import net.risingworld.api.utils.Vector3i;

/** Discovers active consoles on chunk entry and synchronizes each player's cache. */
public final class AddressBookService implements AutoCloseable {
    private static final String UNASSIGNED = "UNASSIGNED";
    private final OZStargate plugin;
    private final PluginSettings settings;
    private final I18n i18n;
    private final AddressBookStore store;
    private final DhdStore objects;
    private final DhdModelStore models;
    private final LocalGateStore gates;
    private final GateNetworkClient network;
    private final Map<String, Vector3i> lastChunks = new HashMap<>();
    private final Map<String, String> lastCodes = new HashMap<>();
    private final Set<String> syncing = new HashSet<>();
    private Runnable changed = () -> { };
    private Predicate<String> discoveryAllowed = uid -> true;
    private boolean closed;

    public AddressBookService(OZStargate plugin, PluginSettings settings, I18n i18n,
            AddressBookStore store, DhdStore objects, DhdModelStore models, LocalGateStore gates,
            GateNetworkClient network) {
        this.plugin = plugin; this.settings = settings; this.i18n = i18n; this.store = store;
        this.objects = objects; this.models = models; this.gates = gates; this.network = network;
    }

    public void setChanged(Runnable observer) { changed = observer; }
    public void setDiscoveryAllowed(Predicate<String> allowed) { discoveryAllowed = allowed; }
    public void start() { plugin.executeDelayed(1f, this::tick); }

    private String code() {
        if (!settings.networkEnabled) return "LOCAL";
        if (network.networkCode() != null) return network.networkCode();
        return settings.networkCodeTrusted == null || settings.networkCodeTrusted.isBlank()
                ? UNASSIGNED : settings.networkCodeTrusted;
    }

    public List<String> known(Player player) {
        String code = code();
        if (player == null || code == null || code.isBlank()) return List.of();
        try { return store.known(code, player.getUID()); }
        catch (SQLException ex) { error(ex); return List.of(); }
    }

    public void discover(Player player, String gateId) {
        String code = code();
        if (closed || player == null || !player.isConnected() || code == null || code.isBlank()) return;
        try {
            if (store.learn(code, player.getUID(), gateId)) {
                player.sendTextMessage(i18n.get("tc.stargate.addressbook.discovered", player).replace("PH_GATE", gateId));
                changed.run();
            }
            sync(player);
        } catch (SQLException ex) { error(ex); }
    }

    /** Records a generated address even if the player disconnects before registration completes. */
    public void learn(String uid, String gateId) {
        if (closed || uid == null || uid.isBlank()) return;
        try {
            if (store.learn(code(), uid, gateId)) changed.run();
            Player player = Server.getPlayerByUID(uid);
            if (player != null && player.isConnected()) sync(player);
        } catch (SQLException ex) { error(ex); }
    }

    public void sync(Player player) {
        if (closed || player == null || !player.isConnected() || !network.isReady() || !settings.networkEnabled) return;
        String code = code(), uid = player.getUID();
        if (code == null || code.isBlank() || !syncing.add(uid)) return;
        try {
            store.movePending(UNASSIGNED, code, uid);
            List<String> pending = store.pending(code, uid);
            List<String> sent = pending.subList(0, Math.min(256, pending.size()));
            network.syncAddressBook(player, sent, snapshot -> {
                syncing.remove(uid);
                if (snapshot == null || !code.equals(code())) return;
                try {
                    List<String> later = new ArrayList<>(store.pending(code, uid));
                    later.removeAll(sent);
                    store.replace(code, uid, snapshot);
                    for (String id : later) store.learn(code, uid, id);
                    changed.run();
                    if (!later.isEmpty()) sync(player);
                } catch (SQLException ex) { error(ex); }
            });
        } catch (SQLException ex) { syncing.remove(uid); error(ex); }
    }

    public void removed(String gateId) {
        try { store.remove(code(), gateId); changed.run(); }
        catch (SQLException ex) { error(ex); }
    }

    public void disconnect(Player player) {
        lastChunks.remove(player.getUID()); lastCodes.remove(player.getUID()); syncing.remove(player.getUID());
    }

    public void check(Player player) {
        if (closed || player == null || !player.isConnected()) return;
        if (!discoveryAllowed.test(player.getUID())) return;
        Vector3i chunk = ChunkUtils.getChunkPosition(player.getPosition());
        String code = code(), uid = player.getUID();
        if (chunk == null || (chunk.equals(lastChunks.get(uid)) && code.equals(lastCodes.get(uid)))) return;
        lastChunks.put(player.getUID(), chunk);
        lastCodes.put(uid, code);
        try {
            for (DhdStore.Binding binding : objects.inChunk(chunk.x, chunk.y, chunk.z)) {
                ObjectElement object = World.getObject(binding.object().id(), chunk.x, chunk.y, chunk.z);
                if (object != null && object.isValid() && object.getCreationDate() == binding.createdAt()
                        && object.getTypeID() == binding.type()) discover(player, binding.gateId());
            }
            for (DhdModelPlacement model : models.all()) {
                Vector3i modelChunk = ChunkUtils.getChunkPosition(model.x(), model.y(), model.z());
                if (modelChunk != null && modelChunk.equals(chunk) && gates.exists(model.gateId()))
                    discover(player, model.gateId());
            }
        } catch (SQLException ex) { lastChunks.remove(uid); lastCodes.remove(uid); error(ex); }
    }

    private void tick() {
        if (closed) return;
        for (Player player : Server.getAllPlayers()) check(player);
        plugin.executeDelayed(1f, this::tick);
    }

    private static void error(SQLException ex) { OZStargate.logger().error("Stargate address book database failure: " + ex.getMessage()); }
    @Override public void close() { closed = true; lastChunks.clear(); lastCodes.clear(); syncing.clear(); }
}
