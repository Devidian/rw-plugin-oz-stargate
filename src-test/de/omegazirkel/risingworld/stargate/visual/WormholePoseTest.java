package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

public class WormholePoseTest {
    private static final long OPENED = 10_000_000_000L;
    private GateView view(String state, boolean ready, long opened) {
        return new GateView(state, "OUTGOING", "peer", 7, ready, 5000, 0, opened);
    }
    private WormholePose at(double seconds) {
        return WormholePose.sample(view("OPEN", true, OPENED), OPENED
                + (long) (seconds * WormholePose.OPEN_AUDIO_SECONDS / WormholePose.SURGE_END * 1_000_000_000L));
    }

    @Test public void onlyAuthoritativeReadyOpenShowsWater() {
        for (String state : new String[]{"IDLE", "OUTGOING", "INCOMING", "UNKNOWN"}) {
            assertFalse(WormholePose.sample(view(state, true, OPENED), OPENED + 900_000_000L).visible());
        }
        assertFalse(WormholePose.sample(view("OPEN", false, OPENED), OPENED + 900_000_000L).visible());
        assertTrue(at(0).visible());
    }

    @Test public void openingGrowsAndSurgesForwardThenSettles() {
        assertEquals(.001f, at(0).radiusScale(), .00001f);
        assertEquals(1f, at(.22).radiusScale(), 0f);
        assertEquals(0f, at(.18).surgeDepth(), .00001f);
        assertEquals(3.78f, at(.915).surgeDepth(), .00001f);
        assertEquals(0f, at(2.05).surgeDepth(), .00001f);
        assertTrue(at(1.65).visible());
        for (int i = 0; i <= 300; i++) {
            WormholePose pose = at(i / 100d);
            assertTrue(pose.radiusScale() > 0 && pose.radiusScale() <= 1);
            assertTrue(pose.rearDepth() >= 0 && pose.rearDepth() <= .9f);
            assertTrue(pose.surgeDepth() >= 0 && pose.surgeDepth() <= 3.78f);
        }
    }

    @Test public void lateViewerAndUnknownAgeDoNotReplaySurge() {
        long now = OPENED + 8_000_000_000L;
        WormholePose late = WormholePose.sample(view("OPEN", true, OPENED), now);
        assertEquals(at(8), late);
        assertEquals(0f, late.surgeDepth(), 0f);
        assertEquals(0f, late.rearDepth(), 0f);
        WormholePose restored = WormholePose.sample(view("OPEN", true, 0), now);
        assertEquals(1f, restored.radiusScale(), 0f);
        assertEquals(0f, restored.surgeDepth(), 0f);
        assertEquals(0f, restored.rearDepth(), 0f);
    }

    @Test public void openingSettlesWithReferenceClip() {
        assertTrue(WormholePose.sample(view("OPEN", true, OPENED), OPENED + 3_000_000_000L).rearDepth() > 0);
        assertEquals(0f, WormholePose.sample(view("OPEN", true, OPENED), OPENED + 3_373_000_000L).rearDepth(), 0f);
    }

    @Test public void closedNetworkStateProvidesNoActiveWormhole() {
        assertTrue(at(.9).surgeDepth() > 1);
        WormholePose closed = WormholePose.sample(view("IDLE", true, OPENED), OPENED + 910_000_000L);
        assertFalse(closed.visible()); assertEquals(0f, closed.surgeDepth(), 0f);
    }
    @Test public void frontHoldsForFourTenthsThenRearVortexSettles() {
        for (int i = 0; i <= 40; i++)
            assertEquals(3.78f, at(.915 + i / 100d).surgeDepth(), .00001f);
        assertTrue(at(.905).surgeDepth() < 3.78f);
        assertTrue(at(1.325).surgeDepth() < 3.78f);
        assertEquals(0f, at(1.85).rearDepth(), 0f);
        assertTrue(at(1.95).rearDepth() > 0);
        assertEquals(0f, at(2.25).surgeDepth(), 0f);
        assertEquals(.9f, at(2.25).rearDepth(), .00001f);
        assertEquals(135f, at(2.25).rearAngle(), .00001f);
        assertEquals(0f, at(2.65).rearDepth(), 0f);
        assertTrue(at(2.65).visible());
        WormholePose offline = WormholePose.sample(view("OPEN", false, OPENED), OPENED + 2_250_000_000L);
        assertFalse(offline.visible());
        assertEquals(0f, offline.rearDepth(), 0f);
    }

}
