package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

/** Time-based cosmetic pose. Early lighting is cosmetic; it never confirms a network lock. */
record GateDialMotion(boolean drivingRing, float angle, int movingChevron, float depth, boolean strokeLit) {
    static final float SWEEP = 24f;
    static final float TRAVEL = 0.14f;

    static GateDialMotion sample(GateView view, long now) {
        GateAnimationState state = GateAnimationState.from(view);
        if (state.mode() != GateAnimationState.Mode.OUTGOING || state.locks() >= 7 || view.stepStartedNanos() == 0)
            return new GateDialMotion(false, 0, -1, 0, false);
        double phase = Math.max(0, Math.min(1, (now - view.stepStartedNanos()) / (view.stepMillis() * 1_000_000d)));
        double turn = ease(phase / 0.62);
        float angle = (float) ((state.locks() % 2 == 0 ? turn : 1 - turn) * SWEEP);
        double depth = phase < 0.80 ? ease((phase - 0.68) / 0.12) : 1 - ease((phase - 0.82) / 0.12);
        return new GateDialMotion(true, angle, GateAnimationState.chevron(state.locks()), (float) depth * TRAVEL, phase >= .80);
    }

    private static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        // Quintic easing: zero speed and acceleration at both endpoints.
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
