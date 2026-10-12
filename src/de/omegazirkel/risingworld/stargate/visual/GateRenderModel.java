package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.worldelements.GameObject;

/** Bundle visual representation of one gate. */
interface GateRenderModel {
    GameObject object();
    void update(GateView view);
    void frame(long now);
}
