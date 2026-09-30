package de.omegazirkel.risingworld.stargate.dhd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Optional visual placement; never owns a gate, inventory or transfer. */
public final class DhdModelStore {
    private final Connection database;

    public DhdModelStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_dhd_models (gate_id TEXT PRIMARY KEY, pos_x REAL NOT NULL, pos_y REAL NOT NULL, pos_z REAL NOT NULL, forward_x REAL NOT NULL, forward_z REAL NOT NULL)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_dhd_models_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_dhd_models WHERE gate_id=OLD.gate_id; END");
        }
    }

    public synchronized boolean save(DhdModelPlacement placement) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("INSERT OR REPLACE INTO stargate_dhd_models SELECT gate_id,?,?,?,?,? FROM stargates WHERE gate_id=?")) {
            statement.setFloat(1, placement.x()); statement.setFloat(2, placement.y()); statement.setFloat(3, placement.z());
            statement.setFloat(4, placement.forwardX()); statement.setFloat(5, placement.forwardZ());
            statement.setString(6, placement.gateId());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized void delete(String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_dhd_models WHERE gate_id=?")) {
            statement.setString(1, gateId); statement.executeUpdate();
        }
    }

    public synchronized List<DhdModelPlacement> all() throws SQLException {
        List<DhdModelPlacement> result = new ArrayList<>();
        try (Statement statement = database.createStatement();
             ResultSet rows = statement.executeQuery("SELECT d.* FROM stargate_dhd_models d JOIN stargates g ON g.gate_id=d.gate_id ORDER BY d.gate_id")) {
            while (rows.next()) result.add(new DhdModelPlacement(rows.getString(1), rows.getFloat(2), rows.getFloat(3),
                    rows.getFloat(4), rows.getFloat(5), rows.getFloat(6)));
        }
        return List.copyOf(result);
    }
}
