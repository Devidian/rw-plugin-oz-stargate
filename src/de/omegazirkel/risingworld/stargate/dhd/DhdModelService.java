package de.omegazirkel.risingworld.stargate.dhd;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Server;
import net.risingworld.api.events.player.PlayerGameObjectInteractionEvent;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.RaycastResult;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.worldelements.Model;

/** Persistent model placement and proximity rendering; DhdService remains dial authority. */
public final class DhdModelService implements AutoCloseable {
    private static final double SHOW_DISTANCE_SQUARED = 96d * 96d;
    private static final double HIDE_DISTANCE_SQUARED = 112d * 112d;
    private static final double INTERACT_DISTANCE_SQUARED = 4d * 4d;
    private static final long CHECK_INTERVAL = 5_000_000_000L;
    private static final long CHECK_TIMEOUT = 8_000_000_000L;
    private static final int SURFACE_MASK = Layer.getBitmask(Layer.TERRAIN, Layer.CONSTRUCTION);
    private static final float AIM_DISTANCE = 20f;
    private static final float CLEARANCE_HEIGHT = 2.6f;
    private static final float[][] CLEARANCE_OFFSETS = {
        {0f, 0f}, {1.3f, 0f}, {-1.3f, 0f}, {0f, 1.3f}, {0f, -1.3f}
    };

    private static final class Viewer {
        final Player player;
        final Set<String> visible = new HashSet<>();
        long sequence, pending, deadline, nextCheck;
        Viewer(Player player) { this.player = player; }
    }

    private final OZStargate plugin;
    private final DhdModelStore store;
    private final LocalGateStore gates;
    private final DhdService dhd;
    private final DhdModelAssets assets;
    private final GateNetworkClient network;
    private final I18n i18n;
    private final Map<String, DhdModelPlacement> placements = new HashMap<>();
    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, Viewer> viewers = new HashMap<>();
    private final Map<String, Object> pendingAims = new HashMap<>();
    private boolean closed, renderFailed;

    public DhdModelService(OZStargate plugin, DhdModelStore store, LocalGateStore gates,
            DhdService dhd, DhdModelAssets assets, I18n i18n, GateNetworkClient network) throws SQLException {
        this.plugin = plugin; this.store = store; this.gates = gates; this.dhd = dhd; this.assets = assets; this.i18n = i18n;
        this.network = network;
        for (DhdModelPlacement placement : store.all()) placements.put(placement.gateId(), placement);
        dhd.setModelValidator(this::canUse);
        network.setDhdVisualStateObserver(this::updateAnimations);
    }

    public void start() { plugin.enqueue(this::tick); }

    public Vector3f audioPosition(String gateId) {
        DhdModelPlacement placement = placements.get(gateId);
        return placement == null ? null : new Vector3f(placement.x(), placement.y() + 2f, placement.z());
    }

    private void updateAnimations() {
        if (closed || renderFailed) return;
        try {
            for (Map.Entry<String, Model> entry : models.entrySet())
                ((AnimatedDhdModel) entry.getValue()).update(network.gateView(entry.getKey()));
        } catch (RuntimeException ex) { fail(ex); }
    }

