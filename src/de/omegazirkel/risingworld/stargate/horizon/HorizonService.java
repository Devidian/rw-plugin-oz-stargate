package de.omegazirkel.risingworld.stargate.horizon;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.LocalDialService;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.stargate.transfer.TransferStore;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import net.risingworld.api.utils.Quaternion;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Server;
import net.risingworld.api.events.player.PlayerChangePositionEvent;
import net.risingworld.api.objects.Area;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.worldelements.Area3D;

/** Server-thread gate passage workflow; native areas are used only for temporary admin previews. */
public final class HorizonService {
    private final OZStargate plugin;
    private final HorizonStore store;
    private final LocalGateStore gates;
    private final LocalSectorStore sectors;
    private final LocalDialService localDial;
    private final GateNetworkClient network;
    private final TransferService transfers;
    private final I18n i18n;
    private final GateVisualStore visuals;
    private final TransferStore journal;
    private List<HorizonZone> zones;
    private static final class Movement {
        final HorizonEntryTracker tracker = new HorizonEntryTracker();
        boolean queued;
    }
    private record Preview(Player player, Area area, Area3D visual) { }
    private final Map<String, Movement> movements = new HashMap<>();
    private final Map<String, Preview> previews = new HashMap<>();
    private boolean closed;
    private Consumer<String> travelObserver = gateId -> { };
    private BiConsumer<Player, String> arrivalObserver = (player, gateId) -> { };

    public void setTravelObserver(Consumer<String> observer) { travelObserver = observer; }
    public void setArrivalObserver(BiConsumer<Player, String> observer) { arrivalObserver = observer; }

    public HorizonService(OZStargate plugin, HorizonStore store, LocalGateStore gates, LocalSectorStore sectors,
            GateNetworkClient network, LocalDialService localDial, TransferService transfers, I18n i18n,
            GateVisualStore visuals, TransferStore journal) throws SQLException {
        this.plugin = plugin; this.store = store; this.gates = gates;
        this.sectors = sectors; this.localDial = localDial;
        this.network = network; this.transfers = transfers; this.i18n = i18n;
        this.visuals = visuals; this.journal = journal;
        zones = store.all();
        baselinePlayers();
    }

