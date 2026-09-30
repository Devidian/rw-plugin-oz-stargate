package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.horizon.HorizonStore;
import de.omegazirkel.risingworld.stargate.horizon.HorizonZone;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class GateVisualTest {
    @Test public void normalizesHorizontalFacingAndRejectsInvalidTransforms() {
        GateVisualPlacement placement = new GateVisualPlacement("A", 1,2,3, 3,4);
        assertEquals(0.6f, placement.forwardX(), 0.00001f);
        assertEquals(0.8f, placement.forwardZ(), 0.00001f);
        assertEquals(25, placement.distanceSquared(1,5,7), 0.00001);
        assertThrows(IllegalArgumentException.class, () -> new GateVisualPlacement("A", 0,0,0, 0,0));
        assertThrows(IllegalArgumentException.class, () -> new GateVisualPlacement("A", Float.NaN,0,0, 0,1));
        assertThrows(IllegalArgumentException.class, () -> new GateVisualPlacement("A", 0,0,0, Float.POSITIVE_INFINITY,1));
    }

    @Test public void visibilityUsesWorldDistanceAndHysteresis() {
        List<GateVisualPlacement> placements = List.of(new GateVisualPlacement("A", 0,0,0, 0,1));
        assertEquals(1, GateVisualPlacement.nearby(placements, Set.of(), 128,0,0).size());
        assertTrue(GateVisualPlacement.nearby(placements, Set.of(), 128.01f,0,0).isEmpty());
        assertEquals(1, GateVisualPlacement.nearby(placements, Set.of("A"), 160,0,0).size());
        assertTrue(GateVisualPlacement.nearby(placements, Set.of("A"), 160.01f,0,0).isEmpty());
        assertTrue(GateVisualPlacement.nearby(placements, Set.of(), 0,129,0).isEmpty());
        assertTrue(GateVisualPlacement.nearby(placements, Set.of(), Float.NaN,0,0).isEmpty());
    }

    @Test public void selectsNearestModelsWithDeterministicCap() {
        List<GateVisualPlacement> placements = new ArrayList<>();
        for (int i = 30; i >= 0; i--) placements.add(new GateVisualPlacement("G"+i, i+1,0,0, 0,1));
        placements.add(new GateVisualPlacement("B", 0,0,0, 0,1));
        placements.add(new GateVisualPlacement("A", 0,0,0, 0,1));
        var selected = GateVisualPlacement.nearby(placements, Set.of(), 0,0,0);
        assertEquals(16, selected.size());
        assertEquals("A", selected.get(0).gateId());
        assertEquals("B", selected.get(1).gateId());
        assertEquals("G13", selected.get(15).gateId());
    }

    @Test public void persistsReplacesAndDeletesWithoutMovingGameplayRecords() throws Exception {
        Path file = Files.createTempFile("stargate-visual-", ".db");
        GateVisualPlacement placement = new GateVisualPlacement("A", 20,4,-6, 0,-1);
        HorizonZone horizon = new HorizonZone("A", 0,0,0, 1,2,1);
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore gates = new LocalGateStore(db); gates.initialize();
                gates.save("A", new Vector3f(1,2,3), new Quaternion(0,0,0,1));
                HorizonStore horizons = new HorizonStore(db); horizons.initialize(); horizons.save(horizon);
                GateVisualStore store = new GateVisualStore(db); store.initialize(); store.initialize();
                assertFalse(store.save(new GateVisualPlacement("MISSING", 0,0,0, 0,1)));
                assertTrue(store.save(new GateVisualPlacement("A", 1,1,1, 1,0)));
                assertTrue(store.save(placement));
                assertEquals(List.of(placement), store.all());
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                GateVisualStore store = new GateVisualStore(db); store.initialize();
                assertEquals(List.of(placement), store.all());
                store.delete("A"); assertTrue(store.all().isEmpty());
                LocalGateStore gates = new LocalGateStore(db);
                assertEquals(1f, gates.gate("A").position().x, 0f);
                assertEquals(2f, gates.gate("A").position().y, 0f);
                assertEquals(3f, gates.gate("A").position().z, 0f);
                assertEquals(1f, gates.gate("A").rotation().w, 0f);
                assertEquals(List.of(horizon), new HorizonStore(db).all());
                assertTrue(store.save(placement));
                gates.delete("A"); assertTrue(store.all().isEmpty());
                try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM stargate_visuals")) {
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                }
            }
        } finally { Files.deleteIfExists(file); }
    }
}
