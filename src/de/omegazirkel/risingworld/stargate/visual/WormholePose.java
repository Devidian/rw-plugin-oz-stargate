package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

/** Cosmetic projection of an established connection; never participates in travel decisions. */
record WormholePose(boolean visible, float radiusScale, float surgeDepth, float rearDepth, float rearAngle, int dissolveFrame) {
    static final double SURGE_END = 2.65;
    static final float PEAK_DEPTH = 3.78f; // Previous 2.8m plus 35%.
    static final double PEAK_START = .915, PEAK_END = PEAK_START + .4, FRONT_END = 2.05;
    static final double REAR_START = 1.85;

    static WormholePose sample(GateView view, long now) {
        if (!view.ready() || !"OPEN".equals(view.state())) return new WormholePose(false, 1, 0, 0, 0, -1);
        // Unknown age (e.g. restored/legacy view) is settled, not a reason to replay the opening.
        double age = view.openedNanos() == 0 ? SURGE_END : Math.max(0, (now - view.openedNanos()) / 1_000_000_000d);
        double t = Math.min(1, age / .22);
        float scale = (float) Math.max(.001, t * t * (3 - 2 * t));
        float depth = 0;
        if (age > .18 && age < PEAK_START) depth = PEAK_DEPTH * smooth((age - .18) / (PEAK_START - .18));
        else if (age >= PEAK_START && age <= PEAK_END) depth = PEAK_DEPTH;
        else if (age > PEAK_END && age < FRONT_END)
            depth = PEAK_DEPTH * (1 - smooth((age - PEAK_END) / (FRONT_END - PEAK_END)));
        float rear = 0, angle = 0;
        if (age > REAR_START && age < SURGE_END) {
            double phase = (age - REAR_START) / (SURGE_END - REAR_START);
            double wave = Math.sin(Math.PI * phase);
            rear = (float) (.9 * wave * wave);
            angle = (float) (270 * phase);
        }
        return new WormholePose(true, scale, depth, rear, angle, -1);
    }

    private static float smooth(double t) { return (float) (t * t * (3 - 2 * t)); }
}
