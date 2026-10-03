package de.omegazirkel.risingworld.stargate.arrival;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Tracks the first regular visit separately from Stargate transfers. */
public final class FirstArrivalStore {
    public record Visit(String state, boolean wasInvisible) { }

    private final Connection db;

    public FirstArrivalStore(Connection db) { this.db = db; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_first_visits (player_uid TEXT PRIMARY KEY, state TEXT NOT NULL, was_invisible INTEGER NOT NULL)");
        }
    }

    public synchronized Visit visit(String uid) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(
                "SELECT state,was_invisible FROM stargate_first_visits WHERE player_uid=?")) {
            statement.setString(1, uid);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new Visit(rows.getString(1), rows.getInt(2) != 0) : null;
            }
        }
    }

    public synchronized void pending(String uid, boolean wasInvisible) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(
                "INSERT OR IGNORE INTO stargate_first_visits(player_uid,state,was_invisible) VALUES (?,'PENDING',?)")) {
            statement.setString(1, uid);
            statement.setInt(2, wasInvisible ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public synchronized void arm(String uid, boolean wasInvisible) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(
                "INSERT INTO stargate_first_visits(player_uid,state,was_invisible) VALUES (?,'ARRIVING',?) "
                + "ON CONFLICT(player_uid) DO UPDATE SET state='ARRIVING',was_invisible=excluded.was_invisible WHERE state='PENDING'")) {
            statement.setString(1, uid);
            statement.setInt(2, wasInvisible ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public synchronized void done(String uid) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(
                "INSERT INTO stargate_first_visits(player_uid,state,was_invisible) VALUES (?,'DONE',0) ON CONFLICT(player_uid) DO UPDATE SET state='DONE'")) {
            statement.setString(1, uid);
            statement.executeUpdate();
        }
    }
}
