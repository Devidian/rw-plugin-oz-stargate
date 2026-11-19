package de.omegazirkel.risingworld.stargate.discovery;

import java.util.Map;
import java.util.Random;
import java.util.Set;

import de.omegazirkel.risingworld.stargate.discovery.DiscoveryCandidateStore.Chunk;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;

import static de.omegazirkel.risingworld.stargate.sector.SectorAddress.CHUNKS_PER_SECTOR;

/** Sector-wide probe order. A persisted cursor represents all completed probes. */
final class DiscoverySectorScan {
    private static final int PATCH_WIDTH = 4;
    private static final int PATCH_CHUNKS = PATCH_WIDTH * PATCH_WIDTH;
    private static final int QUARTER = CHUNKS_PER_SECTOR / 4;
    static final int FIXED_PROBES = 5 * PATCH_CHUNKS;
    static final int END = FIXED_PROBES + CHUNKS_PER_SECTOR * CHUNKS_PER_SECTOR;

    record Target(SectorAddress sector, int cursor) { }
    record Probe(Chunk chunk, int nextCursor) { }

    private DiscoverySectorScan() { }

    static Target choose(SectorAddress origin, int radius, Set<SectorAddress> unavailable,
            Map<SectorAddress, Integer> progress) {
        Target fixed = null;
        Target fallback = null;
        int fixedDistance = Integer.MAX_VALUE;
        int fallbackDistance = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distance = dx * dx + dz * dz;
                if (distance == 0 || distance > radius * radius) continue;
                SectorAddress sector = new SectorAddress(origin.x() + dx, origin.z() + dz);
                if (unavailable.contains(sector)) continue;
                int cursor = progress.getOrDefault(sector, 0);
                if (cursor < FIXED_PROBES) {
                    if (distance < fixedDistance) {
                        fixed = new Target(sector, cursor);
                        fixedDistance = distance;
                    }
                } else if (cursor < END && (fallback == null || cursor < fallback.cursor()
                        || (cursor == fallback.cursor() && distance < fallbackDistance))) {
                    fallback = new Target(sector, cursor);
                    fallbackDistance = distance;
                }
            }
        }
        return fixed != null ? fixed : fallback;
    }

    static Probe next(SectorAddress sector, int cursor) {
        if (cursor < FIXED_PROBES) {
            int patch = cursor / PATCH_CHUNKS;
            int offset = cursor % PATCH_CHUNKS;
            int centreX = patch == 0 ? 2 * QUARTER : QUARTER + 2 * QUARTER * ((patch - 1) / 2);
            int centreZ = patch == 0 ? 2 * QUARTER : QUARTER + 2 * QUARTER * ((patch - 1) % 2);
            return new Probe(chunk(sector, centreX - 2 + offset / PATCH_WIDTH,
                    centreZ - 2 + offset % PATCH_WIDTH), cursor + 1);
        }

        // An odd stride and reversible bit mixing permute the 16-bit chunk indices.
        // Seeding per sector lets the search resume without storing a shuffled list.
        int mask = CHUNKS_PER_SECTOR * CHUNKS_PER_SECTOR - 1;
        Random order = new Random(((long) sector.x() << 32) ^ (sector.z() & 0xffffffffL));
        int offset = order.nextInt(mask + 1);
        int stride = order.nextInt(mask + 1) | 1;
        while (cursor < END) {
            int index = (offset + stride * (cursor++ - FIXED_PROBES)) & mask;
            index ^= index >>> 7;
            index ^= (index << 5) & mask;
            int x = index / CHUNKS_PER_SECTOR;
            int z = index % CHUNKS_PER_SECTOR;
            if (!inFixedPatch(x, z)) return new Probe(chunk(sector, x, z), cursor);
        }
        return null;
    }

    private static boolean inFixedPatch(int x, int z) {
        return (inPatch(x, 2 * QUARTER) && inPatch(z, 2 * QUARTER))
                || ((inPatch(x, QUARTER) || inPatch(x, 3 * QUARTER))
                && (inPatch(z, QUARTER) || inPatch(z, 3 * QUARTER)));
    }

    private static boolean inPatch(int coordinate, int centre) {
        return coordinate >= centre - PATCH_WIDTH / 2 && coordinate < centre + PATCH_WIDTH / 2;
    }

    private static Chunk chunk(SectorAddress sector, int x, int z) {
        return new Chunk(sector.x() * CHUNKS_PER_SECTOR + x, sector.z() * CHUNKS_PER_SECTOR + z);
    }
}
