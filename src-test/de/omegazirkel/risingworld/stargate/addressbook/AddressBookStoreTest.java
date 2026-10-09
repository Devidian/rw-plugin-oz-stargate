package de.omegazirkel.risingworld.stargate.addressbook;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.Test;

import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class AddressBookStoreTest {
    @Test public void emptyExistingBooksStayEmptyAndSyncIsScoped() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(db); gates.initialize();
            AddressBookStore book = new AddressBookStore(db); book.initialize();
            assertEquals(List.of(), book.known("NET", "a"));
            assertTrue(book.learn("NET", "a", "AAAAAAAAAAAAAAAA"));
            assertFalse(book.learn("NET", "a", "AAAAAAAAAAAAAAAA"));
            assertEquals(List.of("AAAAAAAAAAAAAAAA"), book.pending("NET", "a"));
            assertEquals(List.of(), book.known("NET", "b"));
            assertEquals(List.of(), book.known("OTHER", "a"));
            book.learn("UNASSIGNED", "a", "CCCCCCCCCCCCCCCC");
            book.movePending("UNASSIGNED", "NET", "a");
            assertEquals(List.of(), book.known("UNASSIGNED", "a"));
            assertEquals(List.of("AAAAAAAAAAAAAAAA", "CCCCCCCCCCCCCCCC"), book.pending("NET", "a"));
            book.replace("NET", "a", List.of("AAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBBBB"));
            assertEquals(List.of(), book.pending("NET", "a"));
            assertEquals(List.of("AAAAAAAAAAAAAAAA", "BBBBBBBBBBBBBBBB"), book.known("NET", "a"));
            book.replace("NET", "a", List.of("BBBBBBBBBBBBBBBB"));
            assertEquals(List.of("BBBBBBBBBBBBBBBB"), book.known("NET", "a"));
            book.setGateDetails("NET", "BBBBBBBBBBBBBBBB", "1234567890ABCDEF", "LOCAL00000000001", "Beta");
            assertEquals("1234567890ABCDEF", book.address("NET", "BBBBBBBBBBBBBBBB"));
            assertEquals("Beta", book.alias("NET", "BBBBBBBBBBBBBBBB"));
            assertEquals("Beta", book.aliasByAddress("NET", "1234567890ABCDEF"));
            assertEquals("LOCAL00000000001", book.localByAddress("NET", "1234567890ABCDEF"));
            book.remove("NET", "BBBBBBBBBBBBBBBB");
            assertNull(book.address("NET", "BBBBBBBBBBBBBBBB"));
        }
    }

    @Test public void localGateDeletionClearsEveryCachedCopy() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(db); gates.initialize();
            gates.save("LOCAL00000000001", new Vector3f(1, 2, 3), new Quaternion(0, 0, 0, 1));
            AddressBookStore book = new AddressBookStore(db); book.initialize();
            book.learn("LOCAL", "a", "LOCAL00000000001");
            book.learn("LOCAL", "b", "LOCAL00000000001");
            gates.delete("LOCAL00000000001");
            assertEquals(List.of(), book.known("LOCAL", "a"));
            assertEquals(List.of(), book.known("LOCAL", "b"));
        }
    }

    @Test public void codeChangeMovesOnlyLocalGateEntriesAndPreservesNamesAndShares() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(db); gates.initialize();
            String local = "LOCAL00000000001", remote = "AAAAAAAAAAAAAAAA";
            gates.save(local, new Vector3f(1, 2, 3), new Quaternion(0, 0, 0, 1));
            AddressBookStore book = new AddressBookStore(db); book.initialize();
            book.learn("OLD", "alice", local);
            book.learn("OLD", "alice", remote);
            book.replace("OLD", "alice", List.of(local, remote));
            book.setGateDetails("OLD", local, "BBBBBBBBBBBBBBBB", local, "Gate");
            book.setPersonalName("alice", local, "Home");
            book.setShare(42, "alice", 7, local, true);
            book.migrateLocalGates("OLD", "NEW", List.of(local));
            book.migrateLocalGates("OLD", "NEW", List.of(local));
            assertEquals(List.of(local), book.known("NEW", "alice"));
            assertEquals(List.of(local), book.pending("NEW", "alice"));
            assertEquals(List.of(remote), book.known("OLD", "alice"));
            book.replace("NEW", "alice", List.of());
            assertEquals(List.of(local), book.known("NEW", "alice"));
            book.replace("NEW", "alice", List.of(local));
            assertEquals(List.of(), book.pending("NEW", "alice"));
            assertEquals(List.of(local), book.knownLocal("alice"));
            assertEquals("BBBBBBBBBBBBBBBB", book.address("NEW", local));
            assertEquals("Home", book.personalName("alice", local));
            assertTrue(book.shared(42, 7, local));
            assertFalse(book.shared(42, 8, local));
        }
    }

    @Test public void currentNetworkSnapshotDropsStaleRemoteGatesButKeepsLocalGates() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(db); gates.initialize();
            String local = "LOCAL00000000001", staleRemote = "AAAAAAAAAAAAAAAA";
            gates.save(local, new Vector3f(1, 2, 3), new Quaternion(0, 0, 0, 1));
            AddressBookStore book = new AddressBookStore(db); book.initialize();
            book.learn("NET", "alice", local);
            book.learn("NET", "alice", staleRemote);
            book.replace("NET", "alice", List.of(local, staleRemote));
            book.replace("NET", "alice", List.of());
            assertEquals(List.of(local), book.known("NET", "alice"));
            assertEquals(List.of(), book.pending("NET", "alice"));
        }
    }

    @Test public void allShareKeepsIndividualChoiceAfterSwitchingOff() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new LocalGateStore(db).initialize();
            AddressBookStore book = new AddressBookStore(db); book.initialize();
            String gate = "LOCAL00000000001";
            book.setShare(42, "alice", 7, gate, true);
            book.setAllShared(42, "alice", 7, true);
            assertTrue(book.shared(42, 7, gate));
            assertTrue(book.allShared(42, 7));
            book.setAllShared(42, "alice", 7, false);
            assertTrue(book.shared(42, 7, gate));
            assertFalse(book.shared(42, 8, gate));
        }
    }
}
