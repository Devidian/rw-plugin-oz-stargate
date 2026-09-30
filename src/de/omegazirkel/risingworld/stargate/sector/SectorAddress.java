package de.omegazirkel.risingworld.stargate.sector;

import net.risingworld.api.utils.Utils.ChunkUtils;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Vector3i;

/** Native horizontal sector address. Rising World groups 256 chunks per sector. */
public record SectorAddress(int x, int z) {
    private static final int CHUNKS_PER_SECTOR = 256;

    public static SectorAddress fromChunk(int chunkX, int chunkZ) {
        return new SectorAddress(Math.floorDiv(chunkX, CHUNKS_PER_SECTOR),
                Math.floorDiv(chunkZ, CHUNKS_PER_SECTOR));
    }

    public static SectorAddress fromWorld(Vector3f position) {
        if (position == null || !Float.isFinite(position.x) || !Float.isFinite(position.z))
            throw new IllegalArgumentException("Invalid gate position");
        Vector3i chunk = ChunkUtils.getChunkPosition(position);
        if (chunk == null) throw new IllegalArgumentException("Cannot determine gate chunk");
        return fromChunk(chunk.x, chunk.z);
    }

    @Override public String toString() { return "(" + x + ", " + z + ")"; }
}
