package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;

/** Fills persisted candidate pools one chunk at a time on the server thread. */
public final class DiscoveryPoolService implements AutoCloseable {
    private static final int SECTORS_PER_GATE = 5;
    private static final int CHUNKS_PER_SECTOR = 5;
    private static final int MAX_SCAN_CHUNKS = 25;
    private static final long RETRY_EXHAUSTED_AFTER_MS = 5 * 60_000L;
    private final OZStargate plugin;
    private final PluginSettings settings;
    private final LocalGateStore gates;
    private final LocalSectorStore sectors;
    private final DiscoveryCandidateStore store;
    private final GatePlacementService placement;
    private final Map<String, Scan> scans = new HashMap<>();
    private final Map<String, Set<SectorAddress>> exhausted = new HashMap<>();
    private final Map<String, Long> retryExhaustedAt = new HashMap<>();
    private int cursor;
    private boolean closed;

    private static final class Scan {
        final SectorAddress target;
        final int radius;
        int index, found;
        Scan(SectorAddress target, int radius) { this.target = target; this.radius = radius; }
    }

    public DiscoveryPoolService(OZStargate plugin, PluginSettings settings, LocalGateStore gates,
            LocalSectorStore sectors, DiscoveryCandidateStore store, GatePlacementService placement) {
        this.plugin = plugin; this.settings = settings; this.gates = gates;
        this.sectors = sectors; this.store = store; this.placement = placement;
    }

    public void start() { plugin.executeDelayed(2f, this::tick); }

    public List<SectorAddress> sectors(String sourceGateId) throws SQLException {
        store.prune(sourceGateId, settings.discoveryRadiusSectors);
        return store.sectors(sourceGateId);
    }

    public List<DiscoveryCandidateStore.Chunk> chunks(String sourceGateId, SectorAddress sector)
            throws SQLException {
        return store.chunks(sourceGateId, sector);
    }

    public void removeChunk(DiscoveryCandidateStore.Chunk chunk) throws SQLException {
        store.removeChunk(chunk);
    }

    public void gateCreated(SectorAddress sector) {
        try {
            store.removeSector(sector);
            for (String source : gates.ids()) replenishKnown(source);
        }
        catch (SQLException ex) { error(ex); }
        scans.entrySet().removeIf(entry -> entry.getValue().target.equals(sector));
        exhausted.clear();
        retryExhaustedAt.clear();
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
        if (origin == null) return;
        int radius = settings.discoveryRadiusSectors;
        replenishKnown(source);
        if (store.sectors(source).size() >= SECTORS_PER_GATE) {
            scans.remove(source);
            return;
        }
        Scan scan = scans.get(source);
        if (scan != null && scan.radius != radius) { scans.remove(source); scan = null; }
        if (scan == null) {
            List<SectorAddress> known = store.sectors(source);
            Set<SectorAddress> occupied = new HashSet<>();
            for (LocalSectorStore.Entry entry : sectors.all()) occupied.add(entry.address());
            Set<SectorAddress> failed = exhausted.computeIfAbsent(source, ignored -> new HashSet<>());
            SectorAddress target = randomSector(origin, radius, known,
                    failed, occupied);
            if (target == null && !failed.isEmpty()
                    && System.currentTimeMillis() >= retryExhaustedAt.getOrDefault(source, 0L)) {
                failed.clear();
                retryExhaustedAt.put(source, System.currentTimeMillis() + RETRY_EXHAUSTED_AFTER_MS);
                target = randomSector(origin, radius, known, failed, occupied);
            }
            if (target == null) return;
            scan = new Scan(target, radius);
            scans.put(source, scan);
        }
        int[] offset = ringOffset(scan.index++);
        int cx = scan.target.x() * 256 + 128 + offset[0];
        int cz = scan.target.z() * 256 + 128 + offset[1];
        if (sectors.gateAt(scan.target) != null) {
            scans.remove(source); store.removeSector(scan.target); return;
        }
        if (placement.discoveryCandidate(cx, cz)) {
            store.add(source, radius, scan.target, new DiscoveryCandidateStore.Chunk(cx, cz));
            scan.found++;
        }
        if (scan.found >= CHUNKS_PER_SECTOR || scan.index >= MAX_SCAN_CHUNKS) {
            scans.remove(source);
            if (scan.found == 0) exhausted.computeIfAbsent(source, ignored -> new HashSet<>()).add(scan.target);
        }
    }

    private void replenishKnown(String source) throws SQLException {
        SectorAddress origin = sectors.addressOf(source);
        if (origin == null) return;
        int radius = settings.discoveryRadiusSectors;
        store.prune(source, radius);
        store.copyKnown(source, origin, radius, SECTORS_PER_GATE, CHUNKS_PER_SECTOR);
    }

    static SectorAddress randomSector(SectorAddress origin, int radius,
            List<SectorAddress> known, Set<SectorAddress> failed, Set<SectorAddress> occupied) {
        List<SectorAddress> near = new ArrayList<>();
        List<SectorAddress> possible = new ArrayList<>();
        int nearRadius = Math.min(3, radius);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if ((dx == 0 && dz == 0) || dx * dx + dz * dz > radius * radius) continue;
            SectorAddress sector = new SectorAddress(origin.x() + dx, origin.z() + dz);
            if (!known.contains(sector) && !failed.contains(sector) && !occupied.contains(sector)) {
                possible.add(sector);
                if (dx * dx + dz * dz <= nearRadius * nearRadius) near.add(sector);
            }
        }
        List<SectorAddress> choices = near.isEmpty() ? possible : near;
        return choices.isEmpty() ? null : choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
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
        closed = true; scans.clear(); exhausted.clear(); retryExhaustedAt.clear();
    }
}
