package de.omegazirkel.risingworld.stargate.sector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.horizon.AlignedPassage;
import de.omegazirkel.risingworld.stargate.horizon.HorizonService;
import de.omegazirkel.risingworld.stargate.horizon.HorizonStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelPlacement;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelService;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import de.omegazirkel.risingworld.stargate.visual.GateVisualService;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Player;
import net.risingworld.api.World;
import net.risingworld.api.objects.world.Chunk;
import net.risingworld.api.objects.world.Plant;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

/** One command reserves an ID and atomically stores a usable local gate. */
public final class GatePlacementService {
    private static final float ARRIVAL_DISTANCE = 2.4f; // about 1.2 metres

    private final LocalGateStore gates;
    private final LocalSectorStore sectors;
    private final GateVisualStore modelStore;
    private final HorizonStore horizonStore;
    private final GateVisualService models;
    private final HorizonService horizons;
    private final GateNetworkClient network;
    private final I18n i18n;
    private final OZStargate plugin;
    private final DhdModelStore dhdStore;
    private final DhdModelService dhdModels;

    private record DiscoveryCandidate(SectorAddress sector, Vector3f feet, Vector3f arrival,
            Quaternion facing, GateVisualPlacement visual, DhdModelPlacement dhd) { }

    public GatePlacementService(OZStargate plugin, LocalGateStore gates, LocalSectorStore sectors, GateVisualStore modelStore,
            HorizonStore horizonStore, DhdModelStore dhdStore, GateVisualService models, HorizonService horizons,
            DhdModelService dhdModels, GateNetworkClient network, I18n i18n) {
        this.plugin = plugin; this.dhdStore = dhdStore; this.dhdModels = dhdModels;
        this.gates = gates; this.sectors = sectors; this.modelStore = modelStore;
        this.horizonStore = horizonStore; this.models = models; this.horizons = horizons;
        this.network = network; this.i18n = i18n;
    }

    public void create(Player player) {
        if (!player.isAdmin()) { tell(player, "admin_only"); return; }
        Vector3f feet = new Vector3f(player.getPosition());
        Vector3f view = player.getViewDirection();
        double length = Math.hypot(view.x, view.z);
        if (!Double.isFinite(length) || length < 0.001) { tell(player, "direction"); return; }
        float fx = (float) (view.x / length), fz = (float) (view.z / length);
        Vector3f arrival = new Vector3f(feet.x - fx * ARRIVAL_DISTANCE,
                feet.y, feet.z - fz * ARRIVAL_DISTANCE);
        try {
            SectorAddress address = SectorAddress.fromWorld(feet);
            if (!address.equals(SectorAddress.fromWorld(arrival))) { tell(player, "boundary"); return; }
            GateVisualPlacement planned = new GateVisualPlacement("PENDING", feet.x,
                    feet.y - GateVisualPlacement.PLACEMENT_DEPTH, feet.z, fx, fz);
            AlignedPassage passage = AlignedPassage.from(planned);
            if (!passage.acceptsArrival(arrival.x, arrival.y, arrival.z)) {
                tell(player, "ground"); return;
            }
            if (horizonStore.all().stream().anyMatch(zone -> zone.overlaps(passage.zone("PENDING")))) {
                tell(player, "overlap"); return;
            }
            Quaternion facing = new Quaternion().lookAt(-fx, 0f, -fz);
            network.register(player, feet, new Quaternion(player.getRotation()), (gateId, ignored, rotation) -> {
                GateVisualPlacement placement = new GateVisualPlacement(gateId, planned.x(), planned.y(),
                        planned.z(), planned.forwardX(), planned.forwardZ());
                sectors.saveGateWithSetup(gates, gateId, arrival, facing, address, () -> {
                    if (!modelStore.save(placement)) throw new SQLException("Cannot save new gate model");
                    horizonStore.saveNewAligned(placement);
                });
                models.registered(placement);
                try { horizons.registered(); }
                catch (SQLException ex) {
                    OZStargate.logger().error("Cannot refresh new Stargate passage: " + ex.getMessage());
                }
                tell(player, "placed");
            });
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot prepare new Stargate placement: " + ex.getMessage());
            tell(player, "database_error");
        } catch (IllegalArgumentException ex) { tell(player, "ground"); }
    }

    private static boolean clearOfPlants(Plant[] plants, float gateX, float gateZ,
            float dhdX, float dhdZ, float arrivalX, float arrivalZ) {
        if (plants == null) return true;
        for (Plant plant : plants) {
            Vector3f point = plant.getWorldPosition();
            if (Math.hypot(point.x - gateX, point.z - gateZ) < 7f
                    || Math.hypot(point.x - dhdX, point.z - dhdZ) < 5f
                    || Math.hypot(point.x - arrivalX, point.z - arrivalZ) < 4f) return false;
        }
        return true;
    }

