package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import net.risingworld.api.Server;

/** Fills persisted candidate pools one chunk at a time on the server thread. */
public final class DiscoveryPoolService implements AutoCloseable {
    private static final int SECTORS_PER_GATE = 5;
    private static final int CHUNKS_PER_SECTOR = 5;
    private final OZStargate plugin;
    private final PluginSettings settings;
    private final LocalGateStore gates;
    private final LocalSectorStore sectors;
    private final DiscoveryCandidateStore store;
    private final GatePlacementService placement;
    private Runnable availabilityObserver = () -> { };
    private int cursor;
    private boolean closed;

    public DiscoveryPoolService(OZStargate plugin, PluginSettings settings, LocalGateStore gates,
            LocalSectorStore sectors, DiscoveryCandidateStore store, GatePlacementService placement) {
        this.plugin = plugin; this.settings = settings; this.gates = gates;
        this.sectors = sectors; this.store = store; this.placement = placement;
    }

    public void setAvailabilityObserver(Runnable observer) { availabilityObserver = observer; }

    public boolean hasAvailable(String sourceGateId) throws SQLException {
        int maxSector = Server.getMaxSector();
        SectorAddress origin = sectors.addressOf(sourceGateId);
        return origin != null && origin.isWithinLimit(maxSector)
                && store.hasAvailable(sourceGateId, settings.discoveryRadiusSectors, maxSector);
    }

    public void start() { plugin.executeDelayed(2f, this::tick); }

    public List<SectorAddress> sectors(String sourceGateId) throws SQLException {
        if (store.prune(sourceGateId, settings.discoveryRadiusSectors)) availabilityObserver.run();
        int maxSector = Server.getMaxSector();
        return store.sectors(sourceGateId).stream().filter(sector -> sector.isWithinLimit(maxSector)).toList();
    }

    public List<DiscoveryCandidateStore.Chunk> chunks(String sourceGateId, SectorAddress sector)
            throws SQLException {
        return sector.isAccessible() ? store.chunks(sourceGateId, sector) : List.of();
    }

    public void removeChunk(DiscoveryCandidateStore.Chunk chunk) throws SQLException {
        store.removeChunk(chunk);
        availabilityObserver.run();
    }

    public void gateCreated(SectorAddress sector) {
        try { store.removeSector(sector); }
        catch (SQLException ex) { error(ex); }
        availabilityObserver.run();
    }

    private void tick() {
        if (closed) return;
        try {
            List<String> ids = gates.ids();
            if (!ids.isEmpty()) {
                String source = ids.get(Math.floorMod(cursor++, ids.size()));
                fillOne(source);
            }
        } catch (SQLException ex) { error(ex);
        } catch (RuntimeException ex) {
            OZStargate.logger().warn("Discovery pool scan failed: " + ex.getMessage());
        }
        plugin.executeDelayed(.25f, this::tick);
    }

    private void fillOne(String source) throws SQLException {
        SectorAddress origin = sectors.addressOf(source);
        int maxSector = Server.getMaxSector();
        if (origin == null || !origin.isWithinLimit(maxSector)) return;
        int radius = settings.discoveryRadiusSectors;
        if (store.prune(source, radius)) availabilityObserver.run();
        replenishKnown(source, origin, radius, maxSector);
        List<SectorAddress> known = store.sectors(source).stream()
                .filter(sector -> sector.isWithinLimit(maxSector)).toList();
        if (known.size() >= SECTORS_PER_GATE) return;

        Set<SectorAddress> unavailable = new HashSet<>(known);
        for (LocalSectorStore.Entry entry : sectors.all()) unavailable.add(entry.address());
        DiscoverySectorScan.Target target = DiscoverySectorScan.choose(origin, radius, unavailable,
                store.progress(origin, radius), maxSector);
        if (target == null) return;
        DiscoverySectorScan.Probe probe = DiscoverySectorScan.next(target.sector(), target.cursor());
        if (probe == null) {
            store.recordProbe(target.sector(), DiscoverySectorScan.END, null);
            return;
        }
        var chunk = probe.chunk();
        boolean suitable = placement.discoveryCandidate(chunk.x(), chunk.z());
        store.recordProbe(target.sector(), probe.nextCursor(), suitable ? chunk : null);
        if (suitable) replenishKnown(source, origin, radius, maxSector);
    }

    private void replenishKnown(String source, SectorAddress origin, int radius, int maxSector) throws SQLException {
        boolean wasAvailable = store.hasAvailable(source, radius, maxSector);
        store.copyKnown(source, origin, radius, SECTORS_PER_GATE, CHUNKS_PER_SECTOR, maxSector);
        if (!wasAvailable && store.hasAvailable(source, radius, maxSector)) availabilityObserver.run();
    }

    /** Square rings around sector center, with the centre itself as index zero. */
    static int[] ringOffset(int index) {
        if (index == 0) return new int[] {0, 0};
        int ring = 1;
        while (index >= (2 * ring + 1) * (2 * ring + 1)) ring++;
        int previous = (2 * ring - 1) * (2 * ring - 1);
        int step = index - previous;
        int side = 2 * ring;
        if (step < side) return new int[] {-ring + step, -ring};
        step -= side;
        if (step < side) return new int[] {ring, -ring + step};
        step -= side;
        if (step < side) return new int[] {ring - step, ring};
        step -= side;
        return new int[] {-ring, ring - step};
    }

    private static void error(SQLException ex) {
        OZStargate.logger().error("Stargate discovery pool database failure: " + ex.getMessage());
    }

    @Override public void close() {
        closed = true;
    }
}
