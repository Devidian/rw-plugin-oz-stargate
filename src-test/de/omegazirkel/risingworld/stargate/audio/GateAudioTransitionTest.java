package de.omegazirkel.risingworld.stargate.audio;

import static org.junit.Assert.*;
import org.junit.Test;

public class GateAudioTransitionTest {
    private GateAudioTransition move(String before, String direction, long step,
            String after, String nextDirection, long nextStep, int chevrons) {
        return GateAudioTransition.between(before, direction, step, after, nextDirection, nextStep, chevrons);
    }

    @Test public void oneOutgoingRingPerNewStepAndNoLateReplay() {
        GateAudioTransition first = move("IDLE", "OUTGOING", 0, "OUTGOING", "OUTGOING", 10, 0);
        assertTrue(first.startOutgoingStep());
        GateAudioTransition repeat = move("OUTGOING", "OUTGOING", 10, "OUTGOING", "OUTGOING", 10, 0);
        assertFalse(repeat.startOutgoingStep());
        assertTrue(move("OUTGOING", "OUTGOING", 10,
                "OUTGOING", "OUTGOING", 20, 1).startOutgoingStep());
    }

    @Test public void incomingPreemptionAndTerminalStatesCancelPreviousCue() {
        GateAudioTransition incoming = move("OUTGOING", "OUTGOING", 10,
                "INCOMING", "INCOMING", 0, 0);
        assertTrue(incoming.changedMode());
        assertNull(incoming.stateCue());
        assertFalse(incoming.startOutgoingStep());
        assertEquals("gate_open.ogg", move("INCOMING", "INCOMING", 0,
                "OPEN", "INCOMING", 0, 7).stateCue());
        assertEquals("shutdown_b.ogg", move("OPEN", "INCOMING", 0,
                "IDLE", "OUTGOING", 0, 0).stateCue());
        assertEquals("dial_fail.ogg", move("OUTGOING", "OUTGOING", 10,
                "IDLE", "OUTGOING", 0, 0).stateCue());
        assertNull(move("IDLE", "OUTGOING", 0,
                "IDLE", "OUTGOING", 0, 0).stateCue());
    }
}
