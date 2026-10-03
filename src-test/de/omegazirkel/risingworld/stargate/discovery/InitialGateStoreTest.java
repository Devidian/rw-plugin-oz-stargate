package de.omegazirkel.risingworld.stargate.discovery;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.Test;

import de.omegazirkel.risingworld.stargate.network.LocalGateStore;

public class InitialGateStoreTest {
    @Test public void freshDatabaseKeepsBootstrapPendingAcrossReloadUntilCompleted() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            InitialGateStore store = new InitialGateStore(database);
            store.initialize();
            new LocalGateStore(database).initialize();
            assertTrue(store.pending());
            store.initialize();
            assertTrue(store.pending());
            store.complete();
            store.initialize();
            assertFalse(store.pending());
        }
    }

    @Test public void existingGateInventoryNeverBootstrapsAfterUpgrade() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = database.createStatement()) {
                statement.executeUpdate("CREATE TABLE stargates(gate_id TEXT PRIMARY KEY)");
            }
            InitialGateStore store = new InitialGateStore(database);
            store.initialize();
            assertFalse(store.pending());
        }
    }
}
