package de.omegazirkel.risingworld.stargate.sector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Player;

/** Same-server dial state. It never serializes player data or contacts the relay. */
public final class LocalDialService {
    public record Destination(String label, String gateId) { }
    static final int STEP_MS = GateAudioTiming.DIAL_STEP_MS;
    static final long OPEN_MS = GateAudioTiming.OPEN_MS;
    static final long CONNECT_MS = GateAudioTiming.INCOMING_TOTAL_MS;

    static final class Connection {
        final String source, target;
        final long startedAt;
        final long startedNanos;
        long stepNanos;
        long sourceOpenedNanos;
        long targetOpenedNanos;
        int chevrons;
        int incomingChevrons;
        boolean connecting;
        boolean open;
        long expiresAt;
        Connection(String source, String target, long now) {
            this.source = source; this.target = target; startedAt = now;
            startedNanos = System.nanoTime(); stepNanos = startedNanos;
        }
        boolean preemptible(String gateId) { return source.equals(gateId) && !connecting; }
        boolean expired(long now) { return open && now >= expiresAt; }
        boolean canTravel(long now) { return open && now < expiresAt; }
        boolean advance(long now) {
            if (open) return false;
            if (connecting) {
                if (now < expiresAt) {
                    int locks = Math.min(7, 1 + (int) ((now - (expiresAt - CONNECT_MS))
                            / GateAudioTiming.INCOMING_CHEVRON_MS));
                    if (locks == incomingChevrons) return false;
                    incomingChevrons = locks;
                    return true;
                }
                open = true;
                targetOpenedNanos = System.nanoTime();
                expiresAt = now + OPEN_MS;
                return true;
            }
            int step = (int) Math.min(7, (now - startedAt) / STEP_MS);
            if (step <= chevrons) return false;
            chevrons = step;
            stepNanos = System.nanoTime();
            return true;
        }
        void beginIncoming(long now) {
            connecting = true;
            incomingChevrons = 1;
            sourceOpenedNanos = System.nanoTime();
            expiresAt = now + CONNECT_MS;
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
        String state = (source && connection.connecting) || connection.open ? "OPEN"
                : source ? "OUTGOING" : "INCOMING";
        String direction = source ? "OUTGOING" : "INCOMING";
        return new GateNetworkClient.GateView(state, direction, source ? connection.target : connection.source,
                connection.open ? 7 : source ? connection.chevrons : connection.incomingChevrons,
                true, STEP_MS, connection.stepNanos,
                source ? connection.sourceOpenedNanos : connection.targetOpenedNanos);
    }

    /** Null means this gate has no local connection; zero means travel is not ready yet. */
    public Long closingAtNanos(String gateId) {
        Connection connection = byGate.get(gateId);
        return connection == null ? null : connection.open
                ? connection.targetOpenedNanos + OPEN_MS * 1_000_000L : 0L;
    }

    public String openTarget(String sourceGateId) {
        Connection connection = byGate.get(sourceGateId);
        return connection != null && connection.source.equals(sourceGateId)
                && connection.canTravel(System.currentTimeMillis()) ? connection.target : null;
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
            if (source.equals(target) || sectors.addressOf(source) == null) { tell(player, "unavailable"); return; }
            if (byGate.containsKey(source) || network.hasPendingUnregister(source)
                    || !"IDLE".equals(network.remoteGateView(source).state())) {
                tell(player, "busy"); return;
            }
            Connection connection = new Connection(source, target, System.currentTimeMillis());
            byGate.put(source, connection);
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
            if (connection.preemptible(connection.source)
                    && !"IDLE".equals(network.remoteGateView(connection.source).state())) {
                remove(connection); continue;
            }
            if (connection.expired(now)) remove(connection);
            else if (connection.advance(now)) {
                if (connection.chevrons == 7 && !connection.connecting && !connection.open) {
                    try {
                        if (sectors.addressOf(connection.target) == null || gates.gate(connection.target) == null
                                || network.hasPendingUnregister(connection.target)
                                || !"IDLE".equals(network.remoteGateView(connection.target).state())) {
                            remove(connection); continue;
                        }
                        Connection outgoing = byGate.get(connection.target);
                        if (outgoing != null) {
                            if (!outgoing.preemptible(connection.target)) { remove(connection); continue; }
                            remove(outgoing, false);
                        }
                        connection.beginIncoming(now);
                        byGate.put(connection.target, connection);
                    } catch (SQLException ex) {
                        OZStargate.logger().error("Local Stargate target check failed: " + ex.getMessage());
                        remove(connection); continue;
                    }
                }
                network.refreshViews();
                if (connection.open) StargateChat.incoming(connection.target, sectors, i18n);
            }
        }
        plugin.executeDelayed(byGate.values().stream().anyMatch(connection -> connection.connecting)
                ? .05f : .2f, this::tick);
    }

    private void remove(Connection connection) {
        remove(connection, true);
    }

    private void remove(Connection connection, boolean refresh) {
        byGate.remove(connection.source, connection);
        byGate.remove(connection.target, connection);
        if (refresh) network.refreshViews();
    }

    private void tell(Player player, String key) {
        if (player != null) StargateChat.debug(player, i18n.get("tc.stargate.sector." + key, player));
    }
}