    public boolean discoveryCandidate(int chunkX, int chunkZ) throws SQLException {
        return discoveryCandidate(World.getChunk(chunkX, chunkZ)) != null;
    }

    /** Revalidates a cached site before sending the relay registration request. */
    public boolean createDiscovered(Player player, int chunkX, int chunkZ, Consumer<String> created)
            throws SQLException {
        SectorAddress sector = SectorAddress.fromChunk(chunkX, chunkZ);
        if (sectors.gateAt(sector) != null || network.hasPendingRegistration(sector)) return false;
        DiscoveryCandidate candidate = discoveryCandidate(World.getChunk(chunkX, chunkZ));
        if (candidate == null) return false;
        network.register(player, candidate.feet(), candidate.facing(), (id, ignored, rotation) -> {
            try {
                GateVisualPlacement visual = visual(candidate.visual(), id);
                DhdModelPlacement dhd = dhd(candidate.dhd(), id);
                sectors.saveGateWithSetup(gates, id, candidate.arrival(), candidate.facing(),
                        candidate.sector(), () -> {
                            if (!modelStore.save(visual)) throw new SQLException("Cannot save discovered gate model");
                            horizonStore.saveNewAligned(visual);
                            if (!dhdStore.save(dhd)) throw new SQLException("Cannot save discovered DHD");
                        });
                publish(visual, dhd);
                created.accept(id);
            } catch (SQLException ex) { fail(player, ex); throw ex; }
        });
        return true;
    }

    private DiscoveryCandidate discoveryCandidate(Chunk chunk) throws SQLException {
        if (chunk == null || !chunk.isValid() || chunk.containsWater()) return null;
        float[] heights = chunk.getLODTerrain();
        if (heights == null || heights.length != Chunk.SIZE_X * Chunk.SIZE_Z) return null;
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (float height : heights) {
            if (!Float.isFinite(height)) return null;
            min = Math.min(min, height); max = Math.max(max, height);
        }
        if (min <= 90f || max - min > 4f) return null;
        if (chunk.getAllObjects() != null && chunk.getAllObjects().length > 0) return null;
        if (chunk.getAllConstructionElements() != null && chunk.getAllConstructionElements().length > 0) return null;
        int cx = chunk.getChunkPositionX(), cz = chunk.getChunkPositionZ();
        SectorAddress sector = SectorAddress.fromChunk(cx, cz);
        List<int[]> directions = new ArrayList<>(List.of(
                new int[] {1, 0}, new int[] {-1, 0}, new int[] {0, 1}, new int[] {0, -1}));
        Collections.shuffle(directions);
        for (int[] direction : directions) {
            int fx = direction[0], fz = direction[1];
            int gx = 16 + fx * 8, gz = 16 + fz * 8;
            int dx = gx - fx * 16 + fz * 4, dz = gz - fz * 16 - fx * 4;
            float gateX = cx * Chunk.SIZE_X + gx, gateZ = cz * Chunk.SIZE_Z + gz;
            float dhdX = cx * Chunk.SIZE_X + dx, dhdZ = cz * Chunk.SIZE_Z + dz;
            float arrivalX = gateX - fx * ARRIVAL_DISTANCE, arrivalZ = gateZ - fz * ARRIVAL_DISTANCE;
            if (!clearOfPlants(chunk.getAllPlants(), gateX, gateZ, dhdX, dhdZ, arrivalX, arrivalZ)) continue;
            float gateY = chunk.getLODSurfaceLevel(gx, gz, false);
            float dhdY = chunk.getLODSurfaceLevel(dx, dz, false);
            float arrivalY = chunk.getLODSurfaceLevel(Math.round(gx - fx * ARRIVAL_DISTANCE),
                    Math.round(gz - fz * ARRIVAL_DISTANCE), false);
            if (!Float.isFinite(gateY) || !Float.isFinite(dhdY) || !Float.isFinite(arrivalY)) continue;
            GateVisualPlacement visual = new GateVisualPlacement("PENDING", gateX,
                    gateY - GateVisualPlacement.PLACEMENT_DEPTH, gateZ, fx, fz);
            Vector3f arrival = new Vector3f(arrivalX, arrivalY, arrivalZ);
            if (!AlignedPassage.from(visual).acceptsArrival(arrivalX, arrivalY, arrivalZ)
                    || overlaps(visual, null)) continue;
            Vector3f feet = new Vector3f(gateX, gateY, gateZ);
            Quaternion facing = new Quaternion().lookAt(-fx, 0f, -fz);
            DhdModelPlacement dhd = new DhdModelPlacement("PENDING", dhdX, dhdY, dhdZ, fx, fz);
            return new DiscoveryCandidate(sector, feet, arrival, facing, visual, dhd);
        }
        return null;
    }

