package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

public class GateAnimationStateTest {
    private GateAnimationState state(String state, String direction, int step, boolean ready) {
        return GateAnimationState.from(new GateView(state, direction, "peer", step, ready));
    }

    @Test public void sevenConfirmedStepsExcludeBottomPairAndLockTopLast() {
        for (int step = 0; step <= 7; step++) {
            GateAnimationState s = state("OUTGOING", "OUTGOING", step, true);
            int lit = 0;
            for (int i = 0; i < 9; i++) if (s.lit(i)) lit++;
            assertEquals(step, lit);
            assertFalse(s.lit(4)); assertFalse(s.lit(5));
            assertEquals(step == 7, s.lit(0));
            assertEquals(GateAnimationState.Mode.OUTGOING, s.mode());
        }
    }

    @Test public void preemptionIgnoresOldOutgoingProgressAndStopsRing() {
        GateAnimationState incoming = state("INCOMING", "INCOMING", 3, true);
        assertEquals(7, incoming.locks());
        assertEquals(incoming, state("INCOMING", "INCOMING", 0, true));
    }

    @Test public void abortClosureOfflineAndUnknownStateAlwaysDark() {
        for (String s : new String[]{"IDLE", "UNKNOWN"}) {
            assertEquals(0, state(s, "INCOMING", 7, true).locks());
        }
        for (String s : new String[]{"OUTGOING", "INCOMING", "OPEN"}) {
            assertEquals(GateAnimationState.Mode.IDLE, state(s, "OUTGOING", 7, false).mode());
            assertEquals(0, state(s, "OUTGOING", 7, false).locks());
        }
    }

    @Test public void lateViewerGetsCurrentOpenStateWithoutProgressHistory() {
        GateAnimationState open = state("OPEN", "INCOMING", 0, true);
        assertEquals(GateAnimationState.Mode.OPEN, open.mode());
        assertEquals(7, open.locks());
        assertEquals(open, state("OPEN", "OUTGOING", 7, true));
        assertEquals(0, state("OUTGOING", "OUTGOING", -2, true).locks());
        assertEquals(7, state("OUTGOING", "OUTGOING", 99, true).locks());
    }
}
