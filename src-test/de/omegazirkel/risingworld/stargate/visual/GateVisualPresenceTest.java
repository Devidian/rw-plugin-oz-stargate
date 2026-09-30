package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;

public class GateVisualPresenceTest {
    @Test public void boundsOutstandingRequestsAndChecksAgainAfterSuccessfulReply() {
        GateVisualPresence presence = new GateVisualPresence();
        long token = presence.begin(0);
        assertTrue(token > 0);
        assertEquals(0, presence.begin(1));
        assertTrue(presence.complete(token, 100));
        assertFalse(presence.complete(token, 101));
        assertEquals(0, presence.begin(100 + GateVisualPresence.INTERVAL_NANOS - 1));
        assertTrue(presence.begin(100 + GateVisualPresence.INTERVAL_NANOS) > token);
    }

    @Test public void lostReplyTimesOutOnceAndCannotCompleteLaterRequest() {
        GateVisualPresence presence = new GateVisualPresence();
        long old = presence.begin(0);
        assertFalse(presence.timedOut(GateVisualPresence.TIMEOUT_NANOS - 1));
        assertTrue(presence.timedOut(GateVisualPresence.TIMEOUT_NANOS));
        assertFalse(presence.timedOut(GateVisualPresence.TIMEOUT_NANOS + 1));
        long now = GateVisualPresence.TIMEOUT_NANOS + GateVisualPresence.INTERVAL_NANOS;
        long current = presence.begin(now);
        assertTrue(current > old);
        assertFalse(presence.complete(old, now));
        assertTrue(presence.complete(current, now));
    }

    @Test public void removalReplacementAndDistanceChangeInvalidateProbe() {
        GateVisualPresence presence = new GateVisualPresence();
        long old = presence.begin(0);
        presence.reset(100);
        assertFalse(presence.complete(old, 101));
        assertFalse(presence.timedOut(GateVisualPresence.TIMEOUT_NANOS));
        assertTrue(presence.begin(GateVisualPresence.TIMEOUT_NANOS) > old);
    }
}
