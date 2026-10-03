package de.omegazirkel.risingworld.stargate.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GateNetworkClientAddressTest {
    @Test public void acceptsOnlyUsablePublicIpv4ForAutomaticHost() {
        assertTrue(GateNetworkClient.isPublicIpv4("82.165.51.138"));
        assertFalse(GateNetworkClient.isPublicIpv4(""));
        assertFalse(GateNetworkClient.isPublicIpv4("0.0.0.0"));
        assertFalse(GateNetworkClient.isPublicIpv4("127.0.0.1"));
        assertFalse(GateNetworkClient.isPublicIpv4("172.20.0.5"));
        assertFalse(GateNetworkClient.isPublicIpv4("100.64.0.1"));
        assertFalse(GateNetworkClient.isPublicIpv4("not-an-ip"));
    }

    @Test public void localAddressesCannotBeConfusedWithRelayHexAddresses() {
        for (int i = 0; i < 20; i++) {
            String id = GateNetworkClient.newLocalGateId();
            assertTrue(GateNetworkClient.isLocalGateId(id));
            assertTrue(id.length() == 16);
        }
        assertFalse(GateNetworkClient.isLocalGateId("0123456789ABCDEF"));
    }
}
