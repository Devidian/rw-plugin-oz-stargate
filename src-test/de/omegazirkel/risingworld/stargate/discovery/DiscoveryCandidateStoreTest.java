package de.omegazirkel.risingworld.stargate.discovery;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import de.omegazirkel.risingworld.stargate.sector.SectorAddress;

public class DiscoveryCandidateStoreTest {
    @Test public void poolsMayShareSectorsAndOccupiedTargetsArePruned() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var statement = db.createStatement()) {
                statement.executeUpdate("CREATE TABLE stargates(gate_id TEXT PRIMARY KEY)");
                statement.executeUpdate("CREATE TABLE stargate_local_sectors(gate_id TEXT PRIMARY KEY,sector_x INTEGER,sector_z INTEGER)");
            }
            DiscoveryCandidateStore pool = new DiscoveryCandidateStore(db); pool.initialize();
            SectorAddress target = new SectorAddress(2, 1);
            DiscoveryCandidateStore.Chunk chunk = new DiscoveryCandidateStore.Chunk(640, 384);
            pool.add("source-a", 10, target, chunk);
            pool.add("source-b", 10, target, chunk);
            assertEquals(1, pool.chunks("source-a", target).size());
            assertEquals(1, pool.chunks("source-b", target).size());
            pool.prune("source-a", 9);
            assertTrue(pool.sectors("source-a").isEmpty());
            try (var statement = db.createStatement()) {
                statement.executeUpdate("INSERT INTO stargate_local_sectors VALUES('TARGET0000000001',2,1)");
            }
            pool.prune("source-b", 10);
            assertTrue(pool.sectors("source-b").isEmpty());
        }
    }

    @Test public void ringScanStartsInCentreAndNeverRepeatsFirst64Chunks() {
        assertArrayEquals(new int[] {0, 0}, DiscoveryPoolService.ringOffset(0));
        Set<String> positions = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            int[] offset = DiscoveryPoolService.ringOffset(i);
            assertTrue(Math.abs(offset[0]) <= 4);
            assertTrue(Math.abs(offset[1]) <= 4);
            assertTrue(positions.add(offset[0] + "," + offset[1]));
        }
    }
}
