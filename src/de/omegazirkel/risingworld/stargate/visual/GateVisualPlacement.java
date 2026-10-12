package de.omegazirkel.risingworld.stargate.visual;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Stored feet position and normalized horizontal forward direction, in world units. */
public record GateVisualPlacement(String gateId, float x, float y, float z, float forwardX, float forwardZ,
        PlatformType platform) {
    /** Gate mesh origin is 1.2 metres below its designated feet point. */
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

    public GateVisualPlacement(String gateId, float x, float y, float z, float forwardX, float forwardZ) {
        this(gateId, x, y, z, forwardX, forwardZ, null);
    }

    /** Runtime origin of the prefab; persisted Y remains the original placement anchor. */
    public float renderedY() {
        return y + (platform == null ? 0f : platform.gateLiftWorld());
    }

    /** Height of the player crossing area; the circular platform has a lowered walkway. */
    public float passageY() {
        return y + (platform == null ? 0f : PlatformType.PASSAGE_LIFT_WORLD);
    }

    public double renderedDistanceSquared(float px, float py, float pz) {
        double dx = (double) x-px, dy = (double) renderedY()-py, dz = (double) z-pz;
        return dx*dx + dy*dy + dz*dz;
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
