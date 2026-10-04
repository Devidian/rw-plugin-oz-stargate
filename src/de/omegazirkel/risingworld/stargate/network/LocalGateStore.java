package de.omegazirkel.risingworld.stargate.network;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_aliases (gate_id TEXT PRIMARY KEY, alias TEXT NOT NULL)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_alias_delete AFTER DELETE ON stargates BEGIN DELETE FROM stargate_aliases WHERE gate_id=OLD.gate_id; END");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_addresses (gate_id TEXT PRIMARY KEY, local_address TEXT NOT NULL UNIQUE, global_address TEXT UNIQUE)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_address_delete AFTER DELETE ON stargates BEGIN DELETE FROM stargate_addresses WHERE gate_id=OLD.gate_id; END");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_start_gates (gate_id TEXT PRIMARY KEY, enabled INTEGER NOT NULL CHECK(enabled IN (0,1)))");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_start_gate_delete AFTER DELETE ON stargates BEGIN DELETE FROM stargate_start_gates WHERE gate_id=OLD.gate_id; END");
        }
        for (String id : ids()) ensureAddress(id);
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
        ensureAddress(id);
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

    public synchronized void setAlias(String id, String alias) throws SQLException {
        if (!exists(id)) throw new SQLException("Gate does not exist: " + id);
        if (alias == null || alias.isBlank()) {
            try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_aliases WHERE gate_id=?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT INTO stargate_aliases(gate_id,alias) VALUES (?,?) ON CONFLICT(gate_id) DO UPDATE SET alias=excluded.alias")) {
            statement.setString(1, id);
            statement.setString(2, alias);
            statement.executeUpdate();
        }
    }

    public synchronized String alias(String id) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("SELECT alias FROM stargate_aliases WHERE gate_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized Map<String, String> aliases() throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery("SELECT gate_id,alias FROM stargate_aliases")) {
            while (rows.next()) result.put(rows.getString(1), rows.getString(2));
        }
        return result;
    }

    /** Existing gates in the origin sector are start gates until explicitly changed. */
    public synchronized boolean isStartGate(String id) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("SELECT enabled FROM stargate_start_gates WHERE gate_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) return rows.getInt(1) == 1;
            }
        }
        return isOriginGate(id);
    }

    public synchronized boolean isOriginGate(String id) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT 1 FROM stargate_local_sectors WHERE gate_id=? AND sector_x=0 AND sector_z=0")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public synchronized void setStartGate(String id, boolean enabled) throws SQLException {
        if (!exists(id)) throw new SQLException("Gate does not exist: " + id);
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT INTO stargate_start_gates(gate_id,enabled) VALUES(?,?) ON CONFLICT(gate_id) DO UPDATE SET enabled=excluded.enabled")) {
            statement.setString(1, id);
            statement.setInt(2, enabled ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private void ensureAddress(String id) throws SQLException {
        if (localAddress(id) != null) return;
        boolean localId = id.matches("LOCAL[0-9A-F]{11}");
        for (int attempt = 0; attempt < 5; attempt++) {
            try (PreparedStatement statement = database.prepareStatement(
                    "INSERT OR IGNORE INTO stargate_addresses(gate_id,local_address,global_address) VALUES (?,?,?)")) {
                statement.setString(1, id);
                statement.setString(2, localId ? id : "LOCAL" + UUID.randomUUID().toString().replace("-", "").substring(0, 11).toUpperCase());
                statement.setString(3, localId ? null : id);
                statement.executeUpdate();
            }
            if (localAddress(id) != null) return;
        }
        throw new SQLException("Cannot allocate local Stargate address for " + id);
    }

    public synchronized String localAddress(String id) throws SQLException {
        return address(id, "local_address");
    }

    public synchronized String globalAddress(String id) throws SQLException {
        return address(id, "global_address");
    }

    private String address(String id, String column) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("SELECT " + column + " FROM stargate_addresses WHERE gate_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized void setGlobalAddress(String id, String address) throws SQLException {
        if (!exists(id)) throw new SQLException("Gate does not exist: " + id);
        try (PreparedStatement statement = database.prepareStatement("UPDATE stargate_addresses SET global_address=? WHERE gate_id=?")) {
            statement.setString(1, address);
            statement.setString(2, id);
            if (statement.executeUpdate() != 1) throw new SQLException("Gate address is missing: " + id);
        }
    }

    public synchronized String gateByAddress(String address) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT gate_id FROM stargate_addresses WHERE local_address=? OR global_address=?")) {
            statement.setString(1, address);
            statement.setString(2, address);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }
}
