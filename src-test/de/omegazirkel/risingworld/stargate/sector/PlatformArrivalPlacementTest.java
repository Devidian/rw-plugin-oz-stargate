package de.omegazirkel.risingworld.stargate.sector;

import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;

import org.junit.Test;

import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.visual.PlatformType;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class PlatformArrivalPlacementTest {
    private static final Quaternion ROTATION = new Quaternion(0, 0, 0, 1);

    @Test public void raisesOnlyLegacyDefaultArrivalsAndIsIdempotent() throws Exception {
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LocalGateStore gates = new LocalGateStore(database); gates.initialize();
            LocalSectorStore sectors = new LocalSectorStore(database); sectors.initialize();
            GateVisualStore visuals = new GateVisualStore(database); visuals.initialize();
            save(gates, visuals, "round", 0, PlatformType.CIRCULAR, 0);
            save(gates, visuals, "grace", 20, PlatformType.GRACE_OF_GOD, 0);
            save(gates, visuals, "curved", 40, PlatformType.CURVED_MILKY_WAY, 0);
            save(gates, visuals, "custom", 60, PlatformType.CIRCULAR, 1);

            assertEquals(2, PlatformArrivalPlacement.migrate(gates, visuals));
            assertEquals(2.2f, gates.gate("round").position().y, .001f);
            assertEquals(3.2f, gates.gate("grace").position().y, .001f);
            assertEquals(2.666f, gates.gate("curved").position().y, .001f);
            assertEquals(1.888f, gates.gate("custom").position().y, .001f);
            assertEquals(0, PlatformArrivalPlacement.migrate(gates, visuals));
        }
    }

    private static void save(LocalGateStore gates, GateVisualStore visuals, String id, float x,
            PlatformType type, float customOffset) throws Exception {
        float feetY = type.deckHeightWorld();
        gates.save(id, new Vector3f(x, feetY + customOffset, -2.4f), ROTATION);
        visuals.save(new GateVisualPlacement(id, x, feetY - GateVisualPlacement.PLACEMENT_DEPTH,
                0, 0, 1, type));
    }
}
