package de.omegazirkel.risingworld.stargate.horizon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** First observation establishes a baseline; entry stays latched until the player leaves. */
public final class HorizonEntryTracker {
    private Set<String> inside = Set.of();
    private boolean initialized;

    public String move(List<HorizonZone> zones, float x, float y, float z) {
        Set<String> current = new HashSet<>();
        String entered = null;
        for (HorizonZone zone : zones) {
            if (zone.contains(x, y, z)) {
                current.add(zone.gateId());
                if (initialized && !inside.contains(zone.gateId())) entered = zone.gateId();
            }
        }
        inside = current;
        initialized = true;
        // Ambiguous overlapping persisted geometry must never choose a destination.
        return current.size() == 1 ? entered : null;
    }
}