    public void command(Player player, String command, String[] args) {
        if (!player.isAdmin()) { StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player)); return; }
        if (closed) return;
        if (args.length != 2) { tell(player, "usage", ""); return; }
        String gateId = args[1].toUpperCase(Locale.ROOT);
        try {
            LocalGateStore.Gate gate = gates.gate(gateId);
            if (gate == null) { tell(player, "missing_gate", gateId); return; }
            if (command.equals("removedhd")) {
                store.delete(gateId);
                placements.remove(gateId);
                removeModel(gateId);
                tell(player, "removed", gateId);
                return;
            }
            Vector3f position = player.getPosition();
            if (distanceSquared(position, gate.position()) > 32d * 32d) {
                tell(player, "near_gate", gateId); return;
            }
            aimPlacement(player, gateId, placement -> {
                try {
                    LocalGateStore.Gate current = gates.gate(gateId);
                    if (current == null) { tell(player, "missing_gate", gateId); return; }
                    if (distanceSquared(player.getPosition(), current.position()) > 32d * 32d
                            || distanceSquared(new Vector3f(placement.x(), placement.y(), placement.z()),
                                    current.position()) > 32d * 32d) {
                        tell(player, "near_gate", gateId); return;
                    }
                    Model model = createModel(placement);
                    if (!store.save(placement)) { tell(player, "missing_gate", gateId); return; }
                    removeModel(gateId);
                    placements.put(gateId, placement);
                    models.put(gateId, model);
                    renderFailed = false;
                    refresh();
                    tell(player, "saved", gateId);
                } catch (SQLException ex) {
                    OZStargate.logger().error("Cannot persist DHD model: " + ex.getMessage());
                    tell(player, "database_error", gateId);
                } catch (RuntimeException ex) {
                    fail(ex);
                    tell(player, "render_error", gateId);
                }
            });
        } catch (IllegalArgumentException ex) {
            tell(player, "horizontal", gateId);
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot persist DHD model: " + ex.getMessage());
            tell(player, "database_error", gateId);
        } catch (RuntimeException ex) {
            fail(ex);
            tell(player, "render_error", gateId);
        }
    }

    /** Resolve a floor point under the crosshair and verify space for the whole console. */
    public void aimPlacement(Player player, String gateId, Consumer<DhdModelPlacement> onReady) {
        if (closed || !player.isConnected() || !player.isSpawned() || !player.isAdmin()) return;
        Vector3f forward = player.getViewDirection();
        double length = Math.hypot(forward.x, forward.z);
        if (!Double.isFinite(length) || length < .001) { tell(player, "horizontal", gateId); return; }
        float dx = (float) (forward.x / length), dz = (float) (forward.z / length);
        String uid = player.getUID();
        Object token = new Object();
        if (pendingAims.putIfAbsent(uid, token) != null) { tell(player, "pending", gateId); return; }
        player.raycast(AIM_DISTANCE, SURFACE_MASK, false, hit -> plugin.enqueue(() -> {
            if (pendingAims.get(uid) != token) return;
            if (!validAim(player)) { pendingAims.remove(uid, token); return; }
            if (!floorHit(hit)) {
                pendingAims.remove(uid, token); tell(player, "surface", gateId); return;
            }
            Vector3f point = hit.getCollisionPoint();
            DhdModelPlacement placement = new DhdModelPlacement(gateId,
                    point.x, point.y + .02f, point.z, dx, dz);
            checkClearance(player, uid, token, placement, onReady, 0);
        }));
        plugin.executeDelayed(8f, () -> {
            if (pendingAims.remove(uid, token) && player.isConnected()) tell(player, "surface", gateId);
        });
    }

    private void checkClearance(Player player, String uid, Object token, DhdModelPlacement placement,
            Consumer<DhdModelPlacement> onReady, int index) {
        float[] offset = CLEARANCE_OFFSETS[index];
        player.raycastFromWorldPosition(new Vector3f(placement.x() + offset[0], placement.y() + .08f,
                        placement.z() + offset[1]), new Vector3f(0f, 1f, 0f), CLEARANCE_HEIGHT,
                SURFACE_MASK, false, hit -> plugin.enqueue(() -> {
                    if (pendingAims.get(uid) != token) return;
                    if (!validAim(player)) { pendingAims.remove(uid, token); return; }
                    if (hit != null && hit.hasCollision()) {
                        pendingAims.remove(uid, token); tell(player, "clearance", placement.gateId()); return;
                    }
                    if (index + 1 < CLEARANCE_OFFSETS.length) {
                        checkClearance(player, uid, token, placement, onReady, index + 1);
                    } else if (pendingAims.remove(uid, token)) {
                        onReady.accept(placement);
                    }
                }));
    }

    private boolean validAim(Player player) {
        return !closed && player.isConnected() && player.isSpawned() && player.isAdmin();
    }

    private static boolean floorHit(RaycastResult hit) {
        if (hit == null || !hit.hasCollision() || hit.getCollisionPoint() == null
                || hit.getCollisionNormal() == null || hit.getCollisionNormal().y < .7f) return false;
        Vector3f point = hit.getCollisionPoint();
        return Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z);
    }

    /** Publish a DHD already saved in the coordinated placement transaction. */
    public void registered(DhdModelPlacement placement) {
        try {
            Model model = createModel(placement);
            removeModel(placement.gateId());
            placements.put(placement.gateId(), placement);
            models.put(placement.gateId(), model);
            renderFailed = false;
            refresh();
        } catch (RuntimeException ex) { fail(ex); }
    }

    public void interact(PlayerGameObjectInteractionEvent event) {
        if (closed || event.isCancelled()) return;
        GameObject target = event.getGameObject();
        while (target != null) {
            for (Map.Entry<String, Model> entry : models.entrySet()) {
                if (entry.getValue() != target) continue;
                String gateId = entry.getKey();
                if (!canUse(event.getPlayer(), gateId)) return;
                event.setCancelled(true);
                dhd.openModel(event.getPlayer(), gateId);
                return;
            }
            target = target.getParent();
        }
    }

    private boolean canUse(Player player, String gateId) {
        DhdModelPlacement placement = placements.get(gateId);
        return !closed && player.isConnected() && placement != null && models.containsKey(gateId)
                && placement.distanceSquared(player.getPosition().x, player.getPosition().y,
                        player.getPosition().z) <= INTERACT_DISTANCE_SQUARED;
    }

    private Model createModel(DhdModelPlacement placement) {
        Model model = assets.create();
        model.setLocalPosition(placement.x(), placement.y(), placement.z());
        ((AnimatedDhdModel) model).update(network.gateView(placement.gateId()));
        // The authored keypad faces the player who looks toward the placement point.
        model.setLocalRotation(new Quaternion().lookAt(placement.forwardX(), 0f, placement.forwardZ()));
        return model;
    }

    private void tick() {
        if (closed) return;
        if (!renderFailed) {
            try {
                refresh();
                updateAnimations();
            }
            catch (RuntimeException ex) { fail(ex); }
        }
        plugin.executeDelayed(1f, this::tick);
    }

    private void refresh() {
        Set<String> online = new HashSet<>();
        for (Player player : Server.getAllPlayers()) {
            if (!player.isConnected() || !player.isSpawned()) continue;
            online.add(player.getUID());
            Viewer viewer = viewers.get(player.getUID());
            if (viewer != null && viewer.player != player) { disconnect(viewer.player); viewer = null; }
            if (viewer == null) {
                viewer = new Viewer(player);
                viewers.put(player.getUID(), viewer);
            }
            refreshViewer(viewer);
            checkPresence(viewer);
        }
        for (Viewer viewer : List.copyOf(viewers.values())) {
            if (!online.contains(viewer.player.getUID())) disconnect(viewer.player);
        }
        Set<String> used = new HashSet<>();
        for (Viewer viewer : viewers.values()) used.addAll(viewer.visible);
        models.keySet().removeIf(id -> !used.contains(id));
    }

    private void refreshViewer(Viewer viewer) {
        Vector3f position = viewer.player.getPosition();
        Set<String> wanted = new HashSet<>();
        for (DhdModelPlacement placement : placements.values()) {
            double max = viewer.visible.contains(placement.gateId()) ? HIDE_DISTANCE_SQUARED : SHOW_DISTANCE_SQUARED;
            if (placement.distanceSquared(position.x, position.y, position.z) <= max) wanted.add(placement.gateId());
        }
        for (String gateId : Set.copyOf(viewer.visible)) {
            if (wanted.contains(gateId)) continue;
            Model model = models.get(gateId);
            if (model != null) viewer.player.removeGameObject(model);
            viewer.visible.remove(gateId);
            viewer.pending = 0;
        }
        for (String gateId : wanted) {
            if (viewer.visible.contains(gateId)) continue;
            Model model = models.computeIfAbsent(gateId, id -> createModel(placements.get(id)));
            viewer.player.addGameObject(model);
            viewer.visible.add(gateId);
        }
    }

    private void checkPresence(Viewer viewer) {
        if (viewer.visible.isEmpty()) return;
        long now = System.nanoTime();
        if (viewer.pending != 0) {
            if (now < viewer.deadline) return;
            viewer.pending = 0;
            rebuild(viewer);
            return;
        }
        if (now < viewer.nextCheck) return;
        String gateId = viewer.visible.iterator().next();
        Model model = models.get(gateId);
        DhdModelPlacement expected = placements.get(gateId);
        if (model == null || expected == null) return;
        long token = ++viewer.sequence;
        viewer.pending = token;
        viewer.deadline = now + CHECK_TIMEOUT;
        model.readWorldPosition(viewer.player, actual -> plugin.enqueue(() -> {
            if (closed || viewers.get(viewer.player.getUID()) != viewer || viewer.pending != token) return;
            viewer.pending = 0;
            viewer.nextCheck = System.nanoTime() + CHECK_INTERVAL;
            if (models.get(gateId) != model || placements.get(gateId) != expected) return;
            if (actual == null || expected.distanceSquared(actual.x, actual.y, actual.z) > .01) rebuild(viewer);
        }));
    }

    private void rebuild(Viewer viewer) {
        for (String gateId : Set.copyOf(viewer.visible)) {
            Model model = models.get(gateId);
            if (model != null) viewer.player.removeGameObject(model);
        }
        viewer.visible.clear();
        viewer.pending = 0;
        viewer.nextCheck = System.nanoTime() + CHECK_INTERVAL;
        refreshViewer(viewer);
    }

    private void removeModel(String gateId) {
        Model old = models.remove(gateId);
        if (old == null) return;
        for (Viewer viewer : viewers.values()) {
            if (viewer.visible.remove(gateId)) viewer.player.removeGameObject(old);
            viewer.pending = 0;
        }
    }

    public void gateDeleted(String gateId) {
        placements.remove(gateId);
        removeModel(gateId);
    }

    public void disconnect(Player player) {
        Viewer viewer = viewers.remove(player.getUID());
        if (viewer != null) for (String gateId : viewer.visible) {
            Model model = models.get(gateId);
            if (model != null && player.isConnected()) player.removeGameObject(model);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        network.setDhdVisualStateObserver(() -> { });
        for (Viewer viewer : List.copyOf(viewers.values())) disconnect(viewer.player);
        models.clear(); placements.clear();
        pendingAims.clear();
        assets.close();
        dhd.setModelValidator((player, gateId) -> false);
    }

    private void fail(RuntimeException ex) {
        if (!renderFailed) OZStargate.logger().error("DHD model rendering suspended until placement retry/reload: " + ex.getMessage());
        renderFailed = true;
    }

    private void tell(Player player, String key, String gateId) {
        StargateChat.debug(player, i18n.get("tc.stargate.dhd_model." + key, player).replace("PH_GATE", gateId));
    }

    private static double distanceSquared(Vector3f a, Vector3f b) {
        double x = a.x - (double) b.x, y = a.y - (double) b.y, z = a.z - (double) b.z;
        return x * x + y * y + z * z;
    }
}
