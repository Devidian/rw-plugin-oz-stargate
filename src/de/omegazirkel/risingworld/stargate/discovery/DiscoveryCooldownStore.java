package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** One persisted attempt time per player and server world. */
public final class DiscoveryCooldownStore {
    private final Connection database;

    public DiscoveryCooldownStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_discovery_cooldowns "
                    + "(player_uid TEXT PRIMARY KEY, next_at INTEGER NOT NULL)");
        }
    }

    public synchronized long nextAt(String uid) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT next_at FROM stargate_discovery_cooldowns WHERE player_uid=?")) {
            statement.setString(1, uid);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0L; }
        }
    }

    public synchronized void started(String uid, long nextAt) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT INTO stargate_discovery_cooldowns(player_uid,next_at) VALUES(?,?) "
                        + "ON CONFLICT(player_uid) DO UPDATE SET next_at=excluded.next_at")) {
            statement.setString(1, uid); statement.setLong(2, nextAt); statement.executeUpdate();
        }
    }
}
