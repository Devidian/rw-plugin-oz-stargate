package de.omegazirkel.risingworld.stargate.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RemoteGateStateTest {
    @Test public void staleIdleCannotCloseNewerConnection() {
        RemoteGateState state = new RemoteGateState();
        state.transition("a", "OPEN", "OUTGOING", "new", "b", 100);
        assertFalse(state.transition("a", "IDLE", "OUTGOING", "old", "b", 200).accepted());
        assertEquals("new", state.get("a").connectionId());
        assertTrue(state.transition("a", "IDLE", "OUTGOING", "new", "b", 300).closedOpen());
        assertEquals(null, state.get("a"));
    }

    @Test public void duplicateOpenPreservesStartAndNewConnectionGetsNewStart() {
        RemoteGateState state = new RemoteGateState();
        state.transition("a", "OPEN", "OUTGOING", "one", "b", 100);
        state.transition("a", "OPEN", "OUTGOING", "one", "b", 200);
        assertEquals(100L, state.openedAt("a"));
        state.transition("a", "OPEN", "OUTGOING", "two", "c", 300);
        assertEquals(300L, state.openedAt("a"));
    }

    @Test public void incomingClockIsClearedByOtherStatesAndReset() {
        RemoteGateState state = new RemoteGateState();
        state.transition("a", "INCOMING", "INCOMING", "one", "b", 10);
        state.startIncoming("a", 20);
        assertEquals(20L, state.incomingAt("a"));
        state.transition("a", "OPEN", "INCOMING", "one", "b", 100);
        assertEquals(0L, state.incomingAt("a"));
        assertTrue(state.closingAt("a") > 100L);
        state.clear();
        assertEquals(null, state.get("a"));
        assertEquals(0L, state.openedAt("a"));
        assertEquals(0L, state.closingAt("a"));
        assertFalse(state.hasIncoming());
    }

    @Test public void repeatedIncomingOpenSignalsAnnouncementOnlyOnce() {
        RemoteGateState state = new RemoteGateState();
        assertTrue(state.transition("a", "OPEN", "INCOMING", "one", "b", 100).announceIncoming());
        assertFalse(state.transition("a", "OPEN", "INCOMING", "one", "b", 200).announceIncoming());
        assertTrue(state.transition("a", "OPEN", "INCOMING", "two", "b", 300).announceIncoming());
    }

    @Test public void outgoingAndIncomingDirectionsAndExplicitCloseDeadlineSurviveDuplicates() {
        RemoteGateState state = new RemoteGateState();
        state.transition("a", "OPEN", "OUTGOING", "one", "b", 10);
        state.setClosingAt("a", 500);
        state.transition("a", "OPEN", "OUTGOING", "one", "b", 20);
        assertEquals("OUTGOING", state.get("a").direction());
        assertEquals(500L, state.closingAt("a"));
        state.transition("b", "INCOMING", "INCOMING", "two", "a", 30);
        assertEquals("INCOMING", state.get("b").direction());
        assertEquals("a", state.get("b").peerGateId());
    }
}
