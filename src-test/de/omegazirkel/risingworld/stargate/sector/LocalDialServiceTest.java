package de.omegazirkel.risingworld.stargate.sector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalDialServiceTest {
    @Test public void sevenStepsThenOpenAndExpire() {
        LocalDialService.Connection connection = new LocalDialService.Connection("A", "B", 1000);
        assertFalse(connection.advance(1000));
        for (int step = 1; step <= 7; step++) {
            long due = 1000L + step * LocalDialService.STEP_MS;
            assertFalse(connection.advance(due - 1));
            assertTrue(connection.advance(due));
            assertEquals(step, connection.chevrons);
        }
        assertTrue(connection.connecting);
        assertFalse(connection.open);
        assertFalse(connection.advance(connection.expiresAt - 1));
        long opened = connection.expiresAt;
        assertTrue(connection.advance(opened));
        assertTrue(connection.open);
        assertEquals(opened + LocalDialService.OPEN_MS, connection.expiresAt);
        assertFalse(connection.expired(connection.expiresAt - 1));
        assertTrue(connection.expired(connection.expiresAt));
    }

    @Test public void onlyOutgoingSequenceCanBeInterruptedByIncoming() {
        LocalDialService.Connection connection = new LocalDialService.Connection("A", "B", 0);
        assertTrue(connection.preemptible("A"));
        assertFalse(connection.preemptible("B"));
        connection.advance(7L * LocalDialService.STEP_MS);
        connection.advance(connection.expiresAt);
        assertFalse(connection.preemptible("A"));
    }
}
