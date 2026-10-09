package de.omegazirkel.risingworld.stargate.horizon;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import org.junit.Test;

public class LocalTravelVisibilityTest {
    @Test public void unfinishedLocalTravelSurvivesReopenAndKeepsOriginalVisibility() throws Exception {
        Path file = Files.createTempFile("stargate-local-visibility", ".db");
        try {
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                new LocalGateStore(db).initialize();
                HorizonStore store = new HorizonStore(db);
                store.initialize();
                assertTrue(store.beginLocalTravel("visible-player", false));
                assertFalse(store.beginLocalTravel("visible-player", true));
                assertTrue(store.beginLocalTravel("hidden-player", true));
            }
            try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file)) {
                HorizonStore store = new HorizonStore(db);
                store.initialize();
                assertEquals(Boolean.FALSE, store.pendingLocalVisibility("visible-player"));
                assertEquals(Boolean.TRUE, store.pendingLocalVisibility("hidden-player"));
                store.clearLocalTravel("visible-player");
                assertNull(store.pendingLocalVisibility("visible-player"));
                assertTrue(store.beginLocalTravel("visible-player", false));
            }
        } finally { Files.deleteIfExists(file); }
    }
}
