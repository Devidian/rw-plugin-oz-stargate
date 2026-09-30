package de.omegazirkel.risingworld.stargate.sector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Player;

/** Same-server dial state. It never serializes player data or contacts the relay. */
public final class LocalDialService {
    public record Destination(String label, String gateId) { }
    static final int STEP_MS = 3500;
    static final long OPEN_MS = 60_000L;
    static final long CONNECT_MS = 700L;

    static final class Connection {
        final String source, target;
        final long startedAt;
        final long startedNanos;
        long stepNanos;
        long openedNanos;
        int chevrons;
        boolean connecting;
        boolean open;
        long expiresAt;
        Connection(String source, String target, long now) {
            this.source = source; this.target = target; startedAt = now;
            startedNanos = System.nanoTime(); stepNanos = startedNanos;
        }
        boolean preemptible(String gateId) { return source.equals(gateId) && !open; }
        boolean expired(long now) { return open && now >= expiresAt; }
        boolean advance(long now) {
            if (open) return false;
            if (connecting) {
                if (now < expiresAt) return false;
                open = true;
                openedNanos = System.nanoTime();
                expiresAt = now + OPEN_MS;
                return true;
            }
            int step = (int) Math.min(7, (now - startedAt) / STEP_MS);
            if (step <= chevrons) return false;
            chevrons = step;
            stepNanos = System.nanoTime();
            if (step == 7) {
                connecting = true;
                expiresAt = now + CONNECT_MS;
            }
            return true;
        }
    }

    private final OZStargate plugin;
    private final LocalSectorStore sectors;
    private final LocalGateStore gates;
    private final GateNetworkClient network;
    private final I18n i18n;
    private final Map<String, Connection> byGate = new HashMap<>();
    private boolean closed;

    public LocalDialService(OZStargate plugin, LocalSectorStore sectors, LocalGateStore gates,
            GateNetworkClient network, I18n i18n) {
        this.plugin = plugin; this.sectors = sectors; this.gates = gates; this.network = network; this.i18n = i18n;
    }

    public void start() { plugin.executeDelayed(0.2f, this::tick); }
    public void close() { closed = true; byGate.clear(); network.refreshViews(); }
    public void gateDeleted(String gateId) {
        Connection connection = byGate.get(gateId);
        if (connection != null) remove(connection);
    }

    public List<Destination> destinations(String sourceGateId, Player player) throws SQLException {
        List<Destination> result = new ArrayList<>();
        for (LocalSectorStore.Entry entry : sectors.all()) {
            if (!entry.gateId().equals(sourceGateId))
                result.add(new Destination(i18n.get("tc.stargate.sector.address", player)
                        .replace("PH_SECTOR", entry.address().toString()), entry.gateId()));
        }
        return List.copyOf(result);
    }

    public boolean isLocal(String gateId) throws SQLException { return sectors.addressOf(gateId) != null; }

    public GateNetworkClient.GateView view(String gateId, GateNetworkClient.GateView remote) {
        Connection connection = byGate.get(gateId);
        if (connection == null) {
            if (!"IDLE".equals(remote.state())) return remote;
            try {
                if (sectors.addressOf(gateId) == null) return remote;
            } catch (SQLException ex) { return remote; }
            return new GateNetworkClient.GateView("IDLE", "OUTGOING", "", 0, true);
        }
        boolean source = gateId.equals(connection.source);
        String state = connection.open ? "OPEN" : source ? "OUTGOING" : "INCOMING";
        String direction = source ? "OUTGOING" : "INCOMING";
        return new GateNetworkClient.GateView(state, direction, source ? connection.target : connection.source,
                connection.open ? 7 : connection.chevrons, true, STEP_MS, connection.stepNanos,
                connection.openedNanos);
    }

    public String openTarget(String sourceGateId) {
        Connection connection = byGate.get(sourceGateId);
        return connection != null && connection.source.equals(sourceGateId) && connection.open
                && connection.expiresAt > System.currentTimeMillis() ? connection.target : null;
    }

    /** A relay incoming dial interrupts a still-outgoing local sequence. */
    public boolean acceptRemoteIncoming(String gateId) {
        Connection connection = byGate.get(gateId);
        if (connection == null) return true;
        if (connection.preemptible(gateId)) {
            remove(connection);
            return true;
        }
        return false;
    }

    public void dial(Player player, String source, String target) {
        try {
            if (source.equals(target) || sectors.addressOf(source) == null || sectors.addressOf(target) == null
                    || gates.gate(target) == null) { tell(player, "unavailable"); return; }
            if (byGate.containsKey(source) || network.hasPendingUnregister(source)
                    || network.hasPendingUnregister(target)
                    || !"IDLE".equals(network.remoteGateView(source).state())) {
                tell(player, "busy"); return;
            }
            Connection targetConnection = byGate.get(target);
            if (targetConnection != null) {
                if (!targetConnection.preemptible(target)) { tell(player, "busy"); return; }
                remove(targetConnection);
            }
            if (!"IDLE".equals(network.remoteGateView(target).state())) { tell(player, "busy"); return; }
            Connection connection = new Connection(source, target, System.currentTimeMillis());
            byGate.put(source, connection); byGate.put(target, connection);
            network.refreshViews();
            tell(player, "started");
        } catch (SQLException ex) {
            OZStargate.logger().error("Local Stargate dial failed: " + ex.getMessage());
            tell(player, "database_error");
        }
    }

    private void tick() {
        if (closed) return;
        long now = System.currentTimeMillis();
        for (Connection connection : List.copyOf(new java.util.HashSet<>(byGate.values()))) {
            if (byGate.get(connection.source) != connection) continue;
            if (!"IDLE".equals(network.remoteGateView(connection.source).state())
                    || !"IDLE".equals(network.remoteGateView(connection.target).state())) {
                remove(connection); continue;
            }
            if (connection.expired(now)) remove(connection);
            else if (connection.advance(now)) {
                network.refreshViews();
                if (connection.open) StargateChat.incoming(connection.target, sectors, i18n);
            }
        }
        plugin.executeDelayed(0.2f, this::tick);
    }

    private void remove(Connection connection) {
        byGate.remove(connection.source, connection);
        byGate.remove(connection.target, connection);
        network.refreshViews();
    }

    private void tell(Player player, String key) {
        if (player != null) StargateChat.debug(player, i18n.get("tc.stargate.sector." + key, player));
    }
}
