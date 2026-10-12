package de.omegazirkel.risingworld.stargate.dhd;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;

public class BundledDhdModelTest {
    @Test public void keyLightsOnlyWhenDialStepActuallyStarts() {
        assertEquals(0, BundledDhdModel.litCircuits(new GateView("IDLE", "OUTGOING", "", 0, true, 7000, 0)));
        assertEquals(0, BundledDhdModel.litCircuits(new GateView("OUTGOING", "OUTGOING", "peer", 0, true, 7000, 0)));
        assertEquals(1, BundledDhdModel.litCircuits(new GateView("OUTGOING", "OUTGOING", "peer", 0, true, 7000, 1)));
        assertEquals(2, BundledDhdModel.litCircuits(new GateView("OUTGOING", "OUTGOING", "peer", 1, true, 7000, 2)));
        assertEquals(0, BundledDhdModel.litCircuits(new GateView("OUTGOING", "OUTGOING", "peer", 1, false, 7000, 2)));
    }
}
