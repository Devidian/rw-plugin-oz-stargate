package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

public class WormholeTransitionTest {
    private static final long NOW = 10_000_000_000L;
    private GateView view(String state, boolean ready, long opened) {
        return new GateView(state, "OUTGOING", "peer", 7, ready, 5000, 0, opened);
    }
    @Test public void normalClosureErodesWithoutUniformScalingAndEndsOnce() {
        WormholeTransition transition = new WormholeTransition();
        assertEquals(-1, transition.sample(view("OPEN", true, 0), NOW).dissolveFrame());
        GateView idle = view("IDLE", true, 0);
        for (int frame = 0; frame < 12; frame++) {
            WormholePose pose = transition.sample(idle, NOW + frame * 50_000_000L);
            assertTrue(pose.visible());
            assertEquals(1f, pose.radiusScale(), 0f);
            assertEquals(frame, pose.dissolveFrame());
            assertEquals(0f, pose.surgeDepth(), 0f);
        }
        assertFalse(transition.sample(idle, NOW + WormholeTransition.COLLAPSE_NANOS).visible());
        assertFalse(transition.sample(idle, NOW + 2_000_000_000L).visible());
    }
    @Test public void closureDuringOpeningKeepsCurrentRadiusAndCancelsSurge() {
        WormholeTransition transition = new WormholeTransition();
        WormholePose opening = transition.sample(view("OPEN", true, NOW - 100_000_000L), NOW);
        WormholePose closing = transition.sample(view("IDLE", true, 0), NOW);
        assertEquals(opening.radiusScale(), closing.radiusScale(), 0f);
        assertEquals(0f, closing.surgeDepth(), 0f);
    }
    @Test public void offlineOrNewDialCancelsOldTailAndNewOpenStartsNormally() {
        for (GateView interruption : new GateView[]{view("IDLE", false, 0), view("OUTGOING", true, 0)}) {
            WormholeTransition transition = new WormholeTransition();
            transition.sample(view("OPEN", true, 0), NOW);
            transition.sample(view("IDLE", true, 0), NOW);
            assertFalse(transition.sample(interruption, NOW + 100_000_000L).visible());
            assertFalse(transition.sample(view("IDLE", true, 0), NOW + 200_000_000L).visible());
            GateView reopened = view("OPEN", true, NOW + 300_000_000L);
            assertEquals(WormholePose.sample(reopened, NOW + 400_000_000L),
                    transition.sample(reopened, NOW + 400_000_000L));
        }
    }
    @Test public void newlyVisibleClosedGateNeverInventsClosingEffect() {
        WormholeTransition transition = new WormholeTransition();
        assertFalse(transition.sample(view("IDLE", true, 0), NOW).visible());
        assertFalse(transition.sample(view("INCOMING", true, 0), NOW + 10).visible());
    }
    @Test public void closureDuringRearVortexCancelsBothProtrusions() {
        WormholeTransition transition = new WormholeTransition();
        assertTrue(transition.sample(view("OPEN", true, NOW - 2_864_000_000L), NOW).rearDepth() > 0);
        WormholePose closing = transition.sample(view("IDLE", true, 0), NOW);
        assertTrue(closing.visible());
        assertEquals(0f, closing.surgeDepth(), 0f);
        assertEquals(0f, closing.rearDepth(), 0f);
        assertEquals(0, closing.dissolveFrame());
    }

}
