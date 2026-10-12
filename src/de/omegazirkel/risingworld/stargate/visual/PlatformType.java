package de.omegazirkel.risingworld.stargate.visual;

/** Authored Accessory Pack platform aligned to the Milky Way gate origin. */
public enum PlatformType {
    // The circular gate base rests on the .444 m recess floor; the side of the
    // ring then meets the raised deck while the lowest two Chevrons stay hidden.
    CIRCULAR("circular", .444f, 2.4f, 1.10f),
    CURVED_MILKY_WAY("curved_milky_way", 1.333f, .4f, 1.333f),
    CURVED_PEGASUS("curved_pegasus", 1.333f, .4f, 1.333f),
    // Its sloped ring supports meet the gate when the mesh origin is .43 m up.
    GRACE_OF_GOD("grace_of_god", .44f, 2.38f, 1.60f);

    /** The walking trigger stays at the recessed deck even when the circular ring is raised. */
    public static final float PASSAGE_LIFT_WORLD = .4f;

    private final String key;
    private final float deckHeightMetres;
    private final float gateLiftWorld;
    private final float arrivalHeightMetres;

    PlatformType(String key, float deckHeightMetres, float gateLiftWorld, float arrivalHeightMetres) {
        this.key = key;
        this.deckHeightMetres = deckHeightMetres;
        this.gateLiftWorld = gateLiftWorld;
        this.arrivalHeightMetres = arrivalHeightMetres;
    }

    public String key() { return key; }
    public float deckHeightWorld() { return deckHeightMetres * 2f; }
    public float gateLiftWorld() { return gateLiftWorld; }
    /** Walkable height at the arrival point, 1.2 m in front of the aperture. */
    public float arrivalHeightWorld() { return arrivalHeightMetres * 2f; }

    public static PlatformType fromKey(String key) {
        if (key == null) return null;
        for (PlatformType type : values()) if (type.key.equals(key)) return type;
        return null;
    }
}
