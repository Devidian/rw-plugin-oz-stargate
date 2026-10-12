package de.omegazirkel.risingworld.stargate.sector;

import java.sql.SQLException;

import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.visual.PlatformType;
import net.risingworld.api.utils.Vector3f;

/** Arrival feet on the platform surface in front of the gate. */
public final class PlatformArrivalPlacement {
    private static final float ARRIVAL_DISTANCE = 2.4f;
    private static final float POSITION_TOLERANCE = .05f;

    private PlatformArrivalPlacement() { }

    public static Vector3f position(GateVisualPlacement placement) {
        PlatformType platform = placement.platform();
        if (platform == null) throw new IllegalArgumentException("Platform required");
        float groundY = placement.y() + GateVisualPlacement.PLACEMENT_DEPTH - platform.deckHeightWorld();
        return new Vector3f(placement.x() - placement.forwardX() * ARRIVAL_DISTANCE,
                groundY + platform.arrivalHeightWorld(),
                placement.z() - placement.forwardZ() * ARRIVAL_DISTANCE);
    }

    /** Raise only legacy auto-generated arrivals; preserve manually aligned positions. */
    public static int migrate(LocalGateStore gates, GateVisualStore visuals) throws SQLException {
        int changed = 0;
        for (GateVisualPlacement placement : visuals.all()) {
            PlatformType platform = placement.platform();
            if (platform == null || Math.abs(platform.arrivalHeightWorld() - platform.deckHeightWorld()) < .001f)
                continue;
            LocalGateStore.Gate gate = gates.gate(placement.gateId());
            if (gate == null) continue;
            Vector3f old = gate.position();
            Vector3f corrected = position(placement);
            float oldY = placement.y() + GateVisualPlacement.PLACEMENT_DEPTH;
            if (Math.abs(old.x - corrected.x) > POSITION_TOLERANCE
                    || Math.abs(old.y - oldY) > POSITION_TOLERANCE
                    || Math.abs(old.z - corrected.z) > POSITION_TOLERANCE) continue;
            if (!gates.move(gate.id(), corrected, gate.rotation()))
                throw new SQLException("Cannot raise platform arrival for " + gate.id());
            changed++;
        }
        return changed;
    }
}
