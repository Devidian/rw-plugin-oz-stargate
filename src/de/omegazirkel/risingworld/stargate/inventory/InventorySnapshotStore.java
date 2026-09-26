package de.omegazirkel.risingworld.stargate.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** One world-local, single-use escrow per player UID. */
public final class InventorySnapshotStore {
    public enum State { PREPARED, PACKED, UNPACKING }

    public record Escrow(byte[] inventory, byte[] clothes, State state) { }

    private final Connection database;

    public InventorySnapshotStore(Connection database) {
        this.database = database;
    }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_escrow (player_uid TEXT PRIMARY KEY, inventory BLOB NOT NULL, clothes BLOB NOT NULL, state TEXT NOT NULL, saved_at INTEGER NOT NULL)");
            // Previous experimental versions used these tables. Keep them readable for
            // recovery, but never treat a copy-only snapshot as packed inventory.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_snapshots (player_uid TEXT PRIMARY KEY, format TEXT NOT NULL, data BLOB NOT NULL, saved_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS inventory_recovery (player_uid TEXT PRIMARY KEY, format TEXT NOT NULL, data BLOB NOT NULL, saved_at INTEGER NOT NULL)");
        }
    }

    public synchronized boolean prepare(String uid, byte[] inventory, byte[] clothes) throws SQLException {
        if (inventory == null || clothes == null) {
            throw new IllegalArgumentException("Missing inventory or clothing serialization");
        }
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT OR IGNORE INTO inventory_escrow (player_uid,inventory,clothes,state,saved_at) VALUES (?,?,?,?,?)")) {
            statement.setString(1, uid);
            statement.setBytes(2, inventory);
            statement.setBytes(3, clothes);
            statement.setString(4, State.PREPARED.name());
            statement.setLong(5, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized Escrow escrow(String uid) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT inventory,clothes,state FROM inventory_escrow WHERE player_uid=?")) {
            statement.setString(1, uid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new Escrow(result.getBytes(1), result.getBytes(2), State.valueOf(result.getString(3)));
            } catch (IllegalArgumentException ex) {
                throw new SQLException("Unknown inventory escrow state", ex);
            }
        }
    }

    public synchronized boolean transition(String uid, State from, State to) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "UPDATE inventory_escrow SET state=? WHERE player_uid=? AND state=?")) {
            statement.setString(1, to.name());
            statement.setString(2, uid);
            statement.setString(3, from.name());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean consume(String uid, State state) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM inventory_escrow WHERE player_uid=? AND state=?")) {
            statement.setString(1, uid);
            statement.setString(2, state.name());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean hasLegacySnapshot(String uid) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT 1 FROM inventory_snapshots WHERE player_uid=?")) {
            statement.setString(1, uid);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public synchronized void clearLegacySnapshot(String uid) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM inventory_snapshots WHERE player_uid=?")) {
            statement.setString(1, uid);
            statement.executeUpdate();
        }
    }

    public synchronized byte[] legacyRecovery(String uid) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT format,data FROM inventory_recovery WHERE player_uid=?")) {
            statement.setString(1, uid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                if (!"rw-inventory-v1".equals(result.getString(1))) {
                    throw new SQLException("Unsupported legacy recovery format");
                }
                return result.getBytes(2);
            }
        }
    }

    public synchronized void clearLegacyRecovery(String uid) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM inventory_recovery WHERE player_uid=?")) {
            statement.setString(1, uid);
            statement.executeUpdate();
        }
    }
}
