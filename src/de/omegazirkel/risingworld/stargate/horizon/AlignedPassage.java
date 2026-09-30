package de.omegazirkel.risingworld.stargate.horizon;

import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;

/** Static model aperture, in world units. Local +Z is the back, -Z is the front. */
public record AlignedPassage(float x, float y, float z, float forwardX, float forwardZ) {
    public static final float CENTRE_HEIGHT = 6.15f;
    public static final float RADIUS = 4.4f;
    public static final float HALF_DEPTH = 0.6f;
    public static final float BODY_CENTRE = 1.8f;

    public AlignedPassage {
        double length = Math.hypot(forwardX, forwardZ);
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Double.isFinite(length) || length < 0.001) throw new IllegalArgumentException("Invalid aligned passage");
        forwardX = (float) (forwardX/length); forwardZ = (float) (forwardZ/length);
    }

    public static AlignedPassage from(GateVisualPlacement placement) {
        return new AlignedPassage(placement.x(), placement.y()+CENTRE_HEIGHT, placement.z(), placement.forwardX(), placement.forwardZ());
    }

    public double depth(float px, float pz) { return ((double)px-x)*forwardX + ((double)pz-z)*forwardZ; }
    private double radialSquared(float px, float py, float pz) {
        double side = ((double)px-x)*forwardZ - ((double)pz-z)*forwardX;
        double height = (double)py + BODY_CENTRE-y;
        return side*side + height*height;
    }
    public boolean contains(float px, float py, float pz) {
        return Math.abs(depth(px,pz)) <= HALF_DEPTH && radialSquared(px,py,pz) <= (double)RADIUS*RADIUS;
    }
    public boolean acceptsArrival(float px, float py, float pz) {
        double depth = depth(px,pz);
        return depth >= -8 && depth <= -2 && radialSquared(px,py,pz) <= (double)RADIUS*RADIUS;
    }
    public HorizonZone zone(String id) {
        float extentX = Math.abs(forwardZ)*RADIUS + Math.abs(forwardX)*HALF_DEPTH;
        float extentZ = Math.abs(forwardX)*RADIUS + Math.abs(forwardZ)*HALF_DEPTH;
        return new HorizonZone(id, x-extentX, y-RADIUS-BODY_CENTRE, z-extentZ,
                x+extentX, y+RADIUS-BODY_CENTRE, z+extentZ, this);
    }
}
