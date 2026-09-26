package de.omegazirkel.risingworld.stargate.inventory;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.Test;

import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotStore.State;

public class InventorySnapshotStoreTest {
    @Test
    public void escrowIsSingleUseAndTransitionsAreConditional() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            InventorySnapshotStore store = new InventorySnapshotStore(connection);
            store.initialize();
            byte[] inventory = { 1, 2, 3 };
            byte[] clothes = { 4, 5 };

            assertTrue(store.prepare("uid", inventory, clothes));
            assertFalse(store.prepare("uid", new byte[] { 9 }, new byte[] { 9 }));
            assertArrayEquals(inventory, store.escrow("uid").inventory());
            assertArrayEquals(clothes, store.escrow("uid").clothes());
            assertEquals(State.PREPARED, store.escrow("uid").state());

            assertFalse(store.transition("uid", State.PACKED, State.UNPACKING));
            assertTrue(store.transition("uid", State.PREPARED, State.PACKED));
            assertFalse(store.consume("uid", State.PREPARED));
            assertFalse(store.prepare("uid", new byte[] { 9 }, new byte[] { 9 }));
            assertTrue(store.transition("uid", State.PACKED, State.UNPACKING));
            assertTrue(store.consume("uid", State.UNPACKING));
            assertNull(store.escrow("uid"));
            assertFalse(store.consume("uid", State.UNPACKING));
        }
    }

    @Test
    public void oldCopyOnlySnapshotCannotBecomeAnEscrow() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE inventory_snapshots (player_uid TEXT PRIMARY KEY, format TEXT NOT NULL, data BLOB NOT NULL, saved_at INTEGER NOT NULL)");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO inventory_snapshots VALUES (?,?,?,?)")) {
                statement.setString(1, "uid");
                statement.setString(2, "rw-inventory-v1");
                statement.setBytes(3, new byte[] { 1 });
                statement.setLong(4, 1);
                statement.executeUpdate();
            }
            InventorySnapshotStore store = new InventorySnapshotStore(connection);
            store.initialize();

            assertTrue(store.hasLegacySnapshot("uid"));
            assertNull(store.escrow("uid"));
            assertTrue(store.prepare("uid", new byte[] { 2 }, new byte[0]));
            assertEquals(State.PREPARED, store.escrow("uid").state());
            store.clearLegacySnapshot("uid");
            assertFalse(store.hasLegacySnapshot("uid"));
        }
    }
}
