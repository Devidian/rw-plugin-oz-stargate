package de.omegazirkel.risingworld.stargate.visual;

/** One bounded client presence request per viewer; stale replies cannot revive old rendering. */
final class GateVisualPresence {
    static final long INTERVAL_NANOS = 5_000_000_000L;
    static final long TIMEOUT_NANOS = 8_000_000_000L;
    private long sequence;
    private long pending;
    private long deadline;
    private long nextCheck;

    long begin(long now) {
        if (pending != 0 || now < nextCheck) return 0;
        pending = ++sequence;
        deadline = now + TIMEOUT_NANOS;
        return pending;
    }

    boolean complete(long token, long now) {
        if (token == 0 || pending != token) return false;
        pending = 0;
        nextCheck = now + INTERVAL_NANOS;
        return true;
    }

    boolean timedOut(long now) {
        if (pending == 0 || now < deadline) return false;
        pending = 0;
        nextCheck = now + INTERVAL_NANOS;
        return true;
    }

    void reset(long now) {
        pending = 0;
        nextCheck = now + INTERVAL_NANOS;
    }
}
