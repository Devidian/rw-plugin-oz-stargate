package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.SQLException;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import net.risingworld.api.Server;
import net.risingworld.api.utils.Utils.ChunkUtils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;

/** Bounded, restart-safe placement of the first gate in the default spawn sector. */
public final class InitialGateService implements AutoCloseable {
    private static final int CHUNKS_PER_BATCH = 25;
    private static final int MAX_RING_POSITIONS = 257 * 257 + CHUNKS_PER_BATCH;
    private final OZStargate plugin;
    private final PluginSettings settings;
    private final InitialGateStore store;
    private final LocalGateStore gates;
    private final GateNetworkClient network;
    private final GatePlacementService placement;
    private SectorAddress sector;
    private int nextChunk;
    private int checkedInBatch;
    private boolean awaiting;
    private boolean closed;
    private boolean reportedNoSite;

    public InitialGateService(OZStargate plugin, PluginSettings settings, InitialGateStore store,
            LocalGateStore gates, GateNetworkClient network, GatePlacementService placement) {
        this.plugin = plugin; this.settings = settings; this.store = store;
        this.gates = gates; this.network = network; this.placement = placement;
    }

    public void start() { plugin.executeDelayed(1f, this::tick); }

    private void tick() {
        if (closed) return;
        try {
            if (!store.pending()) return;
            if (!gates.ids().isEmpty()) {
                store.complete();
                return;
            }
            if (awaiting) return;
            if (settings.networkEnabled && !network.isReady()) {
                schedule(5f); return;
            }
            Vector3f spawn = Server.getDefaultSpawnPosition();
            if (spawn == null) { schedule(30f); return; }
            SectorAddress current = SectorAddress.fromWorld(spawn);
            if (!current.equals(sector)) { sector = current; nextChunk = 0; checkedInBatch = 0; }
            if (nextChunk >= MAX_RING_POSITIONS) {
                if (!reportedNoSite) {
                    OZStargate.logger().warn("No suitable initial Stargate chunk in spawn sector " + sector);
                    reportedNoSite = true;
                }
                nextChunk = 0;
                checkedInBatch = 0;
                schedule(300f);
                return;
            }
            if (checkedInBatch >= CHUNKS_PER_BATCH) {
                checkedInBatch = 0;
                schedule(5f);
                return;
            }
            int index = nextChunk++;
            int[] offset = DiscoveryPoolService.ringOffset(
                    index < CHUNKS_PER_BATCH ? index : index - CHUNKS_PER_BATCH);
            checkedInBatch++;
            Vector3i spawnChunk = ChunkUtils.getChunkPosition(spawn);
            if (spawnChunk == null) { schedule(30f); return; }
            int x = index >= CHUNKS_PER_BATCH && index < 2 * CHUNKS_PER_BATCH
                    ? spawnChunk.x + offset[0] : sector.x() * 256 + 128 + offset[0];
            int z = index >= CHUNKS_PER_BATCH && index < 2 * CHUNKS_PER_BATCH
                    ? spawnChunk.z + offset[1] : sector.z() * 256 + 128 + offset[1];
            if (!sector.equals(SectorAddress.fromChunk(x, z))) { schedule(.25f); return; }
            if (placement.discoveryCandidate(x, z)) {
                awaiting = true;
                if (placement.createDiscovered(null, x, z, id -> {
                    if (closed) return;
                    try {
                        store.complete();
                        OZStargate.logger().info("Created initial Stargate " + id + " in spawn sector " + sector);
                    } catch (SQLException ex) {
                        OZStargate.logger().error("Cannot finish initial Stargate marker: " + ex.getMessage());
                        schedule(1f);
                    }
                    awaiting = false;
                })) {
                    plugin.executeDelayed(20f, () -> {
                        if (closed || !awaiting) return;
                        awaiting = false;
                        schedule(1f);
                    });
                    return;
                }
                awaiting = false;
            }
            schedule(.25f);
        } catch (SQLException | RuntimeException ex) {
            OZStargate.logger().error("Initial Stargate placement failed: " + ex.getMessage());
            awaiting = false;
            schedule(30f);
        }
    }

    private void schedule(float seconds) {
        if (!closed) plugin.executeDelayed(seconds, this::tick);
    }

    @Override public void close() { closed = true; }
}
