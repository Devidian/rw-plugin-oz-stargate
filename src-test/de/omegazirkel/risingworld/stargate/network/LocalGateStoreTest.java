package de.omegazirkel.risingworld.stargate.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;

public class LocalGateStoreTest {
    @Test public void gateOwnershipPersistsAndUnregisterRemovesIt() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore store = new LocalGateStore(database);
            store.initialize();
            store.save("AB12CD34", new Vector3f(1, 2, 3), new Quaternion(0, 0, 0, 1));
            assertTrue(store.exists("AB12CD34"));
            assertEquals(java.util.List.of("AB12CD34"), store.ids());
            store.delete("AB12CD34");
            assertFalse(store.exists("AB12CD34"));
        }
    }

    @Test public void localAndGlobalAddressesAndAliasSurviveReopenWithoutChangingGateId() throws Exception {
        Path file = Files.createTempFile("stargate-address-", ".db");
        try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            LocalGateStore store = new LocalGateStore(database);
            store.initialize();
            store.save("LOCAL00000000001", new Vector3f(1, 2, 3), new Quaternion(0, 0, 0, 1));
            store.save("ABCDEF0123456789", new Vector3f(4, 5, 6), new Quaternion(0, 0, 0, 1));
            assertEquals("LOCAL00000000001", store.localAddress("LOCAL00000000001"));
            assertNull(store.globalAddress("LOCAL00000000001"));
            String oldGateLocal = store.localAddress("ABCDEF0123456789");
            assertTrue(oldGateLocal.matches("LOCAL[0-9A-F]{11}"));
            assertEquals("ABCDEF0123456789", store.globalAddress("ABCDEF0123456789"));
            store.setGlobalAddress("LOCAL00000000001", "1122334455667788");
            store.setAlias("LOCAL00000000001", "Alpha Gate");
            try (Connection reopened = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore restored = new LocalGateStore(reopened);
                restored.initialize();
                assertEquals(oldGateLocal, restored.localAddress("ABCDEF0123456789"));
                assertEquals("LOCAL00000000001", restored.gateByAddress("1122334455667788"));
                assertEquals("Alpha Gate", restored.alias("LOCAL00000000001"));
                restored.delete("LOCAL00000000001");
                assertNull(restored.gateByAddress("1122334455667788"));
                assertNull(restored.alias("LOCAL00000000001"));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test public void startGateDefaultsToOriginPersistsAndIsRemovedWithGate() throws Exception {
        Path file = Files.createTempFile("stargate-start-", ".db");
        try {
            try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore store = new LocalGateStore(database);
                store.initialize();
                LocalSectorStore sectors = new LocalSectorStore(database);
                sectors.initialize();
                sectors.saveGateWithSetup(store, "ORIGIN", new Vector3f(0, 2, 0),
                        new Quaternion(0, 0, 0, 1), new SectorAddress(0, 0), () -> { });
                sectors.saveGateWithSetup(store, "REMOTE", new Vector3f(100000, 2, 0),
                        new Quaternion(0, 0, 0, 1), new SectorAddress(10, 0), () -> { });
                assertTrue(store.isStartGate("ORIGIN"));
                assertFalse(store.isStartGate("REMOTE"));
                store.setStartGate("ORIGIN", false);
                store.setStartGate("REMOTE", true);
            }
            try (Connection database = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                LocalGateStore store = new LocalGateStore(database);
                store.initialize();
                assertFalse(store.isStartGate("ORIGIN"));
                assertTrue(store.isStartGate("REMOTE"));
                store.delete("REMOTE");
                assertFalse(store.isStartGate("REMOTE"));
            }
        } finally { Files.deleteIfExists(file); }
    }
}
