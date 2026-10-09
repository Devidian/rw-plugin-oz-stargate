package de.omegazirkel.risingworld.stargate.addressbook;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelPlacement;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.bridge.FactionBridge;
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
    private final FactionBridge factions;
    private final Map<String, Vector3i> lastChunks = new HashMap<>();
    private final Map<String, String> lastCodes = new HashMap<>();
    private final Set<String> syncing = new HashSet<>();
    private final Map<String, List<Consumer<Boolean>>> syncWaiters = new HashMap<>();
    private Runnable changed = () -> { };
    private Predicate<String> discoveryAllowed = uid -> true;
    private boolean closed;

    public AddressBookService(OZStargate plugin, PluginSettings settings, I18n i18n,
            AddressBookStore store, DhdStore objects, DhdModelStore models, LocalGateStore gates,
            GateNetworkClient network) {
        this.plugin = plugin; this.settings = settings; this.i18n = i18n; this.store = store;
        this.objects = objects; this.models = models; this.gates = gates; this.network = network;
        this.factions = new FactionBridge(plugin);
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
        try { return settings.networkEnabled ? store.known(code, player.getUID()) : store.knownLocal(player.getUID()); }
        catch (SQLException ex) { error(ex); return List.of(); }
    }

    public List<String> knownAddresses(Player player) {
        try {
            String code = code();
            List<String> addresses = new ArrayList<>();
            for (String gateId : known(player)) {
                String address = gates.exists(gateId) ? gates.localAddress(gateId) : store.address(code, gateId);
                addresses.add(address == null ? gateId : address);
            }
            return addresses.stream().distinct().sorted().toList();
        } catch (SQLException ex) { error(ex); return List.of(); }
    }

    public void setGateDetails(String gateId, String address, String localAddress, String alias) {
        try { store.setGateDetails(code(), gateId, address, localAddress, alias); }
        catch (SQLException ex) { error(ex); }
    }

    public String localByAddress(String address) {
        try { return store.localByAddress(code(), address); }
        catch (SQLException ex) { error(ex); return null; }
    }

    public String aliasByAddress(String address) {
        try { return store.aliasByAddress(code(), address); }
        catch (SQLException ex) { error(ex); return null; }
    }

    public boolean migrateLocalGates(String fromCode, String toCode) {
        try { store.migrateLocalGates(fromCode, toCode, gates.ids()); changed.run(); return true; }
        catch (SQLException ex) { error(ex); return false; }
    }

    private String gateId(String address) throws SQLException {
        String local = gates.gateByAddress(address);
        return local == null ? store.gateIdByAddress(code(), address) : local;
    }

    public String personalName(Player player, String address) {
        if (player == null || !knownAddresses(player).contains(address)) return null;
        try { String id = gateId(address); return id == null ? null : store.personalName(player.getUID(), id); }
        catch (SQLException ex) { error(ex); return null; }
    }

    public void setPersonalName(Player player, String address, String name) {
        if (player == null || !knownAddresses(player).contains(address)) return;
        try { String id = gateId(address); if (id != null) { store.setPersonalName(player.getUID(), id, name.trim()); changed.run(); } }
        catch (SQLException ex) { error(ex); }
    }

    public boolean hasFaction(Player player) { return player != null && factions.factionIdForPlayer(player.getDbID()) != null; }

    public boolean allShared(Player player) {
        if (player == null) return false;
        Integer faction = factions.factionIdForPlayer(player.getDbID());
        if (faction == null) return false;
        try { return store.allShared(player.getDbID(), faction); }
        catch (SQLException ex) { error(ex); return false; }
    }

    public boolean sharedOwn(Player player, String address) {
        if (player == null || !knownAddresses(player).contains(address)) return false;
        Integer faction = factions.factionIdForPlayer(player.getDbID());
        if (faction == null) return false;
        try {
            String id = gateId(address);
            return id != null && gates.exists(id) && store.shared(player.getDbID(), faction, id);
        } catch (SQLException ex) { error(ex); return false; }
    }

    public void toggleShare(Player player, String address) {
        if (player == null || !knownAddresses(player).contains(address)) return;
        Integer faction = factions.factionIdForPlayer(player.getDbID());
        if (faction == null) return;
        try {
            String id = gateId(address);
            if (id == null || !gates.exists(id)) return;
            boolean enabled = !store.shared(player.getDbID(), faction, id);
            store.setShare(player.getDbID(), player.getUID(), faction, id, enabled);
            changed.run();
        } catch (SQLException ex) { error(ex); }
    }

    public void toggleAll(Player player) {
        if (player == null) return;
        Integer faction = factions.factionIdForPlayer(player.getDbID());
        if (faction == null) return;
        try { store.setAllShared(player.getDbID(), player.getUID(), faction, !store.allShared(player.getDbID(), faction)); changed.run(); }
        catch (SQLException ex) { error(ex); }
    }

    public List<String> sharedFromFaction(Player player) {
        if (player == null) return List.of();
        Integer faction = factions.factionIdForPlayer(player.getDbID());
        if (faction == null) return List.of();
        Set<String> addresses = new HashSet<>();
        Map<String, List<String>> knownByOwner = new HashMap<>();
        try {
            for (AddressBookStore.Share share : store.shares(faction)) {
                if (share.ownerDbId() == player.getDbID() || !faction.equals(factions.factionIdForPlayer(share.ownerDbId()))) continue;
                List<String> ownerKnown = knownByOwner.get(share.ownerUid());
                if (ownerKnown == null) {
                    ownerKnown = settings.networkEnabled ? store.known(code(), share.ownerUid()) : store.knownLocal(share.ownerUid());
                    knownByOwner.put(share.ownerUid(), ownerKnown);
                }
                List<String> ids = share.all() ? ownerKnown : List.of(share.gateId());
                for (String id : ids) {
                    if (!gates.exists(id) || !ownerKnown.contains(id)) continue;
                    String address = gates.localAddress(id);
                    if (address != null) addresses.add(address);
                }
            }
        } catch (SQLException ex) { error(ex); return List.of(); }
        addresses.removeAll(knownAddresses(player));
        return addresses.stream().sorted().toList();
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
        sync(player, null);
    }

    /** Returns true only after a current-code relay snapshot has been applied. */
    public void sync(Player player, Consumer<Boolean> completed) {
        if (closed || player == null || !player.isConnected() || !network.isReady() || !settings.networkEnabled) {
            if (completed != null) completed.accept(false);
            return;
        }
        String code = code(), uid = player.getUID();
        if (code == null || code.isBlank()) {
            if (completed != null) completed.accept(false);
            return;
        }
        if (completed != null) syncWaiters.computeIfAbsent(uid, ignored -> new ArrayList<>()).add(completed);
        if (!syncing.add(uid)) return;
        try {
            store.movePending(UNASSIGNED, code, uid);
            if (!"LOCAL".equals(code)) store.movePending("LOCAL", code, uid);
            List<String> pending = store.pending(code, uid);
            List<String> sent = new ArrayList<>();
            for (String id : pending) {
                if (sent.size() == 256) break;
                if (!gates.exists(id) || gates.globalAddress(id) != null) sent.add(id);
            }
            network.syncAddressBook(player, sent, snapshot -> {
                syncing.remove(uid);
                List<Consumer<Boolean>> waiters = syncWaiters.remove(uid);
                if (snapshot == null || !code.equals(code())) {
                    complete(waiters, false);
                    return;
                }
                try {
                    List<String> later = new ArrayList<>(store.pending(code, uid));
                    later.removeAll(sent);
                    List<String> before = store.known(code, uid);
                    store.replace(code, uid, snapshot);
                    for (String id : later) store.learn(code, uid, id);
                    if (!before.equals(store.known(code, uid))) changed.run();
                    complete(waiters, true);
                    boolean retry = false;
                    for (String id : later) {
                        if (!pending.contains(id) || !gates.exists(id) || gates.globalAddress(id) != null) {
                            retry = true;
                            break;
                        }
                    }
                    if (retry) sync(player);
                } catch (SQLException ex) {
                    complete(waiters, false);
                    error(ex);
                }
            });
        } catch (SQLException ex) {
            syncing.remove(uid);
            complete(syncWaiters.remove(uid), false);
            error(ex);
        }
    }

    private static void complete(List<Consumer<Boolean>> waiters, boolean current) {
        if (waiters != null) for (Consumer<Boolean> waiter : waiters) waiter.accept(current);
    }

    public void removed(String gateId) {
        try { store.remove(code(), gateId); changed.run(); }
        catch (SQLException ex) { error(ex); }
    }

    public void disconnect(Player player) {
        lastChunks.remove(player.getUID()); lastCodes.remove(player.getUID()); syncing.remove(player.getUID());
        syncWaiters.remove(player.getUID());
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
    @Override public void close() { closed = true; lastChunks.clear(); lastCodes.clear(); syncing.clear(); syncWaiters.clear(); }
}
