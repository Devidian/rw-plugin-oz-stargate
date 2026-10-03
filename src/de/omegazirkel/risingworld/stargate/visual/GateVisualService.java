package de.omegazirkel.risingworld.stargate.visual;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.audio.GateAudioService;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.worldelements.Model;

/** Server-thread placement and proximity rendering. It cannot mutate travel state. */
public final class GateVisualService {
    private record Viewer(Player player, Set<String> gates, GateVisualPresence presence) { }
    private final OZStargate plugin;
    private final GateVisualStore store;
    private final LocalGateStore gates;
    private final GateModelAssets assets;
    private final I18n i18n;
    private final Predicate<String> alignedGate;
    private final GateNetworkClient network;
    private final GateAudioService audio;
    private final Map<String, GateVisualPlacement> placements = new HashMap<>();
    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, Viewer> viewers = new HashMap<>();
    private boolean closed;
    private boolean renderFailed;

    public GateVisualService(OZStargate plugin, GateVisualStore store, LocalGateStore gates,
            GateModelAssets assets, I18n i18n, Predicate<String> alignedGate, GateNetworkClient network,
            GateAudioService audio) throws SQLException {
        this.plugin = plugin; this.store = store; this.gates = gates; this.assets = assets; this.i18n = i18n; this.alignedGate = alignedGate;
        this.network = network;
        this.audio = audio;
        network.setVisualStateObserver(this::updateAnimations);
        for (GateVisualPlacement placement : store.all()) placements.put(placement.gateId(), placement);
    }

    public void start() {
        // rp resets the client API after onEnable. Sending now would be discarded by
        // that reset while our viewer cache already considered the model visible.
        plugin.enqueue(this::tick);
        plugin.enqueue(this::animationTick);
        plugin.enqueue(this::audioTick);
    }