    public boolean hasGateHere(Player player) {
        try { return sectors.gateAt(SectorAddress.fromWorld(player.getPosition())) != null; }
        catch (SQLException | IllegalArgumentException ex) { return false; }
    }

    /** Relay acknowledgement deletes the local gate; SQLite triggers remove its DHD and visual records. */
    public void removeGate(Player player, String expectedId) {
        if (!player.isAdmin()) { tell(player, "admin_only"); return; }
        try {
            String current = sectors.gateAt(SectorAddress.fromWorld(player.getPosition()));
            if (current == null || !current.equals(expectedId)) { tell(player, "changed"); return; }
            if (!network.isIdleForSetup(current) || sectors.hasActiveTransfer(current)) {
                tell(player, "busy"); return;
            }
            network.unregister(player, current);
        } catch (SQLException ex) { fail(player, ex);
        } catch (IllegalArgumentException ex) { tell(player, "ground"); }
    }

    public String gateHere(Player player) {
        try { return sectors.gateAt(SectorAddress.fromWorld(player.getPosition())); }
        catch (SQLException | IllegalArgumentException ex) { return null; }
    }

    /** Radial placement reuses the sector's existing relay ID when present. */
    public void placeGate(Player player, boolean withDhd) {
        if (!player.isAdmin()) { tell(player, "admin_only"); return; }
        try {
            Vector3f feet = new Vector3f(player.getPosition());
            Vector3f view = player.getViewDirection();
            double length = Math.hypot(view.x, view.z);
            if (!Double.isFinite(length) || length < .001) { tell(player, "direction"); return; }
            float fx = (float) (view.x / length), fz = (float) (view.z / length);
            // In the combined action, the aimed DHD is on the gate's front side.
            float gateFx = withDhd ? -fx : fx, gateFz = withDhd ? -fz : fz;
            SectorAddress sector = SectorAddress.fromWorld(feet);
            Vector3f arrival = new Vector3f(feet.x - gateFx * ARRIVAL_DISTANCE,
                    feet.y, feet.z - gateFz * ARRIVAL_DISTANCE);
            if (!sector.equals(SectorAddress.fromWorld(arrival))) { tell(player, "boundary"); return; }
            GateVisualPlacement planned = new GateVisualPlacement("PENDING", feet.x,
                    feet.y - GateVisualPlacement.PLACEMENT_DEPTH, feet.z, gateFx, gateFz);
            if (!AlignedPassage.from(planned).acceptsArrival(arrival.x, arrival.y, arrival.z)) {
                tell(player, "ground"); return;
            }
            String existing = sectors.gateAt(sector);
            if (existing != null && !network.isIdleForSetup(existing)) { tell(player, "busy"); return; }
            if (overlaps(planned, existing)) { tell(player, "overlap"); return; }
            Quaternion facing = new Quaternion().lookAt(-gateFx, 0f, -gateFz);
            if (withDhd) {
                dhdModels.aimPlacement(player, "PENDING", dhd -> {
                    if (!sector.equals(SectorAddress.fromWorld(new Vector3f(dhd.x(), dhd.y(), dhd.z())))) {
                        tell(player, "dhd_boundary"); return;
                    }
                    if (Math.hypot(dhd.x() - planned.x(), dhd.z() - planned.z()) < 6f) {
                        tell(player, "dhd_too_close"); return;
                    }
                    DhdModelPlacement facingAwayFromGate = new DhdModelPlacement(dhd.gateId(),
                            dhd.x(), dhd.y(), dhd.z(), -dhd.forwardX(), -dhd.forwardZ());
                    savePlacement(player, sector, feet, arrival, facing, planned, existing, facingAwayFromGate);
                });
            } else savePlacement(player, sector, feet, arrival, facing, planned, existing, null);
        } catch (SQLException ex) { fail(player, ex);
        } catch (IllegalArgumentException ex) { tell(player, "ground"); }
    }