    public void command(Player player, String command, String[] args) {
        if (!player.isAdmin()) { StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player)); return; }
        if (args.length < 2) { tell(player, "usage"); return; }
        String id = args[1].toUpperCase(Locale.ROOT);
        try {
            LocalGateStore.Gate gate = gates.gate(id);
            if (gate == null) { tell(player, "missing_gate"); return; }
            if (command.equals("showhorizon")) {
                HorizonZone zone = find(id);
                if (zone == null) tell(player, "missing_zone"); else preview(player, zone);
                return;
            }
            if (!network.isIdleForSetup(id) || journal.active().stream().anyMatch(t -> t.gateId().equals(id))) {
                tell(player, "edit_busy"); return;
            }
            if (command.equals("removehorizon")) {
                if (args.length != 2) { tell(player, "usage"); return; }
                store.delete(id); reload(); tell(player, "removed"); return;
            }
            if (command.equals("aligngate")) {
                if (args.length != 2) { tell(player, "usage_align"); return; }
                align(player, id); return;
            }
            if (isAligned(id)) { tell(player, "aligned_locked"); return; }
            if (args.length != 2 && args.length != 5) { tell(player, "usage"); return; }
            float width = args.length == 5 ? Float.parseFloat(args[2]) : 3f;
            float height = args.length == 5 ? Float.parseFloat(args[3]) : 3f;
            float depth = args.length == 5 ? Float.parseFloat(args[4]) : 1f;
            if (!dimension(width) || !dimension(height) || !dimension(depth)) { tell(player, "dimensions"); return; }
            Vector3f p = player.getPosition();
            Vector3f g = gate.position();
            if ((p.x-g.x)*(p.x-g.x) + (p.y-g.y)*(p.y-g.y) + (p.z-g.z)*(p.z-g.z) > 16*16) {
                tell(player, "near_gate"); return;
            }
            HorizonZone zone = new HorizonZone(id, p.x-width/2, p.y-0.2f, p.z-depth/2,
                    p.x+width/2, p.y+height, p.z+depth/2);
            if (zones.stream().anyMatch(other -> !other.gateId().equals(id) && zone.overlaps(other))) {
                tell(player, "overlap"); return;
            }
            if (!store.save(zone)) { tell(player, "missing_gate"); return; }
            reload(); tell(player, "saved"); preview(player, zone);
        } catch (IllegalArgumentException ex) { tell(player, "sector_boundary".equals(ex.getMessage()) ? "sector_boundary" : "dimensions");
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot update Stargate horizon: " + ex.getMessage());
            tell(player, "database_error");
        }
    }

    private void align(Player player, String id) throws SQLException {
        GateVisualPlacement placement = visuals.all().stream().filter(p -> p.gateId().equals(id)).findFirst().orElse(null);
        if (placement == null) { tell(player, "missing_model"); return; }
        AlignedPassage passage = AlignedPassage.from(placement);
        Vector3f arrival = player.getPosition();
        if (!passage.acceptsArrival(arrival.x, arrival.y, arrival.z)) { tell(player, "arrival_front"); return; }
        SectorAddress sector = sectors.addressOf(id);
        net.risingworld.api.utils.Vector2i arrivalSector = player.getSectorPosition();
        if (sector == null || arrivalSector == null || sector.x() != arrivalSector.x || sector.z() != arrivalSector.y) {
            tell(player, "sector_boundary"); return;
        }
        HorizonZone zone = passage.zone(id);
        if (zones.stream().anyMatch(other -> !other.gateId().equals(id) && zone.overlaps(other))) {
            tell(player, "overlap"); return;
        }
        Quaternion facing = new Quaternion().lookAt(-placement.forwardX(), 0f, -placement.forwardZ());
        if (!store.align(placement, arrival, facing)) { tell(player, "edit_busy"); return; }
        reload();
        tell(player, "aligned");
        preview(player, zone);
    }

    public boolean isAligned(String id) {
        HorizonZone zone = find(id);
        return zone != null && zone.aligned() != null;
    }

    private static boolean dimension(float value) { return Float.isFinite(value) && value >= 0.5f && value <= 8f; }
    private HorizonZone find(String id) { return zones.stream().filter(z -> z.gateId().equals(id)).findFirst().orElse(null); }
    private void reload() throws SQLException {
        zones = store.all();
        baselinePlayers();
        for (Preview preview : List.copyOf(previews.values())) removePreview(preview);
    }

    /** Refresh passage tracking after an atomic first placement. */
    public void registered() throws SQLException { reload(); }

    /** Called after local gate deletion; the SQLite trigger has already removed persisted bounds. */
    public void gateDeleted(String id) {
        zones = zones.stream().filter(zone -> !zone.gateId().equals(id)).toList();
        baselinePlayers();
        for (Preview preview : List.copyOf(previews.values())) removePreview(preview);
    }

    public void move(PlayerChangePositionEvent event) {
        if (closed || event.isCancelled() || zones.isEmpty()) return;
        Player player = event.getPlayer();
        String uid = player.getUID();
        Movement movement = movements.computeIfAbsent(uid, ignored -> new Movement());
        if (movement.queued) return;
        movement.queued = true;
        // Check actual position after all movement listeners, including claim/permission plugins.
        plugin.executeDelayed(0f, () -> {
            if (closed || movements.get(uid) != movement) return;
            movement.queued = false;
            if (!player.isConnected() || event.isCancelled()) return;
            Vector3f position = player.getPosition();
            String entered = movement.tracker.move(zones, position.x, position.y, position.z);
            if (entered == null) return;
            GateNetworkClient.GateView view = network.gateView(entered);
            if (!view.ready() || !"OPEN".equals(view.state()) || !"OUTGOING".equals(view.direction())) return;
            String localTarget = localDial.openTarget(entered);
            if (localTarget != null) {
                if (transfers.hasActive(uid)) return;
                try {
                    LocalGateStore.Gate destination = gates.gate(localTarget);
                    if (destination == null) return;
                    travelObserver.accept(entered);
                    boolean revealAfterArrival = !player.isInvisible();
                    if (revealAfterArrival) player.setInvisible(true);
                    try {
                        player.setPosition(destination.position());
                        player.setRotation(destination.rotation());
                        reset(player);
                    } finally {
                        if (revealAfterArrival && player.isConnected()) player.setInvisible(false);
                    }
                    arrivalObserver.accept(player, localTarget);
                    StargateChat.debug(player, i18n.get("tc.stargate.sector.arrived", player));
                } catch (SQLException ex) {
                    OZStargate.logger().error("Local Stargate arrival failed: " + ex.getMessage());
                }
                return;
            }
            GateNetworkClient.DialWindow window = network.openWindow(view.peerGateId());
            if (window == null || !entered.equals(window.sourceGateId())) return;
            // A single synchronous custody boundary; it also rejects active or ambiguous transfers.
            transfers.warp(player, window.targetGateId());
            travelObserver.accept(entered);
        });
    }

    /** Suppress queued movement and establish a fresh baseline after spawn/arrival. */
    public void reset(Player player) {
        Movement movement = new Movement();
        Vector3f p = player.getPosition();
        movement.tracker.move(zones, p.x, p.y, p.z);
        movements.put(player.getUID(), movement);
    }

    private void baselinePlayers() {
        movements.clear();
        for (Player player : Server.getAllPlayers()) reset(player);
    }

    private void preview(Player player, HorizonZone zone) {
        Preview prior = previews.get(player.getUID());
        if (prior != null) removePreview(prior);
        AlignedPassage aligned = zone.aligned();
        float radius = AlignedPassage.RADIUS, depth = AlignedPassage.HALF_DEPTH;
        Area area = aligned == null
                ? new Area(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())
                : new Area(0, 0, 0, radius*2, radius*2, depth*2);
        Area3D visual = new Area3D(area);
        if (aligned != null) {
            // Area3D's narrow visual axis is perpendicular to the model's
            // local Z axis. Turn the debug box a quarter-turn to face the ring.
            Quaternion rotation = new Quaternion().lookAt(-aligned.forwardZ(), 0f, aligned.forwardX());
            // Its pivot is the box's minimum corner; rotate the centre offset
            // by the same quaternion used for the rendered frame.
            Vector3f centreOffset = rotation.mult(new Vector3f(radius, radius, depth));
            visual.setLocalPosition(aligned.x() - centreOffset.x,
                    aligned.y() - centreOffset.y, aligned.z() - centreOffset.z);
            visual.setLocalRotation(rotation);
        }
        visual.setColor(0.1f, 0.5f, 1f, 0.2f);
        visual.setFrameColor(0.1f, 0.7f, 1f, 1f);
        visual.setFrameVisible(true);
        player.addGameObject(visual);
        Preview preview = new Preview(player, area, visual);
        previews.put(player.getUID(), preview);
        tell(player, aligned == null ? "preview" : "aligned_preview");
        plugin.executeDelayed(30f, () -> {
            if (previews.get(player.getUID()) == preview) removePreview(preview);
        });
    }

    private void removePreview(Preview preview) {
        previews.remove(preview.player().getUID(), preview);
        if (preview.player().isConnected()) preview.player().removeGameObject(preview.visual());
        preview.area().destroy();
    }
    public void disconnect(Player player) {
        movements.remove(player.getUID());
        Preview preview = previews.get(player.getUID());
        if (preview != null) removePreview(preview);
    }
    public void close() {
        closed = true; movements.clear();
        for (Preview preview : List.copyOf(previews.values())) removePreview(preview);
    }
    private void tell(Player player, String key) { StargateChat.debug(player, i18n.get("tc.stargate.horizon." + key, player)); }
}
