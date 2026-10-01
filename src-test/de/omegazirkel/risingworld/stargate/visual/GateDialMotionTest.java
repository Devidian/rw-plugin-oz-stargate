package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

public class GateDialMotionTest {
    private static final long START = 1_000_000_000L;
    private GateDialMotion pose(int step, int milliseconds) {
        GateView view = new GateView("OUTGOING", "OUTGOING", "peer", step, true,
                GateAudioTiming.DIAL_STEP_MS, START);
        return GateDialMotion.sample(view, START + milliseconds * 1_000_000L);
    }

    @Test public void buttonThenFullRingThenChevronWithoutOverlap() {
        int ringStart = GateAudioTiming.DHD_MS;
        int chevronStart = GateAudioTiming.chevronStartMillis(0);
        assertEquals(0f, pose(0, ringStart).angle(), 0f);
        assertEquals(0f, pose(0, ringStart - 1).depth(), 0f);
        assertTrue(pose(0, ringStart + 1500).angle() > 0);
        assertEquals(24f, pose(0, chevronStart).angle(), .00001f);
        assertEquals(0f, pose(0, chevronStart).depth(), 0f);
        assertTrue(pose(0, chevronStart + 900).depth() > 0);
        assertEquals(0f, pose(0, chevronStart + GateAudioTiming.CHEVRON_OUT_MS).depth(), .00001f);
        assertTrue(chevronStart + GateAudioTiming.CHEVRON_OUT_MS < GateAudioTiming.DIAL_STEP_MS);
    }

    @Test public void chevronMovesOutLightsAndReturns() {
        int start = GateAudioTiming.chevronStartMillis(0);
        assertFalse(pose(0, start + 1000).strokeLit());
        assertTrue(pose(0, start + 1300).strokeLit());
        assertEquals(.14f, pose(0, start + (int) (GateAudioTiming.CHEVRON_OUT_MS * .6)).depth(), .00001f);
        assertEquals(0f, pose(0, GateAudioTiming.DIAL_STEP_MS).depth(), 0f);
        assertFalse(GateAnimationState.from(new GateView("OUTGOING", "OUTGOING", "peer", 0, true)).lit(1));
    }

    @Test public void ringReversesContinuouslyAndSeventhLockStopsIt() {
        for (int step = 0; step < 6; step++)
            assertEquals(pose(step, GateAudioTiming.DIAL_STEP_MS).angle(), pose(step + 1, 0).angle(), 0f);
        GateView done = new GateView("OUTGOING", "OUTGOING", "peer", 7, true,
                GateAudioTiming.DIAL_STEP_MS, START);
        assertFalse(GateDialMotion.sample(done, START).drivingRing());
    }

    @Test public void interruptionCancelsAllMovement() {
        for (String state : new String[]{"IDLE", "INCOMING", "OPEN"}) {
            GateView view = new GateView(state, "INCOMING", "peer", 3, true,
                    GateAudioTiming.DIAL_STEP_MS, START);
            GateDialMotion motion = GateDialMotion.sample(view, START + 2_000_000_000L);
            assertFalse(motion.drivingRing());
            assertEquals(-1, motion.movingChevron());
            assertEquals(0f, motion.depth(), 0f);
        }
    }
}
