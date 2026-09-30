package de.omegazirkel.risingworld.stargate.dhd;

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

public class DhdModelStoreTest {
    @Test public void persistsOnePlacementPerGateAndCleansUpOnlyThatGate() throws Exception {
        Path file = Files.createTempFile("stargate-dhd-model-", ".db");
        DhdModelPlacement first = new DhdModelPlacement("GATE-A", 1, 2, 3, 0, 1);
        DhdModelPlacement moved = new DhdModelPlacement("GATE-A", 4, 2, 3, 1, 0);
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore gates = new LocalGateStore(db); gates.initialize();
                gates.save("GATE-A", new Vector3f(1, 2, 3), new Quaternion(0, 0, 0, 1));
                gates.save("GATE-B", new Vector3f(2, 2, 3), new Quaternion(0, 0, 0, 1));
                DhdModelStore store = new DhdModelStore(db); store.initialize();
                assertTrue(store.save(first));
                assertFalse(store.save(new DhdModelPlacement("MISSING", 0, 0, 0, 0, 1)));
                assertTrue(store.save(moved));
                assertEquals(List.of(moved), store.all());
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                DhdModelStore store = new DhdModelStore(db); store.initialize();
                assertEquals(List.of(moved), store.all());
                new LocalGateStore(db).delete("GATE-A");
                assertEquals(List.of(), store.all());
                assertTrue(new LocalGateStore(db).exists("GATE-B"));
            }
        } finally { Files.deleteIfExists(file); }
    }
}
