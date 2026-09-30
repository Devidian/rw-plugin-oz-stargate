package de.omegazirkel.risingworld.stargate.visual;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Optional rendering data only; deleting a model never deletes the registered gate. */
public final class GateVisualStore {
    private final Connection database;
    public GateVisualStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_visuals (gate_id TEXT PRIMARY KEY, pos_x REAL NOT NULL, pos_y REAL NOT NULL, pos_z REAL NOT NULL, forward_x REAL NOT NULL, forward_z REAL NOT NULL)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_visuals_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_visuals WHERE gate_id=OLD.gate_id; END");
        }
    }

    public synchronized boolean save(GateVisualPlacement placement) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("INSERT OR REPLACE INTO stargate_visuals SELECT gate_id,?,?,?,?,? FROM stargates WHERE gate_id=?")) {
            statement.setFloat(1, placement.x()); statement.setFloat(2, placement.y()); statement.setFloat(3, placement.z());
            statement.setFloat(4, placement.forwardX()); statement.setFloat(5, placement.forwardZ());
            statement.setString(6, placement.gateId());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized void delete(String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_visuals WHERE gate_id=?")) {
            statement.setString(1, gateId); statement.executeUpdate();
        }
    }

    public synchronized List<GateVisualPlacement> all() throws SQLException {
        List<GateVisualPlacement> result = new ArrayList<>();
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery("SELECT v.* FROM stargate_visuals v JOIN stargates g ON g.gate_id=v.gate_id ORDER BY v.gate_id")) {
            while (rows.next()) result.add(new GateVisualPlacement(rows.getString(1), rows.getFloat(2), rows.getFloat(3),
                    rows.getFloat(4), rows.getFloat(5), rows.getFloat(6)));
        }
        return List.copyOf(result);
    }
}
