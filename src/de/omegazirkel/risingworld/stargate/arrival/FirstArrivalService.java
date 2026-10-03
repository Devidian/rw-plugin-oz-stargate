package de.omegazirkel.risingworld.stargate.arrival;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;
import de.omegazirkel.risingworld.stargate.horizon.HorizonService;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.stargate.ui.StargatePlayerPluginSettings;
import de.omegazirkel.risingworld.stargate.ui.TravelScreenService;
import de.omegazirkel.risingworld.stargate.visual.GateVisualService;
import net.risingworld.api.events.player.PlayerConnectEvent;
import net.risingworld.api.objects.Player;

/** One first regular arrival per world; transfers do not consume the visit. */
public final class FirstArrivalService {
    private static final float STEP_SECONDS = GateAudioTiming.INCOMING_CHEVRON_MS / 1000f;
    private static final float OPEN_SECONDS = 10f;

    private record Arrival(String uid, Player player, String gateId, boolean wasInvisible, boolean artificial) { }
    private final OZStargate plugin;
    private final FirstArrivalStore store;
    private final LocalGateStore gates;
    private final GateNetworkClient network;
    private final TransferService transfers;
    private final HorizonService horizons;
    private final GateVisualService visuals;
    private final TravelScreenService travelScreen;
    private record Candidate(Player player, boolean wasInvisible) { }
    private final Map<String, Candidate> candidates = new HashMap<>();
    private final Map<String, Arrival> active = new HashMap<>();
    private final Map<String, GateNetworkClient.GateView> reserved = new HashMap<>();
    private boolean closed;

    public FirstArrivalService(OZStargate plugin, FirstArrivalStore store, LocalGateStore gates,
            GateNetworkClient network, TransferService transfers, HorizonService horizons, GateVisualService visuals,
            TravelScreenService travelScreen) {
        this.plugin = plugin;
        this.store = store;
        this.gates = gates;
        this.network = network;
        this.transfers = transfers;
        this.horizons = horizons;
        this.visuals = visuals;
        this.travelScreen = travelScreen;
        network.setArtificialArrival(reserved::get, reserved::containsKey);
    }

    public void connect(PlayerConnectEvent event) {
        if (closed) return;
        Player player = event.getPlayer();
        String uid = player.getUID();
        try {
            FirstArrivalStore.Visit visit = store.visit(uid);
            if (transfers.hasIncoming(uid)) {
                if (visit == null && event.isNewPlayer()) store.pending(uid, false);
                return;
            }
            if (visit == null && !event.isNewPlayer()) {
                // Existing residents predate this feature.
                store.done(uid);
                return;
            }
            if (visit != null && "DONE".equals(visit.state())) return;
            if (visit == null || "PENDING".equals(visit.state())) store.arm(uid, player.isInvisible());
            visit = store.visit(uid);
            candidates.put(uid, new Candidate(player, visit.wasInvisible()));
            player.setInvisible(true);
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot inspect first Stargate visit: " + ex.getMessage());
        }
    }

    public void spawn(Player player) {
        if (closed) return;
        String uid = player.getUID();
        Candidate candidate = candidates.remove(uid);
        if (candidate == null) return;
        boolean wasInvisible = candidate.wasInvisible();
        player.setInvisible(true);
        travelScreen.show(player, System.currentTimeMillis(), StargatePlayerPluginSettings.travelScreenEnabled(player));
        try {
            if (transfers.hasIncoming(uid)) { travelScreen.remove(player); return; }
            List<String> free = new ArrayList<>(), incoming = new ArrayList<>();
            for (String gateId : gates.ids()) {
                if (!visuals.hasPlacement(gateId) || gates.gate(gateId) == null) continue;
                GateNetworkClient.GateView view = network.gateView(gateId);
                if (network.isIdleForArtificialArrival(gateId) && !network.hasPendingUnregister(gateId)) free.add(gateId);
                else if ("OPEN".equals(view.state()) && "INCOMING".equals(view.direction())) incoming.add(gateId);
            }
            if (!free.isEmpty()) {
                Collections.shuffle(free);
                start(new Arrival(uid, player, free.get(0), wasInvisible, true));
            } else if (!incoming.isEmpty()) {
                Collections.shuffle(incoming);
                start(new Arrival(uid, player, incoming.get(0), wasInvisible, false));
            } else {
                store.done(uid);
                travelScreen.remove(player);
                player.setInvisible(wasInvisible);
            }
        } catch (SQLException | RuntimeException ex) {
            Arrival arrival = active.get(uid);
            if (arrival != null) abort(arrival);
            else { travelScreen.remove(player); player.setInvisible(wasInvisible); }
            OZStargate.logger().error("Cannot start first Stargate arrival: " + ex.getMessage());
        }
    }

