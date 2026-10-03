package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Persists whether the first-install start-sector gate is still due. */
public final class InitialGateStore {
    private final Connection database;

    public InitialGateStore(Connection database) { this.database = database; }

    /** Must run before LocalGateStore creates the stargates table. */
    public synchronized void initialize() throws SQLException {
        boolean fresh;
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='stargates'");
                ResultSet rows = statement.executeQuery()) {
            fresh = !rows.next();
        }
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_initial_gate "
                    + "(singleton INTEGER PRIMARY KEY CHECK(singleton=1), state TEXT NOT NULL)");
        }
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT OR IGNORE INTO stargate_initial_gate(singleton,state) VALUES(1,?)")) {
            statement.setString(1, fresh ? "PENDING" : "DONE");
            statement.executeUpdate();
        }
    }

    public synchronized boolean pending() throws SQLException {
        try (Statement statement = database.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT state FROM stargate_initial_gate WHERE singleton=1")) {
            return rows.next() && "PENDING".equals(rows.getString(1));
        }
    }

    public synchronized void complete() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("UPDATE stargate_initial_gate SET state='DONE' WHERE singleton=1");
        }
    }
}
