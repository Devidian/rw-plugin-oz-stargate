package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.worldelements.GameObject;

/** Client-side presentation of the established connection. */
interface WormholeVisual {
    GameObject object();
    void frame(GateView view, long now);
}
