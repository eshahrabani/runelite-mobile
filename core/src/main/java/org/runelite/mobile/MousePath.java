package org.runelite.mobile;

/**
 * Expands a touch-sample segment into a mouse-like motion stream, so the
 * synthesized AWT telemetry delivered to the game resembles a real mouse
 * (small, closely spaced moves) instead of single jumped events.
 *
 * Pure arithmetic with no Android dependencies, so it is testable off-device.
 */
public final class MousePath {

    /** Maximum distance between emitted points, in game pixels. */
    public static final int MAX_SEG_PX = 8;
    /** Maximum number of fill points emitted per source segment. */
    public static final int MAX_FILL = 16;

    private MousePath() {}

    /**
     * Fills the segment {@code (x0,y0,t0) -> (x1,y1,t1)} with intermediate
     * points at most {@link #MAX_SEG_PX} apart, capped at {@link #MAX_FILL}.
     *
     * @return flat {@code [x,y,t, ...]} of the points to emit; the start is
     *         excluded and the endpoint is included. Never {@code null}, and
     *         at minimum the endpoint.
     */
    public static long[] expand(int x0, int y0, long t0, int x1, int y1, long t1) {
        long dx = (long) x1 - x0;
        long dy = (long) y1 - y0;
        double dist = Math.sqrt((double) dx * dx + (double) dy * dy);
        int k = (int) Math.min(MAX_FILL, Math.max(0d, Math.ceil(dist / MAX_SEG_PX) - 1d));
        if (k <= 0) {
            return new long[]{x1, y1, t1};
        }
        if (t1 <= t0) {
            t1 = t0 + k + 1L;
        }
        long dt = t1 - t0;
        long den = k + 1L;
        long[] out = new long[(k + 1) * 3];
        for (int i = 1; i <= k; i++) {
            int o = (i - 1) * 3;
            out[o] = Math.round(x0 + (double) dx * i / den);
            out[o + 1] = Math.round(y0 + (double) dy * i / den);
            out[o + 2] = t0 + dt * i / den;
        }
        int o = k * 3;
        out[o] = x1;
        out[o + 1] = y1;
        out[o + 2] = t1;
        return out;
    }
}
