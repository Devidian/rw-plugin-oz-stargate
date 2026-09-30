package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

public class GateDialMotionTest {
    private static final long START = 1_000_000_000L;
    private GateView view(int locks) { return new GateView("OUTGOING", "OUTGOING", "peer", locks, true, 5000, START); }
    private GateDialMotion pose(int locks, double fraction) {
        return GateDialMotion.sample(view(locks), START + (long) (fraction * 5_000_000_000L));
    }

    @Test public void ringAcceleratesBrakesAndStopsBeforeVStroke() {
        float slowStart = pose(0, .05).angle() - pose(0, 0).angle();
        float middle = pose(0, .30).angle() - pose(0, .25).angle();
        float slowEnd = pose(0, .62).angle() - pose(0, .57).angle();
        assertTrue(slowStart < middle / 5); assertTrue(slowEnd < middle / 5);
        assertEquals(24f, pose(0, .62).angle(), 0.00001f);
        for (double t : new double[]{.65,.74,.80,.88,.94,1,2}) {
            assertEquals(24f, pose(0, t).angle(), 0.00001f);
        }
        assertEquals(0f, pose(0, .65).depth(), 0f);
    }

    @Test public void vLightsAtInnerStopBeforeReturningHome() {
        assertFalse(pose(0, .79).strokeLit());
        assertTrue(pose(0, .80).strokeLit());
        assertTrue(pose(0, .88).strokeLit());
        assertTrue(pose(0, 1.5).strokeLit());
        assertFalse(pose(1, 0).strokeLit());
        assertEquals(.07f, pose(0, .74).depth(), .00001f);
        assertEquals(.14f, pose(0, .80).depth(), .00001f);
        assertEquals(.14f, pose(0, .82).depth(), .00001f);
        assertEquals(.07f, pose(0, .88).depth(), .00001f);
        assertEquals(0f, pose(0, .94).depth(), .00001f);
        assertEquals(0f, pose(0, 1.5).depth(), 0f);
        assertFalse(GateAnimationState.from(view(0)).lit(1)); // Time alone cannot confirm a lock.
        assertTrue(GateAnimationState.from(view(1)).lit(1));
    }

    @Test public void successiveStepsReverseWithoutJumpingAndFinishAtTop() {
        for (int step = 0; step < 6; step++) {
            assertEquals(pose(step, 1).angle(), pose(step + 1, 0).angle(), 0.00001f);
        }
        assertEquals(0, pose(6, .8).movingChevron());
        assertFalse(pose(7, 0).drivingRing());
    }

    @Test public void interruptionAndLateViewerUseCurrentStateWithoutQueuedMotion() {
        for (String state : new String[]{"IDLE", "INCOMING", "OPEN"}) {
            GateDialMotion p = GateDialMotion.sample(new GateView(state, "INCOMING", "peer", 3, true, 5000, START), START + 4_000_000_000L);
            assertFalse(p.strokeLit()); assertFalse(p.drivingRing()); assertEquals(-1, p.movingChevron()); assertEquals(0f, p.depth(), 0f);
        }
        GateDialMotion offline = GateDialMotion.sample(new GateView("OUTGOING", "OUTGOING", "peer", 0, false, 5000, START), START + 4_000_000_000L);
        assertFalse(offline.strokeLit()); assertFalse(offline.drivingRing()); assertEquals(0f, offline.depth(), 0f);
        assertEquals(pose(2,.8), GateDialMotion.sample(view(2), START + 4_000_000_000L));
        GateView faster = new GateView("OUTGOING", "OUTGOING", "peer", 2, true, 2000, START);
        assertEquals(pose(2,.8), GateDialMotion.sample(faster, START + 1_600_000_000L));
    }
}
