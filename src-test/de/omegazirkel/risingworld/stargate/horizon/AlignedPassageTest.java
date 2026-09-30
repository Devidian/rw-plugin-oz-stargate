package de.omegazirkel.risingworld.stargate.horizon;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.Test;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.transfer.TransferStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdStore;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class AlignedPassageTest {
    private final GateVisualPlacement model = new GateVisualPlacement("A", 10,20,30, 0,1);
    private final Vector3f arrival = new Vector3f(10,22,26);
    private final Quaternion rotation = new Quaternion(0,1,0,0);
    private final HorizonZone legacy = new HorizonZone("A", 1,1,1, 2,3,2);

    @Test public void circularBodyCentreExcludesFrameCornersAndBehindArrival() {
        AlignedPassage passage = AlignedPassage.from(model);
        float centreFeet = passage.y()-AlignedPassage.BODY_CENTRE;
        assertTrue(passage.contains(10,centreFeet,30));
        assertTrue(passage.contains(10,centreFeet,30.59f));
        assertFalse(passage.contains(10,centreFeet,30.61f));
        assertFalse(passage.contains(14,centreFeet+4,30));
        assertTrue(passage.contains(10,20,30)); // body centre above the lowest rim
        assertFalse(passage.contains(10,19,30));
        assertTrue(passage.acceptsArrival(arrival.x,arrival.y,arrival.z));
        assertFalse(passage.acceptsArrival(10,22,34)); // rear
        assertFalse(passage.acceptsArrival(10,22,29)); // inside safety distance
        assertFalse(passage.acceptsArrival(10,22,21)); // too far
        assertFalse(passage.acceptsArrival(20,22,26)); // beside aperture
        assertFalse(passage.acceptsArrival(10,Float.NaN,26));
        assertFalse(passage.contains(Float.NaN,22,30));
    }

    @Test public void rotatedApertureUsesExactPlaneWithinConservativeBounds() {
        AlignedPassage passage = new AlignedPassage(0,6.15f,0,1,1);
        float feet=passage.y()-AlignedPassage.BODY_CENTRE;
        assertTrue(passage.contains(2,feet,-2));
        assertFalse(passage.contains(2,feet,2)); // inside AABB, outside rotated thin plane
        HorizonZone zone=passage.zone("A");
        assertTrue(2 <= zone.maxX() && 2 <= zone.maxZ());
        assertFalse(zone.contains(2,feet,2));
        assertTrue(passage.acceptsArrival(-3,feet,-3));
        assertFalse(passage.acceptsArrival(3,feet,3));
        assertTrue(zone.overlaps(new HorizonZone("B", -1,3,-1,1,7,1)));
        HorizonEntryTracker tracker=new HorizonEntryTracker();
        assertNull(tracker.move(List.of(zone),0,feet,0)); // editing/arrival baseline
        assertNull(tracker.move(List.of(zone),3,feet,3));
        assertEquals("A",tracker.move(List.of(zone),0,feet,0));
        assertNull(tracker.move(List.of(zone),0,feet,0));
        assertNull(tracker.move(List.of(zone),-3,feet,-3));
        assertEquals("A",tracker.move(List.of(zone),0,feet,0)); // decline then re-enter
    }

    private void initialize(Connection db) throws Exception {
        LocalGateStore gates=new LocalGateStore(db);gates.initialize();
        gates.save("A", new Vector3f(1,2,3),new Quaternion(0,0,0,1));
        HorizonStore horizons=new HorizonStore(db);horizons.initialize();horizons.save(legacy);
        GateVisualStore visuals=new GateVisualStore(db);visuals.initialize();visuals.save(model);
        new TransferStore(db).initialize();
        DhdStore consoles=new DhdStore(db);consoles.initialize();
        consoles.bind(new DhdStore.Binding(new DhdStore.ObjectKey(1,0,0,0),1,(short)1,"A"));
    }

    @Test public void alignmentSurvivesReopenAndRemovalUnlocksLegacyZone() throws Exception {
        Path file=Files.createTempFile("stargate-alignment-", ".db");
        try {
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                initialize(db);
                assertTrue(new HorizonStore(db).align(model,arrival,rotation));
                assertFalse(new HorizonStore(db).save(legacy));
            }
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+file)) {
                HorizonStore store=new HorizonStore(db);store.initialize();
                assertEquals(List.of(AlignedPassage.from(model).zone("A")),store.all());
                LocalGateStore.Gate gate=new LocalGateStore(db).gate("A");
                assertEquals(22f,gate.position().y,0); assertEquals(26f,gate.position().z,0);
                assertEquals(1f,gate.rotation().y,0); assertEquals(0f,gate.rotation().w,0);
                assertNotNull(new DhdStore(db).find(new DhdStore.ObjectKey(1,0,0,0)));
                assertEquals(List.of(model),new GateVisualStore(db).all());
                try(var s=db.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM stargate_horizons")) {
                    assertTrue(rows.next());assertEquals(0,rows.getInt(1));
                }
                store.delete("A");assertTrue(store.all().isEmpty());assertTrue(store.save(legacy));
                assertTrue(store.align(model,arrival,rotation));
                new LocalGateStore(db).delete("A");assertTrue(store.all().isEmpty());
                try(var s=db.createStatement();var rows=s.executeQuery("SELECT COUNT(*) FROM stargate_aligned_horizons")) {
                    assertTrue(rows.next());assertEquals(0,rows.getInt(1));
                }
            }
        } finally {Files.deleteIfExists(file);}
    }

    @Test public void arbitraryDirectionRoundtripStillAligns() throws Exception {
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            initialize(db);
            GateVisualStore visuals=new GateVisualStore(db);
            visuals.save(new GateVisualPlacement("A",10,20,30,0.1234567f,-0.9876543f));
            GateVisualPlacement loaded=visuals.all().get(0);
            Vector3f front=new Vector3f(loaded.x()-loaded.forwardX()*4,22,loaded.z()-loaded.forwardZ()*4);
            assertTrue(new HorizonStore(db).align(loaded,front,rotation));
        }
    }

    @Test public void sqlFailureRollsBackArrivalAndLegacyZone() throws Exception {
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            initialize(db);
            try(var s=db.createStatement()) {s.executeUpdate("CREATE TRIGGER reject_alignment BEFORE INSERT ON stargate_aligned_horizons BEGIN SELECT RAISE(ABORT,'test failure'); END");}
            HorizonStore store=new HorizonStore(db);
            assertThrows(SQLException.class,()->store.align(model,arrival,rotation));
            assertEquals(List.of(legacy),store.all());
            assertEquals(2f,new LocalGateStore(db).gate("A").position().y,0);
            assertTrue(db.getAutoCommit());
        }
    }

    @Test public void missingChangedModelsAndActiveTransfersCannotChangeArrival() throws Exception {
        try(Connection db=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            initialize(db);
            HorizonStore store=new HorizonStore(db);
            TransferStore journal=new TransferStore(db);
            journal.insert(new TransferStore.Transfer("T","U","IN","WAITING","A","B","{}",Long.MAX_VALUE));
            assertFalse(store.align(model,arrival,rotation));
            journal.transition("T","WAITING","DONE");
            new GateVisualStore(db).save(new GateVisualPlacement("A",11,20,30,0,1));
            assertFalse(store.align(model,arrival,rotation));
            new GateVisualStore(db).delete("A");assertFalse(store.align(model,arrival,rotation));
            assertEquals(List.of(legacy),store.all());
            assertEquals(2f,new LocalGateStore(db).gate("A").position().y,0);
            new LocalGateStore(db).delete("A");assertFalse(store.align(model,arrival,rotation));
        }
    }
}
