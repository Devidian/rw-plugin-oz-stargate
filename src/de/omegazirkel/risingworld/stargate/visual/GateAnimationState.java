package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

/** Cosmetic projection only: never establishes a connection or authorizes travel. */
record GateAnimationState(Mode mode, int locks) {
    enum Mode { IDLE, OUTGOING, INCOMING, OPEN }
    private static final int[] ORDER = {1, 2, 3, 6, 7, 8, 0};

    static GateAnimationState from(GateView view) {
        if (!view.ready()) return new GateAnimationState(Mode.IDLE, 0);
        if ("OPEN".equals(view.state())) return new GateAnimationState(Mode.OPEN, 7);
        if ("INCOMING".equals(view.state()))
            return new GateAnimationState(Mode.INCOMING, 7);
        if ("OUTGOING".equals(view.state()))
            return new GateAnimationState(Mode.OUTGOING, Math.max(0, Math.min(7, view.chevrons())));
        return new GateAnimationState(Mode.IDLE, 0);
    }

    boolean lit(int index) {
        for (int i = 0; i < locks; i++) if (ORDER[i] == index) return true;
        return false;
    }

    static int chevron(int step) { return ORDER[step]; }
}
