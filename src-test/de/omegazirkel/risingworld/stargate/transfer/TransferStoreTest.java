package de.omegazirkel.risingworld.stargate.transfer;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.Test;

public class TransferStoreTest {
    @Test public void interruptedTransferAndTargetInventorySurviveReopen() throws Exception {
        Path path = Files.createTempFile("stargate-transfer-test", ".db");
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + path)) {
                TransferStore store = new TransferStore(db);
                store.initialize();
                assertTrue(store.insert(new TransferStore.Transfer("travel-1", "player-1", "OUT", "PREPARED",
                        "gate-a", "gate-b", "{original-inventory}", 12345)));
                assertFalse(store.insert(new TransferStore.Transfer("travel-1", "player-1", "OUT", "PREPARED",
                        "gate-a", "gate-b", "{replacement}", 12345)));
                assertTrue(store.saveBase("player-1", new byte[]{1, 2}, new byte[]{3, 4}));
                assertFalse(store.saveBase("player-1", new byte[]{9}, new byte[]{9}));
                assertTrue(store.transition("travel-1", "PREPARED", "CLEARING"));
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + path)) {
                TransferStore store = new TransferStore(db);
                store.initialize();
                assertEquals("CLEARING", store.byId("travel-1").state());
                assertEquals("{original-inventory}", store.byId("travel-1").data());
                assertArrayEquals(new byte[]{1, 2}, store.base("player-1").inventory());
                assertArrayEquals(new byte[]{3, 4}, store.base("player-1").clothes());
                assertEquals(1, store.active().size());
                assertFalse(store.transition("travel-1", "PREPARED", "CLEARED"));
            }
        } finally { Files.deleteIfExists(path); }
    }

    @Test public void completionMarksReturnInventoryAtomicallyAndRestorationIsSingleUse() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            TransferStore store = new TransferStore(db);
            store.initialize();
            store.insert(new TransferStore.Transfer("travel-2", "player-2", "OUT", "DEPARTING", "a", "b", "{}", 0));
            store.saveBase("player-2", new byte[]{8}, new byte[]{7});
            TransferStore.Transfer before = store.byId("travel-2");
            assertTrue(store.completeOutgoing(before));
            assertFalse(store.completeOutgoing(before));
            assertEquals("DONE", store.byId("travel-2").state());
            assertEquals("RESTORE_PENDING", store.base("player-2").state());
            assertTrue(store.consumeBase("player-2", "RESTORE_PENDING"));
            assertFalse(store.consumeBase("player-2", "RESTORE_PENDING"));
            assertTrue(store.active().isEmpty());
        }
    }

    @Test public void emptyTargetCanCompleteVisitWithoutCreatingAVisitorBase() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            TransferStore store = new TransferStore(db);
            store.initialize();
            store.insert(new TransferStore.Transfer("arrival", "player-empty", "IN", "PREPARED", "b", "a", "{}", 0));
            assertTrue(store.transition("arrival", "PREPARED", "APPLYING"));
            assertTrue(store.transition("arrival", "APPLYING", "APPLIED"));
            assertTrue(store.transition("arrival", "APPLIED", "DONE"));
            assertNull(store.base("player-empty"));

            store.insert(new TransferStore.Transfer("departure", "player-empty", "OUT", "DEPARTING", "b", "a", "{}", 0));
            assertTrue(store.completeOutgoing(store.byId("departure")));
            assertNull(store.base("player-empty"));
            assertTrue(store.active().isEmpty());
        }
    }

    @Test public void arrivalVisibilityRecoverySurvivesReopenAndClearsOnlyItsTransfer() throws Exception {
        Path path = Files.createTempFile("stargate-arrival-test", ".db");
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + path)) {
                TransferStore store = new TransferStore(db);
                store.initialize();
                store.markArrivalPending("player", "arrival-1");
                assertEquals(1_000L, store.arrivalScreenStart("player", "arrival-1", 1_000L));
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + path)) {
                TransferStore store = new TransferStore(db);
                store.initialize();
                assertEquals("arrival-1", store.pendingArrival("player"));
                assertEquals(1_000L, store.arrivalScreenStart("player", "arrival-1", 9_000L));
                store.clearPendingArrival("player", "other-arrival");
                assertEquals("arrival-1", store.pendingArrival("player"));
                store.clearPendingArrival("player", "arrival-1");
                assertNull(store.pendingArrival("player"));
                store.clearArrivalScreen("player", "arrival-1");
                assertEquals(9_000L, store.arrivalScreenStart("player", "arrival-1", 9_000L));
            }
        } finally { Files.deleteIfExists(path); }
    }
}
