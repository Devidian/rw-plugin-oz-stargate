package de.omegazirkel.risingworld.stargate.discovery;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.List;
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

    @Test public void emptyOriginPoolBorrowsOnlyFreeSitesWithinRadiusAndRefillsAfterCreation() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var statement = db.createStatement()) {
                statement.executeUpdate("CREATE TABLE stargates(gate_id TEXT PRIMARY KEY)");
                statement.executeUpdate("CREATE TABLE stargate_local_sectors(gate_id TEXT PRIMARY KEY,sector_x INTEGER,sector_z INTEGER)");
                statement.executeUpdate("INSERT INTO stargate_local_sectors VALUES('origin',0,0)");
                statement.executeUpdate("INSERT INTO stargate_local_sectors VALUES('occupied',1,0)");
            }
            DiscoveryCandidateStore pool = new DiscoveryCandidateStore(db); pool.initialize();
            pool.add("other", 10, new SectorAddress(2, 1), new DiscoveryCandidateStore.Chunk(640, 384));
            pool.add("other", 10, new SectorAddress(2, 1), new DiscoveryCandidateStore.Chunk(641, 384));
            pool.add("other", 10, new SectorAddress(3, 0), new DiscoveryCandidateStore.Chunk(896, 128));
            pool.add("other", 10, new SectorAddress(1, 0), new DiscoveryCandidateStore.Chunk(384, 128));
            pool.add("other", 10, new SectorAddress(11, 0), new DiscoveryCandidateStore.Chunk(2944, 128));

            pool.copyKnown("origin", new SectorAddress(0, 0), 10, 1, 5);
            assertEquals(List.of(new SectorAddress(2, 1)), pool.sectors("origin"));
            assertEquals(2, pool.chunks("origin", new SectorAddress(2, 1)).size());
            pool.removeSector(new SectorAddress(2, 1));
            pool.copyKnown("origin", new SectorAddress(0, 0), 10, 1, 5);
            assertEquals(List.of(new SectorAddress(3, 0)), pool.sectors("origin"));
        }
    }

    @Test public void rejectedSiteIsRemovedFromEverySourcePool() throws Exception {
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
            pool.removeChunk(chunk);
            assertTrue(pool.sectors("source-a").isEmpty());
            assertTrue(pool.sectors("source-b").isEmpty());
        }
    }

    @Test public void sectorChoiceExcludesOccupiedSectors() {
        SectorAddress origin = new SectorAddress(0, 0);
        Set<SectorAddress> occupied = new HashSet<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (x * x + z * z > 4 || x == 0 && z == 0) continue;
            occupied.add(new SectorAddress(x, z));
        }
        assertNull(DiscoveryPoolService.randomSector(origin, 2, List.of(), Set.of(), occupied));
        SectorAddress free = new SectorAddress(1, 0);
        occupied.remove(free);
        assertEquals(free, DiscoveryPoolService.randomSector(origin, 2, List.of(), Set.of(), occupied));
    }
}
