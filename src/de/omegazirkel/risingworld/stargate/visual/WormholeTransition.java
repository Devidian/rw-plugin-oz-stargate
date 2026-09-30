package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

/** Per-model visual tail on normal closure; never extends the network's travel window. */
final class WormholeTransition {
    static final long COLLAPSE_NANOS = 600_000_000L;
    private boolean wasOpen;
    private long closingStarted;
    private WormholePose lastOpen;

    WormholePose sample(GateView view, long now) {
        WormholePose current = WormholePose.sample(view, now);
        if (current.visible()) {
            wasOpen = true;
            closingStarted = 0;
            lastOpen = current;
            return current;
        }
        // Loss of readiness or a new attempt cancels any old visual tail immediately.
        if (!view.ready() || !"IDLE".equals(view.state())) {
            wasOpen = false;
            closingStarted = 0;
            return current;
        }
        if (wasOpen) {
            wasOpen = false;
            closingStarted = now;
        }
        if (closingStarted == 0 || lastOpen == null) return current;
        double phase = Math.max(0, (now - closingStarted) / (double) COLLAPSE_NANOS);
        if (phase >= 1) {
            closingStarted = 0;
            return current;
        }
        // Alpha erosion lives in the closing atlas; never shrink the entire disk uniformly.
        return new WormholePose(true, lastOpen.radiusScale(), 0, 0, 0, Math.min(11, (int) (phase * 12)));
    }
}
