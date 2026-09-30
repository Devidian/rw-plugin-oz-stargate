package de.omegazirkel.risingworld.stargate.sector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.Test;

import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.horizon.HorizonStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelPlacement;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class LocalSectorStoreTest {
    private static final Quaternion ROTATION = new Quaternion(0, 0, 0, 1);

    @Test public void negativeChunksUseFloorDivision() {
        assertEquals(new SectorAddress(-1, -2), SectorAddress.fromChunk(-1, -257));
        assertEquals(new SectorAddress(0, 1), SectorAddress.fromChunk(255, 256));
    }

    @Test public void registrationIsAtomicAndGateDeletionCleansSector() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(database);
            gates.initialize();
            LocalSectorStore sectors = new LocalSectorStore(database);
            sectors.initialize();
            Vector3f position = new Vector3f(1, 2, 3);
            SectorAddress address = new SectorAddress(0, 0);
            sectors.saveGate(gates, "A", position, ROTATION, address);
            assertEquals("A", sectors.gateAt(address));
            assertEquals(List.of(new LocalSectorStore.Entry("A", address)), sectors.all());
            try { sectors.saveGate(gates, "B", new Vector3f(4, 2, 5), ROTATION, address); fail("same sector accepted"); }
            catch (SQLException expected) { assertNull(gates.gate("B")); }
            gates.delete("A");
            assertNull(sectors.addressOf("A"));
            sectors.saveGate(gates, "B", position, ROTATION, address);
            assertEquals("B", sectors.gateAt(address));
        }
    }

    @Test public void legacyConflictRollsBackEntireMigration() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(database);
            gates.initialize();
            LocalSectorStore sectors = new LocalSectorStore(database);
            sectors.initialize();
            gates.save("A", new Vector3f(1, 2, 3), ROTATION);
            gates.save("B", new Vector3f(4, 2, 5), ROTATION);
            try { sectors.migrateExisting(gates, position -> new SectorAddress(0, 0)); fail("legacy conflict accepted"); }
            catch (SQLException expected) { assertEquals(List.of(), sectors.all()); }
            gates.delete("B");
            sectors.migrateExisting(gates, position -> new SectorAddress(0, 0));
            assertEquals(1, sectors.all().size());
            sectors.migrateExisting(gates, position -> new SectorAddress(0, 0));
            assertEquals(1, sectors.all().size());
        }
    }

    @Test public void firstPlacementCommitsGateModelAndPassageTogether() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(database); gates.initialize();
            LocalSectorStore sectors = new LocalSectorStore(database); sectors.initialize();
            GateVisualStore models = new GateVisualStore(database); models.initialize();
            HorizonStore horizons = new HorizonStore(database); horizons.initialize();
            GateVisualPlacement model = new GateVisualPlacement("A", 10, 17.6f, 30, 0, 1);
            Vector3f arrival = new Vector3f(10, 20, 27.6f);
            SectorAddress address = new SectorAddress(1, 2);
            try {
                sectors.saveGateWithSetup(gates, "A", arrival, ROTATION, address, () -> {
                    models.save(model);
                    horizons.saveNewAligned(model);
                    throw new SQLException("simulated setup failure");
                });
                fail("partial first placement accepted");
            } catch (SQLException expected) {
                assertNull(gates.gate("A"));
                assertNull(sectors.gateAt(address));
                assertEquals(List.of(), models.all());
                assertEquals(List.of(), horizons.all());
            }
            sectors.saveGateWithSetup(gates, "A", arrival, ROTATION, address, () -> {
                models.save(model);
                horizons.saveNewAligned(model);
            });
            assertEquals(arrival.z, gates.gate("A").position().z, 0.001f);
            assertEquals(List.of(model), models.all());
            assertEquals(model.y() + 6.15f, horizons.all().get(0).aligned().y(), 0.001f);
            assertEquals("A", sectors.gateAt(address));
        }
    }

    @Test public void movingGateAndDhdPreservesIdentityAndRollsBackTogether() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(database); gates.initialize();
            LocalSectorStore sectors = new LocalSectorStore(database); sectors.initialize();
            GateVisualStore models = new GateVisualStore(database); models.initialize();
            HorizonStore horizons = new HorizonStore(database); horizons.initialize();
            DhdModelStore dhds = new DhdModelStore(database); dhds.initialize();
            // The move guard queries this journal even if no transfer has yet happened.
            try (var statement = database.createStatement()) {
                statement.executeUpdate("CREATE TABLE stargate_transfers (gate_id TEXT, state TEXT)");
            }
            SectorAddress sector = new SectorAddress(0, 0);
            Vector3f firstArrival = new Vector3f(10, 20, 27.6f);
            GateVisualPlacement first = new GateVisualPlacement("A", 10, 17.6f, 30, 0, 1);
            DhdModelPlacement firstDhd = new DhdModelPlacement("A", 14, 20, 14, 0, 1);
            sectors.saveGateWithSetup(gates, "A", firstArrival, ROTATION, sector, () -> {
                models.save(first); horizons.saveNewAligned(first); dhds.save(firstDhd);
            });
            Vector3f nextArrival = new Vector3f(30, 25, 47.6f);
            GateVisualPlacement next = new GateVisualPlacement("A", 30, 22.6f, 50, 0, 1);
            DhdModelPlacement nextDhd = new DhdModelPlacement("A", 34, 25, 34, 0, 1);
            try {
                sectors.moveGateWithSetup(gates, "A", nextArrival, ROTATION, sector, () -> {
                    models.save(next); horizons.replaceAligned(next); dhds.save(nextDhd);
                    throw new SQLException("simulated DHD failure");
                });
                fail("partial move accepted");
            } catch (SQLException expected) {
                assertEquals(firstArrival.z, gates.gate("A").position().z, .001f);
                assertEquals(List.of(first), models.all());
                assertEquals(List.of(firstDhd), dhds.all());
            }
            assertEquals(true, sectors.moveGateWithSetup(gates, "A", nextArrival, ROTATION, sector, () -> {
                models.save(next); horizons.replaceAligned(next); dhds.save(nextDhd);
            }));
            assertEquals("A", sectors.gateAt(sector));
            assertEquals(nextArrival.z, gates.gate("A").position().z, .001f);
            assertEquals(List.of(next), models.all());
            assertEquals(List.of(nextDhd), dhds.all());
            assertEquals(next.y() + 6.15f, horizons.all().get(0).aligned().y(), .001f);
            try (var statement = database.createStatement()) {
                statement.executeUpdate("INSERT INTO stargate_transfers VALUES ('A','PREPARED')");
            }
            assertEquals(true, sectors.hasActiveTransfer("A"));
            assertEquals(false, sectors.moveGateWithSetup(gates, "A", firstArrival, ROTATION, sector,
                    () -> fail("busy gate moved")));
            try (var statement = database.createStatement()) {
                statement.executeUpdate("DELETE FROM stargate_transfers");
            }
            gates.delete("A");
            assertEquals(List.of(), sectors.all());
            assertEquals(List.of(), models.all());
            assertEquals(List.of(), horizons.all());
            assertEquals(List.of(), dhds.all());
        }
    }
}
