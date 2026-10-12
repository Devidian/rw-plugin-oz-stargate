package de.omegazirkel.risingworld.stargate.dhd;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.worldelements.GameObject;

/** Interchangeable OBJ and bundled DHD visuals for a persisted placement. */
interface DhdRenderModel {
    GameObject object();
    void update(GateView view);
}
