package de.omegazirkel.risingworld.stargate.network;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

/** World-local gate ownership and arrival transform. */
public final class LocalGateStore {
    public record Gate(String id, Vector3f position, Quaternion rotation) { }

    private final Connection database;

    public LocalGateStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargates (gate_id TEXT PRIMARY KEY, pos_x REAL NOT NULL, pos_y REAL NOT NULL, pos_z REAL NOT NULL, rot_x REAL NOT NULL, rot_y REAL NOT NULL, rot_z REAL NOT NULL, rot_w REAL NOT NULL, created_at INTEGER NOT NULL)");
        }
    }

    public synchronized void save(String id, Vector3f position, Quaternion rotation) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("INSERT INTO stargates VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, id);
            statement.setFloat(2, position.x);
            statement.setFloat(3, position.y);
            statement.setFloat(4, position.z);
            statement.setFloat(5, rotation.x);
            statement.setFloat(6, rotation.y);
            statement.setFloat(7, rotation.z);
            statement.setFloat(8, rotation.w);
            statement.setLong(9, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /** Update only the arrival transform; the registered relay identity is unchanged. */
    public synchronized boolean move(String id, Vector3f position, Quaternion rotation) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "UPDATE stargates SET pos_x=?,pos_y=?,pos_z=?,rot_x=?,rot_y=?,rot_z=?,rot_w=? WHERE gate_id=?")) {
            statement.setFloat(1, position.x); statement.setFloat(2, position.y); statement.setFloat(3, position.z);
            statement.setFloat(4, rotation.x); statement.setFloat(5, rotation.y);
            statement.setFloat(6, rotation.z); statement.setFloat(7, rotation.w);
            statement.setString(8, id);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean exists(String id) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("SELECT 1 FROM stargates WHERE gate_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public synchronized Gate gate(String id) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("SELECT pos_x,pos_y,pos_z,rot_x,rot_y,rot_z,rot_w FROM stargates WHERE gate_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                return new Gate(id, new Vector3f(rows.getFloat(1), rows.getFloat(2), rows.getFloat(3)),
                        new Quaternion(rows.getFloat(4), rows.getFloat(5), rows.getFloat(6), rows.getFloat(7)));
            }
        }
    }

    public synchronized void delete(String id) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargates WHERE gate_id=?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    public synchronized List<String> ids() throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery("SELECT gate_id FROM stargates ORDER BY created_at")) {
            while (rows.next()) ids.add(rows.getString(1));
        }
        return ids;
    }
}
