package de.omegazirkel.risingworld.stargate.audio;

/** Reference clip lengths and playback timing in milliseconds; no source audio. */
public final class GateAudioTiming {
    public static final int DHD_MS = 1131;
    public static final int RING_CLOCKWISE_MS = 3182;
    public static final int RING_COUNTERCLOCKWISE_MS = 3090;
    public static final int CHEVRON_OUT_MS = 2286;
    public static final int INCOMING_CHEVRON_MS = 400;
    public static final int INCOMING_TOTAL_MS = 7 * INCOMING_CHEVRON_MS;
    public static final int DIAL_STEP_MS = 7000;
    public static final int OPEN_MS = 60000;
    public static final int SHUTDOWN_MS = 3216;
    public static final int COLLAPSE_MS = 600;
    public static final int SHUTDOWN_START_MS = OPEN_MS + COLLAPSE_MS - SHUTDOWN_MS;

    private GateAudioTiming() { }

    public static int ringMillis(int step) {
        return step % 2 == 0 ? RING_CLOCKWISE_MS : RING_COUNTERCLOCKWISE_MS;
    }

    public static int chevronStartMillis(int step) { return DHD_MS + ringMillis(step); }

    public static boolean closingCueDue(long openedNanos, long nowNanos) {
        return openedNanos != 0 && nowNanos - openedNanos >= SHUTDOWN_START_MS * 1_000_000L;
    }

    public static boolean closingCueDueAt(long closesAtNanos, long nowNanos) {
        return closesAtNanos != 0
                && nowNanos >= closesAtNanos + (COLLAPSE_MS - SHUTDOWN_MS) * 1_000_000L;
    }
}
