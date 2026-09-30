package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;

public class WormholeLoopTest {
    @Test public void playbackPreservesReferenceDurationAndHandlesLongUptime() {
        assertEquals(0, WormholeLoop.frame(0));
        assertEquals(1, WormholeLoop.frame(83_333_334L));
        assertEquals(139, WormholeLoop.frame(11_666_666_666L));
        assertEquals(0, WormholeLoop.frame(11_666_666_667L));
        assertEquals(0, WormholeLoop.frame(35_000_000_000L));
        for (long time : new long[]{Long.MIN_VALUE, -1, Long.MAX_VALUE, 999_999_999_999_999L}) {
            assertTrue(WormholeLoop.frame(time) >= 0);
            assertTrue(WormholeLoop.frame(time) < 140);
        }
    }

    @Test public void eachFrameSamplesInsideItsOwnTileAndKeepsImageUpright() {
        for (int frame = 0; frame < 140; frame++) {
            int column = frame % 7, row = (frame % 70) / 7;
            assertTrue(WormholeLoop.u(frame, -1) > column / 7f);
            assertTrue(WormholeLoop.u(frame, 1) < (column + 1) / 7f);
            assertTrue(WormholeLoop.v(frame, -1) > 1 - (row + 1) / 10f);
            assertTrue(WormholeLoop.v(frame, 1) < 1 - row / 10f);
            assertTrue(WormholeLoop.v(frame, 1) > WormholeLoop.v(frame, -1));
        }
        assertEquals(WormholeLoop.u(0, 0), WormholeLoop.u(70, 0), 0f);
        assertEquals(WormholeLoop.v(0, 0), WormholeLoop.v(70, 0), 0f);
    }
}
