package de.omegazirkel.risingworld.stargate.sector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

/** Mandatory one-gate-per-sector index, committed with local gate ownership. */
public final class LocalSectorStore {
    public record Entry(String gateId, SectorAddress address) { }
    @FunctionalInterface public interface Setup { void save() throws SQLException; }

    private final Connection database;
    public LocalSectorStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_local_sectors (gate_id TEXT PRIMARY KEY, sector_x INTEGER NOT NULL, sector_z INTEGER NOT NULL, UNIQUE(sector_x,sector_z))");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_local_sector_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_local_sectors WHERE gate_id=OLD.gate_id; END");
        }
    }

    /** Existing worlds are migrated as one transaction; no arbitrary winner for a conflict. */
    public synchronized void migrateExisting(LocalGateStore gates) throws SQLException {
        migrateExisting(gates, SectorAddress::fromWorld);
    }

    synchronized void migrateExisting(LocalGateStore gates, Function<Vector3f, SectorAddress> addressOf) throws SQLException {
        if (!database.getAutoCommit()) throw new SQLException("Unexpected nested sector migration");
        database.setAutoCommit(false);
        try {
            for (String id : gates.ids()) {
                LocalGateStore.Gate gate = gates.gate(id);
                SectorAddress expected = addressOf.apply(gate.position());
                SectorAddress recorded = addressOf(id);
                if (recorded == null) insert(id, expected);
                else if (!recorded.equals(expected)) throw new SQLException("Gate " + id + " moved to another sector; recorded " + recorded + ", current " + expected);
            }
            database.commit();
        } catch (SQLException | RuntimeException ex) {
            database.rollback(); throw ex;
        } finally { database.setAutoCommit(true); }
    }

    /** Relay ID is already reserved; both world-local records must commit together. */
    public synchronized void saveGate(LocalGateStore gates, String gateId, Vector3f position,
            Quaternion rotation) throws SQLException {
        saveGate(gates, gateId, position, rotation, SectorAddress.fromWorld(position));
    }

    synchronized void saveGate(LocalGateStore gates, String gateId, Vector3f position,
            Quaternion rotation, SectorAddress address) throws SQLException {
        saveGateWithSetup(gates, gateId, position, rotation, address, () -> { });
    }

    /** Gate identity, sector ownership and optional setup share one SQLite commit. */
    public synchronized void saveGateWithSetup(LocalGateStore gates, String gateId, Vector3f arrival,
            Quaternion rotation, SectorAddress address, Setup setup) throws SQLException {
        if (!database.getAutoCommit()) throw new SQLException("Unexpected nested gate registration");
        database.setAutoCommit(false);
        try {
            gates.save(gateId, arrival, rotation);
            insert(gateId, address);
            setup.save();
            database.commit();
        } catch (SQLException | RuntimeException ex) {
            database.rollback(); throw ex;
        } finally { database.setAutoCommit(true); }
    }

    /** Keep sector ownership and gate ID while moving all gate-owned transforms together. */
    public synchronized boolean moveGateWithSetup(LocalGateStore gates, String gateId, Vector3f arrival,
            Quaternion rotation, SectorAddress address, Setup setup) throws SQLException {
        if (!database.getAutoCommit()) throw new SQLException("Unexpected nested gate move");
        database.setAutoCommit(false);
        try {
            if (!address.equals(addressOf(gateId)) || hasActiveTransfer(gateId)
                    || !gates.move(gateId, arrival, rotation)) {
                database.rollback(); return false;
            }
            setup.save();
            database.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            database.rollback(); throw ex;
        } finally { database.setAutoCommit(true); }
    }

    public synchronized boolean hasActiveTransfer(String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT 1 FROM stargate_transfers WHERE gate_id=? AND state NOT IN ('DONE','CANCELLED') LIMIT 1")) {
            statement.setString(1, gateId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private void insert(String gateId, SectorAddress address) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT INTO stargate_local_sectors (gate_id,sector_x,sector_z) VALUES (?,?,?)")) {
            statement.setString(1, gateId);
            statement.setInt(2, address.x()); statement.setInt(3, address.z());
            statement.executeUpdate();
        }
    }

    public synchronized SectorAddress addressOf(String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT sector_x,sector_z FROM stargate_local_sectors WHERE gate_id=?")) {
            statement.setString(1, gateId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new SectorAddress(rows.getInt(1), rows.getInt(2)) : null;
            }
        }
    }

    public synchronized String gateAt(SectorAddress address) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT gate_id FROM stargate_local_sectors WHERE sector_x=? AND sector_z=?")) {
            statement.setInt(1, address.x()); statement.setInt(2, address.z());
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized boolean remove(String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM stargate_local_sectors WHERE gate_id=?")) {
            statement.setString(1, gateId); return statement.executeUpdate() == 1;
        }
    }

    public synchronized List<Entry> all() throws SQLException {
        List<Entry> entries = new ArrayList<>();
        try (Statement statement = database.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT l.gate_id,l.sector_x,l.sector_z FROM stargate_local_sectors l JOIN stargates g ON g.gate_id=l.gate_id ORDER BY l.sector_x,l.sector_z")) {
            while (rows.next()) entries.add(new Entry(rows.getString(1), new SectorAddress(rows.getInt(2), rows.getInt(3))));
        }
        return List.copyOf(entries);
    }
}
