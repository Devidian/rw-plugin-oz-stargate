package de.omegazirkel.risingworld.stargate.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.Test;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

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
}
