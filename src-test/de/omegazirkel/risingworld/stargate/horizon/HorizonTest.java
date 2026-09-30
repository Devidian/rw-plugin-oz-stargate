package de.omegazirkel.risingworld.stargate.horizon;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class HorizonTest {
    private final HorizonZone zone = new HorizonZone("A", -1,-0.2f,-1, 1,3,1);

    @Test public void validatesBoundsAndIncludesFloorAndEdges() {
        assertTrue(zone.contains(-1, -0.2f, 1));
        assertFalse(zone.contains(0, -0.21f, 0));
        assertFalse(zone.contains(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new HorizonZone("A", 0,0,0, Float.POSITIVE_INFINITY,1,1));
        assertThrows(IllegalArgumentException.class, () -> new HorizonZone("A", 0,0,0, 0,1,1));
        assertTrue(zone.overlaps(new HorizonZone("B", 1,0,0, 2,2,2)));
        assertFalse(zone.overlaps(new HorizonZone("B", 1.01f,0,0, 2,2,2)));
    }

    @Test public void arrivalAndReloadInsideDoNotTriggerAndCancellationNeedsExit() {
        HorizonEntryTracker tracker = new HorizonEntryTracker();
        List<HorizonZone> zones = List.of(zone);
        assertNull(tracker.move(zones, 0,0,0)); // arrival/reload baseline inside
        assertNull(tracker.move(zones, 0.5f,0,0)); // opening while inside
        assertNull(tracker.move(zones, 2,0,0));
        assertEquals("A", tracker.move(zones, 0,0,0));
        assertNull(tracker.move(zones, 0.5f,0,0)); // declined/failed, still inside
        assertNull(tracker.move(zones, 2,0,0));
        assertEquals("A", tracker.move(zones, 0,0,0));
    }

    @Test public void ambiguousOverlapAndCrossingWithoutInsideSampleDoNotTravel() {
        HorizonEntryTracker tracker = new HorizonEntryTracker();
        List<HorizonZone> overlap = List.of(zone, new HorizonZone("B", 0,0,0, 2,2,2));
        assertNull(tracker.move(overlap, -3,0,0));
        assertNull(tracker.move(overlap, 0.5f,1,0.5f));
        assertNull(tracker.move(overlap, -0.5f,1,0.5f)); // already latched A
        assertNull(tracker.move(overlap, -3,0,0));
        assertNull(tracker.move(overlap, 3,0,0));
        assertEquals("A", tracker.move(overlap, -0.5f,1,0.5f));
    }

    @Test public void persistsReplacesRemovesAndCleansOnGateDeletion() throws Exception {
        Path file = Files.createTempFile("stargate-horizon-", ".db");
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore gates = new LocalGateStore(db); gates.initialize();
                gates.save("A", new Vector3f(0,0,0), new Quaternion(0,0,0,1));
                HorizonStore store = new HorizonStore(db); store.initialize();
                assertFalse(store.save(new HorizonZone("MISSING", 0,0,0, 1,1,1)));
                assertTrue(store.save(zone));
                assertTrue(store.save(new HorizonZone("A", 1,1,1, 2,2,2)));
                assertEquals(1, store.all().size());
                assertTrue(store.save(zone));
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                HorizonStore store = new HorizonStore(db); store.initialize();
                assertEquals(List.of(zone), store.all());
                store.delete("A"); assertTrue(store.all().isEmpty());
                assertTrue(new LocalGateStore(db).exists("A"));
                assertTrue(store.save(zone));
                new LocalGateStore(db).delete("A");
                assertTrue(store.all().isEmpty());
                try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM stargate_horizons")) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
            }
        } finally { Files.deleteIfExists(file); }
    }
}