    private void start(Arrival arrival) throws SQLException {
        active.put(arrival.uid(), arrival);
        if (arrival.artificial()) {
            step(arrival, 1);
        } else {
            position(arrival);
            plugin.executeDelayed(OPEN_SECONDS, () -> finish(arrival));
        }
    }

    private void step(Arrival arrival, int chevrons) {
        if (active.get(arrival.uid()) != arrival) return;
        long now = System.nanoTime();
        reserved.put(arrival.gateId(), new GateNetworkClient.GateView("INCOMING", "INCOMING", "",
                chevrons, true, GateAudioTiming.INCOMING_CHEVRON_MS, now));
        network.refreshViews();
        if (chevrons < 7) plugin.executeDelayed(STEP_SECONDS, () -> step(arrival, chevrons + 1));
        else plugin.executeDelayed(STEP_SECONDS, () -> open(arrival));
    }

    private void open(Arrival arrival) {
        if (active.get(arrival.uid()) != arrival) return;
        long now = System.nanoTime();
        reserved.put(arrival.gateId(), new GateNetworkClient.GateView("OPEN", "INCOMING", "", 7,
                true, GateAudioTiming.INCOMING_CHEVRON_MS, now, now));
        network.refreshViews();
        try { position(arrival); }
        catch (SQLException | RuntimeException ex) {
            abort(arrival);
            OZStargate.logger().error("First Stargate arrival gate disappeared: " + ex.getMessage());
            return;
        }
        plugin.executeDelayed(OPEN_SECONDS, () -> finish(arrival));
    }

    private void position(Arrival arrival) throws SQLException {
        LocalGateStore.Gate gate = gates.gate(arrival.gateId());
        if (gate == null) throw new SQLException("Arrival gate removed: " + arrival.gateId());
        arrival.player().setPosition(gate.position());
        arrival.player().setRotation(gate.rotation());
        arrival.player().setInvisible(true);
        horizons.reset(arrival.player());
        travelScreen.remove(arrival.player());
        visuals.arrived(arrival.player(), arrival.gateId());
    }

    private void finish(Arrival arrival) {
        if (active.get(arrival.uid()) != arrival) return;
        active.remove(arrival.uid());
        if (arrival.artificial()) {
            reserved.remove(arrival.gateId());
            network.refreshViews();
        }
        try { store.done(arrival.uid()); }
        catch (SQLException ex) {
            OZStargate.logger().error("Cannot finish first Stargate visit: " + ex.getMessage());
        }
        if (arrival.player().isConnected()) {
            arrival.player().setInvisible(arrival.wasInvisible());
            visuals.arrivedPlayer(arrival.player(), arrival.gateId());
        }
    }

    private void abort(Arrival arrival) {
        if (active.remove(arrival.uid(), arrival) && arrival.artificial()) {
            reserved.remove(arrival.gateId());
            network.refreshViews();
        }
        if (arrival.player().isConnected()) travelScreen.remove(arrival.player());
        if (arrival.player().isConnected()) arrival.player().setInvisible(arrival.wasInvisible());
    }

    public void disconnect(Player player) {
        String uid = player.getUID();
        candidates.remove(uid);
        Arrival arrival = active.get(uid);
        if (arrival != null) abort(arrival);
    }

    public boolean inProgress(String uid) { return candidates.containsKey(uid) || active.containsKey(uid); }

    public void close() {
        closed = true;
        for (Arrival arrival : List.copyOf(active.values())) abort(arrival);
        for (Candidate candidate : candidates.values()) {
            if (candidate.player().isConnected()) {
                travelScreen.remove(candidate.player());
                candidate.player().setInvisible(candidate.wasInvisible());
            }
        }
        candidates.clear();
        reserved.clear();
        network.refreshViews();
    }
}
