package de.omegazirkel.risingworld.stargate.dhd;

/** Persistent position and horizontal facing for one console per local gate. */
public record DhdModelPlacement(String gateId, float x, float y, float z, float forwardX, float forwardZ) {
    public DhdModelPlacement {
        double length = Math.hypot(forwardX, forwardZ);
        if (gateId == null || gateId.isBlank() || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(z) || !Double.isFinite(length) || length < .001) {
            throw new IllegalArgumentException("Invalid DHD model placement");
        }
        forwardX = (float) (forwardX / length);
        forwardZ = (float) (forwardZ / length);
    }

    public double distanceSquared(float px, float py, float pz) {
        double dx = x - (double) px, dy = y - (double) py, dz = z - (double) pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
