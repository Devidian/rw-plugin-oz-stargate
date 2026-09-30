package de.omegazirkel.risingworld.stargate.visual;

/** Overlapping tangent boxes around the ring; none reaches into the clear aperture. */
final class GateCollisionGeometry {
    static final int SEGMENTS = 48;
    static final float INNER = 2.30f, OUTER = 3.075f, CENTRE_Y = 3.075f, HALF_DEPTH = .24f;
    record Segment(float x, float y, float angle, float width, float height, float depth) { }
    private GateCollisionGeometry() { }

    static Segment segment(int index) {
        double angle = index * Math.PI * 2 / SEGMENTS;
        float centre = (INNER + OUTER) / 2;
        // Width at the outer edge keeps adjacent boxes overlapping across the entire thickness.
        float width = (float) (2 * OUTER * Math.tan(Math.PI / SEGMENTS) + .01);
        return new Segment((float) Math.cos(angle) * centre, CENTRE_Y + (float) Math.sin(angle) * centre,
                (float) Math.toDegrees(angle) - 90, width, OUTER - INNER, HALF_DEPTH * 2);
    }
}
