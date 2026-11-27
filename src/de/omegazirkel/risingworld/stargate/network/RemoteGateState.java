package de.omegazirkel.risingworld.stargate.network;

import java.util.HashMap;
import java.util.Map;

import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;

/** Relay-owned gate lifetimes. Called only on the server thread. */
final class RemoteGateState {
    record GateState(String state, String direction, String connectionId, String peerGateId) {}
    record Transition(boolean accepted, boolean closedOpen, boolean announceIncoming) {}

    private final Map<String, GateState> states = new HashMap<>();
    private final Map<String, Long> openedTimes = new HashMap<>();
    private final Map<String, Long> closingTimes = new HashMap<>();
    private final Map<String, Long> incomingTimes = new HashMap<>();

    Transition transition(String gateId, String state, String direction, String connectionId, String peerGateId,
            long nowNanos) {
        GateState next = new GateState(state, direction, connectionId, peerGateId);
        GateState previous = states.get(gateId);
        if ("IDLE".equals(state)) {
            if (previous == null || !previous.connectionId().equals(connectionId))
                return new Transition(false, false, false);
            states.remove(gateId);
            openedTimes.remove(gateId);
            closingTimes.remove(gateId);
            incomingTimes.remove(gateId);
            return new Transition(true, "OPEN".equals(previous.state()), false);
        }
        states.put(gateId, next);
        if (!"INCOMING".equals(state)) incomingTimes.remove(gateId);
        if ("OPEN".equals(state)) {
            if (previous == null || !"OPEN".equals(previous.state()) || !previous.connectionId().equals(connectionId))
                openedTimes.put(gateId, nowNanos);
            if ("INCOMING".equals(direction) && !closingTimes.containsKey(gateId))
                closingTimes.put(gateId, nowNanos + GateAudioTiming.OPEN_MS * 1_000_000L);
        } else {
            openedTimes.remove(gateId);
        }
        return new Transition(true, false, "OPEN".equals(state) && "INCOMING".equals(direction)
                && !next.equals(previous));
    }

    GateState get(String gateId) { return states.get(gateId); }
    long openedAt(String gateId) { return openedTimes.getOrDefault(gateId, 0L); }
    long closingAt(String gateId) { return closingTimes.getOrDefault(gateId, 0L); }
    long incomingAt(String gateId) { return incomingTimes.getOrDefault(gateId, 0L); }
    boolean hasIncoming(String gateId) { return incomingTimes.containsKey(gateId); }
    boolean hasIncoming() { return !incomingTimes.isEmpty(); }
    void startIncoming(String gateId, long nowNanos) { incomingTimes.put(gateId, nowNanos); }
    void setClosingAt(String gateId, long nowNanos) { closingTimes.put(gateId, nowNanos); }

    void clear() {
        states.clear();
        openedTimes.clear();
        closingTimes.clear();
        incomingTimes.clear();
    }
}
