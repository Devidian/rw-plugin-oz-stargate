package de.omegazirkel.risingworld.stargate.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.Test;

import de.omegazirkel.risingworld.tools.ServerThreadDispatcher;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;

public class GateNetworkStateDispatchTest {
    @Test
    public void relayStateAndDisconnectReachObserversInServerQueueOrder() throws Exception {
        Queue<Runnable> tasks = new ArrayDeque<>();
        var client = new GateNetworkClient(null, null, null, null, null, dispatcher(tasks));
        List<String> notifications = new ArrayList<>();
        client.setStateObserver(() -> notifications.add("state:" + client.remoteGateView("gate").state()));
        client.setDhdVisualStateObserver(() -> notifications.add("dhd"));
        client.setVisualStateObserver(() -> notifications.add("visual"));

        client.onTextMessage(state("OUTGOING", "dial-a"));
        assertEquals("IDLE", client.remoteGateView("gate").state());
        assertTrue(notifications.isEmpty());
        tasks.remove().run();
        assertEquals(List.of("state:OUTGOING", "dhd", "visual"), notifications);

        client.onTextMessage(state("IDLE", "outdated-dial"));
        client.onDisconnected();
        tasks.remove().run();
        assertEquals("OUTGOING", client.remoteGateView("gate").state());
        assertEquals(3, notifications.size());
        tasks.remove().run();
        assertEquals(List.of("state:OUTGOING", "dhd", "visual", "state:IDLE", "dhd", "visual"), notifications);
    }

    @Test
    public void disconnectCompletesAddressCallbackOnceAndDropsLateResponse() throws Exception {
        Queue<Runnable> tasks = new ArrayDeque<>();
        var client = new GateNetworkClient(null, null, null, null, null, dispatcher(tasks));
        List<List<String>> replies = new ArrayList<>();
        addAddressRequest(client, replies::add);

        client.onDisconnected();
        client.onDisconnected();
        client.onTextMessage("""
                {"v":1,"type":"addressList","requestId":"fixture","payload":{"gates":[]}}
                """);
        assertTrue(replies.isEmpty());
        while (!tasks.isEmpty()) tasks.remove().run();
        assertEquals(1, replies.size());
        assertNull(replies.getFirst());
    }

    @Test
    public void artificialArrivalKeepsPrecedenceWhileRelayStateChanges() throws Exception {
        Queue<Runnable> tasks = new ArrayDeque<>();
        var client = new GateNetworkClient(null, null, null, null, null, dispatcher(tasks));
        var local = new GateNetworkClient.GateView("OPEN", "INCOMING", "", 7, true);
        client.setArtificialArrival(gate -> local, gate -> true);
        client.onTextMessage(state("OUTGOING", "dial-a"));
        tasks.remove().run();
        assertSame(local, client.gateView("gate"));
        assertEquals("OUTGOING", client.remoteGateView("gate").state());

        client.onDisconnected();
        tasks.remove().run();
        assertSame(local, client.gateView("gate"));
        client.setArtificialArrival(gate -> null, gate -> false);
        assertEquals("IDLE", client.gateView("gate").state());
    }

    @SuppressWarnings("unchecked")
    private static void addAddressRequest(GateNetworkClient client, Consumer<List<String>> callback) throws Exception {
        var request = Class.forName(GateNetworkClient.class.getName() + "$Request").getDeclaredConstructor(
                String.class, String.class, Vector3f.class, Quaternion.class, String.class, String.class,
                Consumer.class, GateNetworkClient.RegistrationHandler.class);
        request.setAccessible(true);
        var requests = GateNetworkClient.class.getDeclaredField("requests");
        requests.setAccessible(true);
        ((Map<String, Object>) requests.get(client)).put("fixture",
                request.newInstance("getAddressList", null, null, null, null, null, callback, null));
    }

    private static String state(String status, String connectionId) {
        return """
                {"v":1,"type":"gateState","requestId":"fixture","payload":{
                  "gateId":"gate","state":"%s","direction":"OUTGOING","connectionId":"%s","peerGateId":"peer"
                }}
                """.formatted(status, connectionId);
    }

    private static ServerThreadDispatcher dispatcher(Queue<Runnable> queue) throws Exception {
        // The Tools test constructor is intentionally package-private.
        var constructor = ServerThreadDispatcher.class.getDeclaredConstructor(
                BooleanSupplier.class, Consumer.class, Consumer.class);
        constructor.setAccessible(true);
        return constructor.newInstance((BooleanSupplier) () -> false,
                (Consumer<Runnable>) queue::add, (Consumer<String>) message -> fail(message));
    }
}
