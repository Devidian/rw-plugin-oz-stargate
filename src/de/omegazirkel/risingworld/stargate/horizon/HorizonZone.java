package de.omegazirkel.risingworld.stargate.horizon;

/** World bounds for broad-phase overlap, optionally with an exact rotated circular aperture. */
public record HorizonZone(String gateId, float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ, AlignedPassage aligned) {
    public HorizonZone(String gateId, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this(gateId, minX, minY, minZ, maxX, maxY, maxZ, null);
    }
    public HorizonZone {
        if (gateId == null || gateId.isBlank()
                || !Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)
                || minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("Invalid horizon bounds");
        }
    }

    public boolean contains(float x, float y, float z) {
        if (aligned != null) return aligned.contains(x, y, z);
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(HorizonZone other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY
                && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
