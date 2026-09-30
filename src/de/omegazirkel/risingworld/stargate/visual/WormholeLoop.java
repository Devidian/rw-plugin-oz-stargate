package de.omegazirkel.risingworld.stargate.visual;

/** Layout and wall-clock playback of the supplied 280-frame/24fps reference, sampled at 12fps. */
final class WormholeLoop {
    static final int FRAMES = 140, FPS = 12, COLUMNS = 7, ROWS = 10, TILE = 384;
    static final int FRAMES_PER_ATLAS = COLUMNS * ROWS;
    private WormholeLoop() { }

    static int frame(long now) {
        // Split seconds to avoid multiplying the full nanoTime value (overflow after long uptime).
        long seconds = Math.floorDiv(now, 1_000_000_000L);
        long remainder = Math.floorMod(now, 1_000_000_000L);
        return (int) Math.floorMod(Math.floorMod(seconds, FRAMES) * FPS
                + remainder * FPS / 1_000_000_000L, FRAMES);
    }

    static float u(int frame, float x) {
        return ((frame % COLUMNS) * TILE + .5f + (x + 1) * .5f * (TILE - 1)) / (COLUMNS * TILE);
    }

    static float v(int frame, float y) {
        int row = (frame % FRAMES_PER_ATLAS) / COLUMNS;
        return 1 - (row * TILE + .5f + (1 - y) * .5f * (TILE - 1)) / (ROWS * TILE);
    }
}
