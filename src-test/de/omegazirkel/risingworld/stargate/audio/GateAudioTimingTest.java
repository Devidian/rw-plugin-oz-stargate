package de.omegazirkel.risingworld.stargate.audio;

import static org.junit.Assert.*;
import org.junit.Test;

public class GateAudioTimingTest {
    @Test public void sevenStepsFitThreeConsecutiveClips() {
        for (int step = 0; step < 7; step++) {
            int ringEnd = GateAudioTiming.chevronStartMillis(step);
            assertEquals(GateAudioTiming.DHD_MS + GateAudioTiming.ringMillis(step), ringEnd);
            assertTrue(ringEnd + GateAudioTiming.CHEVRON_OUT_MS < GateAudioTiming.DIAL_STEP_MS);
        }
    }

    @Test public void shutdownClipEndsWithVisualCollapseAfterSixtySecondOpenWindow() {
        long opened = 1_000_000_000L;
        long cue = opened + GateAudioTiming.SHUTDOWN_START_MS * 1_000_000L;
        assertFalse(GateAudioTiming.closingCueDue(opened, cue - 1));
        assertTrue(GateAudioTiming.closingCueDue(opened, cue));
        assertFalse(GateAudioTiming.closingCueDue(0, cue));
        assertEquals(GateAudioTiming.OPEN_MS + GateAudioTiming.COLLAPSE_MS,
                GateAudioTiming.SHUTDOWN_START_MS + GateAudioTiming.SHUTDOWN_MS);
        long travelReady = opened + GateAudioTiming.INCOMING_TOTAL_MS * 1_000_000L;
        long closesAt = travelReady + GateAudioTiming.OPEN_MS * 1_000_000L;
        long delayedCue = closesAt + (GateAudioTiming.COLLAPSE_MS - GateAudioTiming.SHUTDOWN_MS) * 1_000_000L;
        assertFalse(GateAudioTiming.closingCueDueAt(0, delayedCue));
        assertFalse(GateAudioTiming.closingCueDueAt(closesAt, delayedCue - 1));
        assertTrue(GateAudioTiming.closingCueDueAt(closesAt, delayedCue));
        assertEquals(closesAt + GateAudioTiming.COLLAPSE_MS * 1_000_000L,
                delayedCue + GateAudioTiming.SHUTDOWN_MS * 1_000_000L);
    }
}