    public void placeDhd(Player player) {
        if (!player.isAdmin()) { tell(player, "admin_only"); return; }
        try {
            SectorAddress sector = SectorAddress.fromWorld(player.getPosition());
            String id = sectors.gateAt(sector);
            if (id == null) { tell(player, "missing_gate"); return; }
            if (!network.isIdleForSetup(id)) { tell(player, "busy"); return; }
            LocalGateStore.Gate gate = gates.gate(id);
            if (gate == null || Math.hypot(player.getPosition().x - gate.position().x,
                    player.getPosition().z - gate.position().z) > 32f) {
                tell(player, "near_gate"); return;
            }
            dhdModels.aimPlacement(player, id, dhd -> {
                try {
                    if (!valid(player, sector) || !id.equals(sectors.gateAt(sector))
                            || !network.isIdleForSetup(id)) {
                        tell(player, "busy"); return;
                    }
                    LocalGateStore.Gate current = gates.gate(id);
                    if (current == null || dhd.distanceSquared(current.position().x,
                            current.position().y, current.position().z) > 32d * 32d
                            || Math.hypot(player.getPosition().x - current.position().x,
                                    player.getPosition().z - current.position().z) > 32f) {
                        tell(player, "near_gate"); return;
                    }
                    if (!dhdStore.save(dhd)) { tell(player, "missing_gate"); return; }
                    dhdModels.registered(dhd);
                    tell(player, "dhd_placed");
                } catch (SQLException ex) { fail(player, ex); }
            });
        } catch (SQLException ex) { fail(player, ex);
        } catch (IllegalArgumentException ex) { tell(player, "ground"); }
    }

    private void savePlacement(Player player, SectorAddress sector, Vector3f feet, Vector3f arrival,
            Quaternion facing, GateVisualPlacement planned, String existing, DhdModelPlacement dhd) {
        try {
            if (!valid(player, sector) || overlaps(planned, existing)) { tell(player, "changed"); return; }
            if (existing == null) {
                if (sectors.gateAt(sector) != null) { tell(player, "occupied"); return; }
                network.register(player, feet, new Quaternion(player.getRotation()), (id, ignored, rotation) -> {
                    GateVisualPlacement visual = visual(planned, id);
                    sectors.saveGateWithSetup(gates, id, arrival, facing, sector, () -> {
                        if (!modelStore.save(visual)) throw new SQLException("Cannot save gate model");
                        horizonStore.saveNewAligned(visual);
                        if (dhd != null && !dhdStore.save(dhd(dhd, id))) throw new SQLException("Cannot save DHD");
                    });
                    publish(visual, dhd == null ? null : dhd(dhd, id));
                    tell(player, dhd == null ? "placed" : "placed_with_dhd");
                });
                return;
            }
            if (!existing.equals(sectors.gateAt(sector)) || !network.isIdleForSetup(existing)) {
                tell(player, "busy"); return;
            }
            GateVisualPlacement visual = visual(planned, existing);
            DhdModelPlacement actualDhd = dhd == null ? null : dhd(dhd, existing);
            boolean moved = sectors.moveGateWithSetup(gates, existing, arrival, facing, sector, () -> {
                if (!modelStore.save(visual)) throw new SQLException("Cannot save gate model");
                horizonStore.replaceAligned(visual);
                if (actualDhd != null && !dhdStore.save(actualDhd)) throw new SQLException("Cannot save DHD");
            });
            if (!moved) { tell(player, "busy"); return; }
            publish(visual, actualDhd);
            tell(player, actualDhd == null ? "moved" : "moved_with_dhd");
        } catch (SQLException ex) { fail(player, ex); }
    }

    private boolean overlaps(GateVisualPlacement planned, String existing) throws SQLException {
        return horizonStore.all().stream().filter(zone -> !zone.gateId().equals(existing))
                .anyMatch(zone -> zone.overlaps(AlignedPassage.from(planned).zone("PENDING")));
    }

    private void publish(GateVisualPlacement visual, DhdModelPlacement dhd) {
        models.registered(visual);
        if (dhd != null) dhdModels.registered(dhd);
        try { horizons.registered(); }
        catch (SQLException ex) { OZStargate.logger().error("Cannot refresh passage: " + ex.getMessage()); }
    }

    private boolean valid(Player player, SectorAddress sector) {
        return player.isConnected() && player.isSpawned() && player.isAdmin()
                && sector.equals(SectorAddress.fromWorld(player.getPosition()));
    }

    private static GateVisualPlacement visual(GateVisualPlacement p, String id) {
        return new GateVisualPlacement(id, p.x(), p.y(), p.z(), p.forwardX(), p.forwardZ());
    }
    private static DhdModelPlacement dhd(DhdModelPlacement p, String id) {
        return new DhdModelPlacement(id, p.x(), p.y(), p.z(), p.forwardX(), p.forwardZ());
    }
    private void fail(Player player, SQLException ex) {
        OZStargate.logger().error("Cannot place Stargate: " + ex.getMessage());
        tell(player, "database_error");
    }

    private void tell(Player player, String key) {
        if (player != null && player.isConnected())
            StargateChat.debug(player, i18n.get("tc.stargate.sector.placement_" + key, player));
    }
}
