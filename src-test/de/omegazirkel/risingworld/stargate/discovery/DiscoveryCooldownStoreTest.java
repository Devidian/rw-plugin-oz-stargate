package de.omegazirkel.risingworld.stargate.discovery;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.Test;

public class DiscoveryCooldownStoreTest {
    @Test public void cooldownPersistsAcrossStoreReopen() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            DiscoveryCooldownStore first = new DiscoveryCooldownStore(db);
            first.initialize();
            first.started("uid-a", 1234567L);
            DiscoveryCooldownStore second = new DiscoveryCooldownStore(db);
            second.initialize();
            assertEquals(1234567L, second.nextAt("uid-a"));
            assertEquals(0L, second.nextAt("uid-b"));
        }
    }
}
