package de.omegazirkel.risingworld.stargate.dhd;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class DhdStoreTest {
    @Test public void linksPersistWithoutOverwritingAndGateDeletionCleansThem() throws Exception {
        Path file = Files.createTempFile("stargate-dhd-", ".db");
        DhdStore.ObjectKey object = new DhdStore.ObjectKey(42, -1, 2, 3);
        DhdStore.Binding binding = new DhdStore.Binding(object, 123456, (short) 7, "GATE-A");
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore gates = new LocalGateStore(db); gates.initialize();
                gates.save("GATE-A", new Vector3f(1,2,3), new Quaternion(0,0,0,1));
                gates.save("GATE-B", new Vector3f(2,2,3), new Quaternion(0,0,0,1));
                DhdStore store = new DhdStore(db); store.initialize();
                assertTrue(store.bind(binding));
                assertFalse(store.bind(new DhdStore.Binding(object, 987, (short) 8, "GATE-B")));
                assertEquals(binding, store.find(object));
                assertNull(store.find(new DhdStore.ObjectKey(42, 0, 2, 3)));
                assertFalse(store.bind(new DhdStore.Binding(new DhdStore.ObjectKey(43,0,0,0), 1, (short) 1, "MISSING")));
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                DhdStore store = new DhdStore(db); store.initialize();
                assertEquals(binding, store.find(object));
                assertTrue(store.unbind(object)); assertFalse(store.unbind(object));
                assertTrue(store.bind(binding));
                new LocalGateStore(db).delete("GATE-A");
                assertNull(store.find(object));
                assertTrue(new LocalGateStore(db).exists("GATE-B"));
            }
        } finally { Files.deleteIfExists(file); }
    }
}
