package de.omegazirkel.risingworld.stargate.arrival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.Test;

public class FirstArrivalStoreTest {
    @Test public void transferDoesNotConsumeFirstRegularVisit() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            FirstArrivalStore visits = new FirstArrivalStore(db);
            visits.initialize();
            assertNull(visits.visit("new"));
            visits.pending("new", false);
            assertEquals("PENDING", visits.visit("new").state());
            visits.arm("new", true);
            assertEquals("ARRIVING", visits.visit("new").state());
            assertTrue(visits.visit("new").wasInvisible());
            visits.arm("new", false);
            assertTrue(visits.visit("new").wasInvisible());
            visits.done("new");
            assertEquals("DONE", visits.visit("new").state());
            visits.arm("new", false);
            assertEquals("DONE", visits.visit("new").state());
        }
    }

    @Test public void regularFirstVisitSurvivesReconnectAndReload() throws Exception {
        String url = "jdbc:sqlite:file:first-arrival-test?mode=memory&cache=shared";
        try (Connection db = DriverManager.getConnection(url); Connection second = DriverManager.getConnection(url)) {
            FirstArrivalStore first = new FirstArrivalStore(db);
            first.initialize();
            first.arm("uid", false);
            FirstArrivalStore reopened = new FirstArrivalStore(second);
            reopened.initialize();
            assertEquals("ARRIVING", reopened.visit("uid").state());
            assertFalse(reopened.visit("uid").wasInvisible());
            reopened.done("uid");
            assertEquals("DONE", first.visit("uid").state());
        }
    }
}
