package de.omegazirkel.risingworld.stargate.horizon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.utils.Quaternion;

/** Additive gate-owned world storage; does not touch inventory/transfer tables. */
public final class HorizonStore {
    private final Connection database;
    public HorizonStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_horizons (gate_id TEXT PRIMARY KEY, min_x REAL NOT NULL, min_y REAL NOT NULL, min_z REAL NOT NULL, max_x REAL NOT NULL, max_y REAL NOT NULL, max_z REAL NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_aligned_horizons (gate_id TEXT PRIMARY KEY, centre_x REAL NOT NULL, centre_y REAL NOT NULL, centre_z REAL NOT NULL, forward_x REAL NOT NULL, forward_z REAL NOT NULL)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_aligned_horizons_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_aligned_horizons WHERE gate_id=OLD.gate_id; END");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_horizons_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_horizons WHERE gate_id=OLD.gate_id; END");
        }
    }

    public synchronized boolean save(HorizonZone zone) throws SQLException {
        if (zone.aligned() != null) throw new IllegalArgumentException("Use explicit alignment transaction");
        try (PreparedStatement statement = database.prepareStatement("INSERT OR REPLACE INTO stargate_horizons SELECT gate_id,?,?,?,?,?,? FROM stargates WHERE gate_id=? AND NOT EXISTS (SELECT 1 FROM stargate_aligned_horizons a WHERE a.gate_id=stargates.gate_id)")) {
            statement.setFloat(1, zone.minX()); statement.setFloat(2, zone.minY()); statement.setFloat(3, zone.minZ());
            statement.setFloat(4, zone.maxX()); statement.setFloat(5, zone.maxY()); statement.setFloat(6, zone.maxZ());
            statement.setString(7, zone.gateId());
            return statement.executeUpdate() == 1;
        }
    }

    /** Called only inside the first-placement transaction after the gate exists. */
    public synchronized void saveNewAligned(GateVisualPlacement placement) throws SQLException {
        AlignedPassage passage = AlignedPassage.from(placement);
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT INTO stargate_aligned_horizons VALUES (?,?,?,?,?,?)")) {
            statement.setString(1, placement.gateId()); statement.setFloat(2, passage.x());
            statement.setFloat(3, passage.y()); statement.setFloat(4, passage.z());
            statement.setFloat(5, passage.forwardX()); statement.setFloat(6, passage.forwardZ());
            statement.executeUpdate();
        }
    }

    /** Called inside the coordinated gate-move transaction. */
    public synchronized void replaceAligned(GateVisualPlacement placement) throws SQLException {
        AlignedPassage passage = AlignedPassage.from(placement);
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT OR REPLACE INTO stargate_aligned_horizons VALUES (?,?,?,?,?,?)")) {
            statement.setString(1, placement.gateId()); statement.setFloat(2, passage.x());
            statement.setFloat(3, passage.y()); statement.setFloat(4, passage.z());
            statement.setFloat(5, passage.forwardX()); statement.setFloat(6, passage.forwardZ());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_horizons WHERE gate_id=?")) {
            statement.setString(1, placement.gateId()); statement.executeUpdate();
        }
    }

    /** Arrival update, legacy-zone removal and model-aligned passage creation are one commit. */
    public synchronized boolean align(GateVisualPlacement placement, Vector3f arrival, Quaternion rotation) throws SQLException {
        AlignedPassage passage = AlignedPassage.from(placement);
        if (!passage.acceptsArrival(arrival.x, arrival.y, arrival.z)) throw new IllegalArgumentException("Arrival must be in front of aperture");
        if (!Float.isFinite(rotation.x) || !Float.isFinite(rotation.y) || !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w))
            throw new IllegalArgumentException("Invalid arrival rotation");
        if (!database.getAutoCommit()) throw new SQLException("Unexpected nested alignment transaction");
        database.setAutoCommit(false);
        try {
            // Verify the model transform and idle journal again; tolerate float direction renormalization.
            try (PreparedStatement statement = database.prepareStatement("SELECT 1 FROM stargate_visuals v JOIN stargates g ON g.gate_id=v.gate_id WHERE v.gate_id=? AND v.pos_x=? AND v.pos_y=? AND v.pos_z=? AND ABS(v.forward_x-?)<0.000001 AND ABS(v.forward_z-?)<0.000001 AND NOT EXISTS (SELECT 1 FROM stargate_transfers t WHERE t.gate_id=v.gate_id AND t.state NOT IN ('DONE','CANCELLED'))")) {
                statement.setString(1, placement.gateId()); statement.setFloat(2, placement.x()); statement.setFloat(3, placement.y());
                statement.setFloat(4, placement.z()); statement.setFloat(5, placement.forwardX()); statement.setFloat(6, placement.forwardZ());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) { database.rollback(); return false; }
                }
            }
            try (PreparedStatement statement = database.prepareStatement("UPDATE stargates SET pos_x=?,pos_y=?,pos_z=?,rot_x=?,rot_y=?,rot_z=?,rot_w=? WHERE gate_id=?")) {
                statement.setFloat(1, arrival.x); statement.setFloat(2, arrival.y); statement.setFloat(3, arrival.z);
                statement.setFloat(4, rotation.x); statement.setFloat(5, rotation.y); statement.setFloat(6, rotation.z); statement.setFloat(7, rotation.w);
                statement.setString(8, placement.gateId());
                if (statement.executeUpdate() != 1) throw new SQLException("Gate disappeared during alignment");
            }
            try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_horizons WHERE gate_id=?")) {
                statement.setString(1, placement.gateId()); statement.executeUpdate();
            }
            try (PreparedStatement statement = database.prepareStatement("INSERT OR REPLACE INTO stargate_aligned_horizons VALUES (?,?,?,?,?,?)")) {
                statement.setString(1, placement.gateId()); statement.setFloat(2, passage.x()); statement.setFloat(3, passage.y());
                statement.setFloat(4, passage.z()); statement.setFloat(5, passage.forwardX()); statement.setFloat(6, passage.forwardZ());
                statement.executeUpdate();
            }
            database.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            database.rollback(); throw ex;
        } finally { database.setAutoCommit(true); }
    }

    public synchronized void delete(String gateId) throws SQLException {
        if (!database.getAutoCommit()) throw new SQLException("Unexpected nested horizon transaction");
        database.setAutoCommit(false);
        try {
            for (String table : List.of("stargate_horizons", "stargate_aligned_horizons")) {
                try (PreparedStatement statement = database.prepareStatement("DELETE FROM " + table + " WHERE gate_id=?")) {
                    statement.setString(1, gateId); statement.executeUpdate();
                }
            }
            database.commit();
        } catch (SQLException ex) { database.rollback(); throw ex;
        } finally { database.setAutoCommit(true); }
    }

    public synchronized List<HorizonZone> all() throws SQLException {
        List<HorizonZone> result = new ArrayList<>();
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery("SELECT h.* FROM stargate_horizons h JOIN stargates g ON g.gate_id=h.gate_id WHERE NOT EXISTS (SELECT 1 FROM stargate_aligned_horizons a WHERE a.gate_id=h.gate_id)")) {
            while (rows.next()) result.add(new HorizonZone(rows.getString(1), rows.getFloat(2), rows.getFloat(3), rows.getFloat(4), rows.getFloat(5), rows.getFloat(6), rows.getFloat(7)));
        }
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery("SELECT h.* FROM stargate_aligned_horizons h JOIN stargates g ON g.gate_id=h.gate_id")) {
            while (rows.next()) result.add(new AlignedPassage(rows.getFloat(2), rows.getFloat(3), rows.getFloat(4), rows.getFloat(5), rows.getFloat(6)).zone(rows.getString(1)));
        }
        return List.copyOf(result);
    }
}
