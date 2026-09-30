package de.omegazirkel.risingworld.stargate.visual;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Stored feet position and normalized horizontal forward direction, in world units. */
public record GateVisualPlacement(String gateId, float x, float y, float z, float forwardX, float forwardZ) {
    /** 1.2 metres below the admin's feet; Rising World uses two units per metre. */
    public static final float PLACEMENT_DEPTH = 2.4f;
    public static final float SHOW_DISTANCE = 128f;
    public static final float HIDE_DISTANCE = 160f;
    public static final int MAX_VISIBLE = 16;

    public GateVisualPlacement {
        double length = Math.hypot(forwardX, forwardZ);
        if (gateId == null || gateId.isBlank() || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(z) || !Double.isFinite(length) || length < 0.001) {
            throw new IllegalArgumentException("Invalid gate visual transform");
        }
        forwardX = (float) (forwardX / length);
        forwardZ = (float) (forwardZ / length);
    }

    public double distanceSquared(float px, float py, float pz) {
        double dx = (double) x-px, dy = (double) y-py, dz = (double) z-pz;
        return dx*dx + dy*dy + dz*dz;
    }

    /** Stable nearest-first cap, with hysteresis for models already visible to this player. */
    public static List<GateVisualPlacement> nearby(Collection<GateVisualPlacement> placements,
            Set<String> visible, float x, float y, float z) {
        return placements.stream().filter(p -> {
            double radius = visible.contains(p.gateId()) ? HIDE_DISTANCE : SHOW_DISTANCE;
            return p.distanceSquared(x, y, z) <= radius*radius;
        }).sorted(Comparator.comparingDouble((GateVisualPlacement p) -> p.distanceSquared(x, y, z))
                .thenComparing(GateVisualPlacement::gateId)).limit(MAX_VISIBLE).toList();
    }
}
