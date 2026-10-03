package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.stargate.sector.SectorAddress;

/** Persisted, per-source pool of remote sectors and suitable chunks. */
public final class DiscoveryCandidateStore {
    public record Chunk(int x, int z) { }
    private final Connection database;

    public DiscoveryCandidateStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_discovery_candidates "
                    + "(source_gate_id TEXT NOT NULL, radius INTEGER NOT NULL, sector_x INTEGER NOT NULL, "
                    + "sector_z INTEGER NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, "
                    + "PRIMARY KEY(source_gate_id,chunk_x,chunk_z))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS stargate_discovery_candidates_sector "
                    + "ON stargate_discovery_candidates(source_gate_id,sector_x,sector_z)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_discovery_source_deleted "
                    + "AFTER DELETE ON stargates BEGIN DELETE FROM stargate_discovery_candidates "
                    + "WHERE source_gate_id=OLD.gate_id; END");
        }
    }

    public synchronized void prune(String sourceId, int radius) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM stargate_discovery_candidates WHERE source_gate_id=? AND radius<>?")) {
            statement.setString(1, sourceId); statement.setInt(2, radius); statement.executeUpdate();
        }
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("DELETE FROM stargate_discovery_candidates WHERE EXISTS "
                    + "(SELECT 1 FROM stargate_local_sectors s WHERE "
                    + "s.sector_x=stargate_discovery_candidates.sector_x "
                    + "AND s.sector_z=stargate_discovery_candidates.sector_z)");
        }
    }

    public synchronized List<SectorAddress> sectors(String sourceId) throws SQLException {
        List<SectorAddress> result = new ArrayList<>();
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT DISTINCT sector_x,sector_z FROM stargate_discovery_candidates "
                        + "WHERE source_gate_id=? ORDER BY sector_x,sector_z")) {
            statement.setString(1, sourceId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new SectorAddress(rows.getInt(1), rows.getInt(2)));
            }
        }
        return List.copyOf(result);
    }

    public synchronized List<Chunk> chunks(String sourceId, SectorAddress sector) throws SQLException {
        List<Chunk> result = new ArrayList<>();
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT chunk_x,chunk_z FROM stargate_discovery_candidates "
                        + "WHERE source_gate_id=? AND sector_x=? AND sector_z=? ORDER BY chunk_x,chunk_z")) {
            statement.setString(1, sourceId);
            statement.setInt(2, sector.x()); statement.setInt(3, sector.z());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new Chunk(rows.getInt(1), rows.getInt(2)));
            }
        }
        return List.copyOf(result);
    }

    public synchronized void add(String sourceId, int radius, SectorAddress sector, Chunk chunk) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT OR IGNORE INTO stargate_discovery_candidates "
                        + "(source_gate_id,radius,sector_x,sector_z,chunk_x,chunk_z) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, sourceId); statement.setInt(2, radius);
            statement.setInt(3, sector.x()); statement.setInt(4, sector.z());
            statement.setInt(5, chunk.x()); statement.setInt(6, chunk.z()); statement.executeUpdate();
        }
    }

    public synchronized void removeSector(SectorAddress sector) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM stargate_discovery_candidates WHERE sector_x=? AND sector_z=?")) {
            statement.setInt(1, sector.x()); statement.setInt(2, sector.z()); statement.executeUpdate();
        }
    }

    public synchronized void removeChunk(String sourceId, Chunk chunk) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "DELETE FROM stargate_discovery_candidates WHERE source_gate_id=? AND chunk_x=? AND chunk_z=?")) {
            statement.setString(1, sourceId); statement.setInt(2, chunk.x()); statement.setInt(3, chunk.z());
            statement.executeUpdate();
        }
    }
}
