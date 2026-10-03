package de.omegazirkel.risingworld.stargate.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TravelScreenServiceTest {
    @Test
    public void keepsOriginalDeadlineAcrossServers() {
        long start = 1_000_000L;
        assertEquals(10_000L, TravelScreenService.remainingMs(start, start));
        assertEquals(4_000L, TravelScreenService.remainingMs(start, start + 6_000L));
        assertEquals(0L, TravelScreenService.remainingMs(start, start + 10_000L));
        assertEquals(0L, TravelScreenService.remainingMs(start, start + 12_000L));
    }

    @Test
    public void rejectsAbsentOrFutureTimestamp() {
        assertEquals(0L, TravelScreenService.remainingMs(0L, 1_000_000L));
        assertEquals(0L, TravelScreenService.remainingMs(1_002_000L, 1_000_000L));
    }
}
