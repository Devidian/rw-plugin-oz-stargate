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
}
