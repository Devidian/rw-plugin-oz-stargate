package de.omegazirkel.risingworld.stargate.dhd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** World-local links only; inventory and gate networking remain separate. */
public final class DhdStore {
    public record ObjectKey(long id, int x, int y, int z) { }
    public record Binding(ObjectKey object, long createdAt, short type, String gateId) { }
    private final Connection database;

    public DhdStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_dhds (object_id INTEGER NOT NULL, chunk_x INTEGER NOT NULL, chunk_y INTEGER NOT NULL, chunk_z INTEGER NOT NULL, object_created INTEGER NOT NULL, object_type INTEGER NOT NULL, gate_id TEXT NOT NULL, PRIMARY KEY(object_id,chunk_x,chunk_y,chunk_z))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS stargate_dhds_gate ON stargate_dhds(gate_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS stargate_dhds_chunk ON stargate_dhds(chunk_x,chunk_y,chunk_z)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_dhds_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_dhds WHERE gate_id=OLD.gate_id; END");
        }
    }

    public synchronized boolean bind(Binding binding) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("INSERT OR IGNORE INTO stargate_dhds SELECT ?,?,?,?,?,?,gate_id FROM stargates WHERE gate_id=?")) {
            key(statement, binding.object());
            statement.setLong(5, binding.createdAt()); statement.setShort(6, binding.type()); statement.setString(7, binding.gateId());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized Binding find(ObjectKey object) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("SELECT object_created,object_type,gate_id FROM stargate_dhds WHERE object_id=? AND chunk_x=? AND chunk_y=? AND chunk_z=?")) {
            key(statement, object);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new Binding(object, rows.getLong(1), rows.getShort(2), rows.getString(3)) : null;
            }
        }
    }

    public synchronized List<Binding> inChunk(int x, int y, int z) throws SQLException {
        List<Binding> result = new ArrayList<>();
        try (PreparedStatement statement = database.prepareStatement("SELECT d.object_id,d.object_created,d.object_type,d.gate_id FROM stargate_dhds d JOIN stargates g ON g.gate_id=d.gate_id WHERE d.chunk_x=? AND d.chunk_y=? AND d.chunk_z=?")) {
            statement.setInt(1, x); statement.setInt(2, y); statement.setInt(3, z);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new Binding(new ObjectKey(rows.getLong(1), x, y, z), rows.getLong(2), rows.getShort(3), rows.getString(4)));
            }
        }
        return List.copyOf(result);
    }

    public synchronized boolean unbind(ObjectKey object) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_dhds WHERE object_id=? AND chunk_x=? AND chunk_y=? AND chunk_z=?")) {
            key(statement, object); return statement.executeUpdate() == 1;
        }
    }

    private static void key(PreparedStatement statement, ObjectKey key) throws SQLException {
        statement.setLong(1, key.id()); statement.setInt(2, key.x()); statement.setInt(3, key.y()); statement.setInt(4, key.z());
    }
}
