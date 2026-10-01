package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;

/** Time-based cosmetic pose. Early lighting is cosmetic; it never confirms a network lock. */
record GateDialMotion(boolean drivingRing, float angle, int movingChevron, float depth, boolean strokeLit) {
    static final float SWEEP = 24f;
    static final float TRAVEL = 0.14f;

    static GateDialMotion sample(GateView view, long now) {
        GateAnimationState state = GateAnimationState.from(view);
        if (state.mode() != GateAnimationState.Mode.OUTGOING || state.locks() >= 7 || view.stepStartedNanos() == 0)
            return new GateDialMotion(false, 0, -1, 0, false);
        double elapsedMs = Math.max(0, (now - view.stepStartedNanos()) / 1_000_000d);
        // DHD light/cue, then the full recorded ring stroke, then the chevron stroke.
        double turn = ease((elapsedMs - GateAudioTiming.DHD_MS) / GateAudioTiming.ringMillis(state.locks()));
        float angle = (float) ((state.locks() % 2 == 0 ? turn : 1 - turn) * SWEEP);
        double stroke = (elapsedMs - GateAudioTiming.chevronStartMillis(state.locks()))
                / GateAudioTiming.CHEVRON_OUT_MS;
        double depth = stroke < .55 ? ease(stroke / .55) : 1 - ease((stroke - .68) / .32);
        return new GateDialMotion(true, angle, GateAnimationState.chevron(state.locks()), (float) depth * TRAVEL,
                stroke >= .55);
    }

    private static double ease(double value) {
        double t = Math.max(0, Math.min(1, value));
        // Quintic easing: zero speed and acceleration at both endpoints.
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
}