    public void command(Player player, String command, String[] args) {
        if (!player.isAdmin()) { StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player)); return; }
        if (closed) return;
        if (args.length != 2) { tell(player, "usage", ""); return; }
        String id = args[1].toUpperCase(Locale.ROOT);
        try {
            if (!gates.exists(id)) { tell(player, "missing_gate", id); return; }
            if (alignedGate.test(id)) { tell(player, "aligned_locked", id); return; }
            if (command.equals("removegatemodel")) {
                store.delete(id);
                placements.remove(id);
                removeModel(id);
                tell(player, "removed", id);
                return;
            }
            Vector3f p = player.getPosition(), direction = player.getViewDirection();
            GateVisualPlacement placement = new GateVisualPlacement(id, p.x, p.y - GateVisualPlacement.PLACEMENT_DEPTH, p.z, direction.x, direction.z);
            Model model = createModel(placement);
            if (!store.save(placement)) { tell(player, "missing_gate", id); return; }
            removeModel(id);
            placements.put(id, placement);
            models.put(id, model);
            renderFailed = false;
            refresh();
            tell(player, "saved", id);
        } catch (IllegalArgumentException ex) {
            tell(player, "horizontal", id);
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot persist Stargate model: " + ex.getMessage());
            tell(player, "database_error", id);
        } catch (RuntimeException ex) {
            failRendering(ex);
            tell(player, "render_error", id);
        }
    }

    /** Publish a model persisted by the atomic first-placement transaction. */
    public void registered(GateVisualPlacement placement) {
        try {
            Model model = createModel(placement);
            removeModel(placement.gateId());
            placements.put(placement.gateId(), placement);
            models.put(placement.gateId(), model);
            renderFailed = false;
            refresh();
        } catch (RuntimeException ex) { failRendering(ex); }
    }

    public boolean hasPlacement(String gateId) { return placements.containsKey(gateId); }

    private Model createModel(GateVisualPlacement placement) {
        Model model = assets.createAnimated();
        ((AnimatedGateModel) model).update(network.gateView(placement.gateId()));
        model.setLocalPosition(placement.x(), placement.y(), placement.z());
        model.setLocalRotation(new Quaternion().lookAt(placement.forwardX(), 0f, placement.forwardZ()));
        return model;
    }

    private void updateAnimations() {
        if (closed || renderFailed) return;
        try {
            for (Map.Entry<String, Model> entry : models.entrySet()) {
                ((AnimatedGateModel) entry.getValue()).update(network.gateView(entry.getKey()));
            }
        } catch (RuntimeException ex) { failRendering(ex); }
        syncAudio();
    }

    private void audioTick() {
        if (closed) return;
        syncAudio();
        plugin.executeDelayed(.1f, this::audioTick);
    }

    private void syncAudio() {
        Map<String, Set<Player>> listeners = new HashMap<>();
        for (Viewer viewer : viewers.values()) for (String id : viewer.gates())
            listeners.computeIfAbsent(id, ignored -> new HashSet<>()).add(viewer.player());
        audio.update(placements, listeners, network);
    }

    public void travelled(Player traveler, String gateId) {
        GateVisualPlacement placement = placements.get(gateId);
        if (placement == null) return;
        Set<Player> listeners = new HashSet<>();
        for (Viewer viewer : viewers.values()) if (viewer.gates().contains(gateId)
                && !viewer.player().getUID().equals(traveler.getUID())) listeners.add(viewer.player());
        audio.travel(gateId, placement, listeners);
    }

    public void arrived(Player player, String gateId) {
        GateVisualPlacement placement = placements.get(gateId);
        if (placement == null) return;
        Set<Player> listeners = new HashSet<>();
        for (Viewer viewer : viewers.values()) if (viewer.gates().contains(gateId)
                && !viewer.player().getUID().equals(player.getUID())) listeners.add(viewer.player());
        audio.travel(gateId, placement, listeners);
    }

    public void arrivedPlayer(Player player, String gateId) {
        GateVisualPlacement placement = placements.get(gateId);
        if (placement != null) audio.travel(gateId, placement, Set.of(player));
    }

    private void animationTick() {
        if (closed) return;
        if (!renderFailed) {
            try {
                long now = System.nanoTime();
                for (Model model : models.values()) ((AnimatedGateModel) model).frame(now);
            } catch (RuntimeException ex) { failRendering(ex); }
        }
        // One loop per service, not per model/viewer; idle poses produce no network mutations.
        plugin.executeDelayed(1f / 30f, this::animationTick);
    }

    private void tick() {
        if (closed) return;
        if (!renderFailed) {
            try { refresh(); }
            catch (RuntimeException ex) { failRendering(ex); }
        }
        plugin.executeDelayed(1f, this::tick);
    }

    private void failRendering(RuntimeException ex) {
        if (!renderFailed) OZStargate.logger().error("Stargate model rendering suspended until placement retry/reload: " + ex.getMessage());
        renderFailed = true;
    }

    private void refresh() {
        Set<String> online = new HashSet<>();
        for (Player player : Server.getAllPlayers()) {
            if (!player.isConnected() || !player.isSpawned()) continue;
            online.add(player.getUID());
            refreshPlayer(player);
            checkPresence(viewers.get(player.getUID()));
        }
        for (Viewer viewer : List.copyOf(viewers.values())) {
            if (!online.contains(viewer.player().getUID())) disconnect(viewer.player());
        }
        Set<String> used = new HashSet<>();
        for (Viewer viewer : viewers.values()) used.addAll(viewer.gates());
        models.keySet().removeIf(id -> !used.contains(id));
        syncAudio();
    }

    private void refreshPlayer(Player player) {
        Viewer viewer = viewers.get(player.getUID());
        if (viewer != null && viewer.player() != player) { disconnect(viewer.player()); viewer = null; }
        if (viewer == null) {
            viewer = new Viewer(player, new HashSet<>(), new GateVisualPresence());
            viewers.put(player.getUID(), viewer);
        }
        Vector3f p = player.getPosition();
        List<GateVisualPlacement> nearby = GateVisualPlacement.nearby(placements.values(), viewer.gates(), p.x, p.y, p.z);
        Set<String> wanted = new HashSet<>();
        for (GateVisualPlacement placement : nearby) wanted.add(placement.gateId());
        for (String id : Set.copyOf(viewer.gates())) {
            if (!wanted.contains(id)) {
                player.removeGameObject(models.get(id));
                viewer.gates().remove(id);
                viewer.presence().reset(System.nanoTime());
            }
        }
        for (GateVisualPlacement placement : nearby) {
            if (!viewer.gates().contains(placement.gateId())) {
                Model model = models.computeIfAbsent(placement.gateId(), ignored -> createModel(placement));
                player.addGameObject(model);
                viewer.gates().add(placement.gateId());
            }
        }
    }

    /** The manual rp client reset can arrive after server startup/spawn callbacks. */
    private void checkPresence(Viewer viewer) {
        long now = System.nanoTime();
        if (viewer.gates().isEmpty()) { viewer.presence().reset(now); return; }
        if (viewer.presence().timedOut(now)) {
            rebuildViewer(viewer);
            return;
        }
        long token = viewer.presence().begin(now);
        if (token == 0) return;
        String gateId = viewer.gates().iterator().next();
        Model model = models.get(gateId);
        GateVisualPlacement expected = placements.get(gateId);
        // Exactly one client query per viewer, independent of the number of visible gates.
        model.readWorldPosition(viewer.player(), position -> plugin.enqueue(() -> {
            if (closed || viewers.get(viewer.player().getUID()) != viewer
                    || !viewer.presence().complete(token, System.nanoTime())) return;
            if (!viewer.player().isConnected() || models.get(gateId) != model
                    || !viewer.gates().contains(gateId) || placements.get(gateId) != expected) return;
            if (position == null || !Double.isFinite(expected.distanceSquared(position.x, position.y, position.z))
                    || expected.distanceSquared(position.x, position.y, position.z) > 0.01) {
                rebuildViewer(viewer);
            }
        }));
    }

    private void rebuildViewer(Viewer viewer) {
        if (closed || !viewer.player().isConnected() || !viewer.player().isSpawned()) return;
        try {
            // Remove registrations before adding them: no duplicate instances for this or other viewers.
            for (String id : Set.copyOf(viewer.gates())) viewer.player().removeGameObject(models.get(id));
            viewer.gates().clear();
            viewer.presence().reset(System.nanoTime());
            refreshPlayer(viewer.player());
            OZStargate.logger().info("Restored Stargate model visibility after missing client presence");
        } catch (RuntimeException ex) { failRendering(ex); }
    }

    /** Storage trigger already deleted its row; immediately remove its live model. */
    public void gateDeleted(String id) {
        placements.remove(id);
        removeModel(id);
        syncAudio();
    }

    private void removeModel(String id) {
        Model model = models.get(id);
        for (Viewer viewer : viewers.values()) {
            if (viewer.gates().remove(id)) {
                viewer.presence().reset(System.nanoTime());
                if (viewer.player().isConnected() && model != null) viewer.player().removeGameObject(model);
            }
        }
        models.remove(id);
    }

    public void onSpawn(Player player) {
        if (closed || renderFailed) return;
        try { refreshPlayer(player); }
        catch (RuntimeException ex) { failRendering(ex); }
    }

    public void disconnect(Player player) {
        audio.disconnect(player);
        Viewer viewer = viewers.get(player.getUID());
        if (viewer == null || viewer.player() != player) return;
        viewers.remove(player.getUID());
        if (player.isConnected()) {
            for (String id : viewer.gates()) player.removeGameObject(models.get(id));
        }
    }

    public void close() {
        closed = true;
        network.setVisualStateObserver(() -> { });
        for (Viewer viewer : List.copyOf(viewers.values())) disconnect(viewer.player());
        models.clear();
    }

    private void tell(Player player, String key, String id) {
        StargateChat.debug(player, i18n.get("tc.stargate.visual." + key, player).replace("PH_GATE", id));
    }
}
