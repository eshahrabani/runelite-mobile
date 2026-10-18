package org.runelite.mobile;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Drives the injected client's own display state: its resizable-vs-fixed display mode
 * and its canvas size.
 *
 * <p>The port presents the client's frame 1:1 (the surface blit is a fit of whatever the
 * client produced), so the frame size is entirely the client's: {@code
 * GameEngine.initialize()} puts the applet in {@code Constants.GAME_FIXED_SIZE} mode and
 * the client's own {@code init()} hard-sets its canvas to 765x503. A device-aspect frame
 * therefore needs the client's own switch -- the same one the game's options screen
 * flips -- not a host-side stretch.
 *
 * <p>Everything here is reflection over {@code Object} / the public API: this class
 * lives in the app dex and must not reference RuneLite or client types at compile time.
 * The one version-specific internal is the display-mode applier, found by name in the
 * candidate order below ({@code ms.bo(int, byte)} is the method the game itself calls
 * with the user's preference; {@code client.ne/nw/nv(int)} are duplicates that set the
 * same flag -- the static boolean {@code client.eu} that {@code isResized()} returns).
 * Calling the game's own applier keeps its side effects in place: the
 * {@code ResizeableChanged} post on the RuneLite bus and the 7680x2160 maximum-size
 * request. Re-derive these names on a client bump -- see
 * {@code docs/telemetry-assessment.md} §6.
 */
final class ClientDisplay {

    private static final String TAG = "RuneLiteMobile";

    /** The client's resizable display mode ({@code client.eu = true}). */
    static final int MODE_RESIZABLE = 2;

    private ClientDisplay() {
    }

    // Resolved once: the display-mode appliers (in candidate order) and the API methods.
    private static Method[] modeAppliers;
    private static Method clientIsResized;

    private static Method clientCanvasWidth;
    private static Method clientCanvasHeight;
    private static Method clientSetStretched;
    private static Method clientInvalidateStretching;
    private static Method gameEngineResizeCanvas;

    /**
     * Puts the client into its own resizable display mode. The appliers are tried in turn
     * and the first one that makes {@code isResized()} true wins; a candidate that throws
     * or has no effect is logged and skipped, so a client bump that renames one of them
     * only costs that one entry.
     *
     * <p>There is deliberately **no way back to fixed**: the fixed applier
     * ({@code ms.bo(1, 0)}) corrupts the client's canvas bookkeeping in this environment —
     * it requests the resizable 7680x2160 maximum, and the next client-side resize then
     * threw {@code NegativeArraySizeException} out of the frame-buffer constructor
     * ({@code tx.<init>}), killing the client thread. Fill off therefore leaves the
     * client's display mode alone: at boot that is the client's own fixed mode (what the
     * port always ran), and after a fill session it stays resizable until the app
     * restarts.
     *
     * @return true when the client reports the resizable mode afterwards.
     */
    static boolean requestResizable(Object client, ClassLoader cl) {
        if (client == null) {
            return false;
        }
        try {
            if (isResized(client, cl)) {
                return true;
            }
            for (Method applier : modeAppliers(client, cl)) {
                try {
                    if (applier.getParameterCount() == 2) {
                        applier.invoke(null, MODE_RESIZABLE, (byte) 0);
                    } else {
                        applier.invoke(null, MODE_RESIZABLE);
                    }
                    if (isResized(client, cl)) {
                        Log.i(TAG, "display mode " + MODE_RESIZABLE + " applied via " + applier.getName());
                        return true;
                    }
                    Log.w(TAG, "display mode applier " + applier.getName() + "(" + MODE_RESIZABLE
                        + ") had no effect (isResized=" + isResized(client, cl) + ")");
                } catch (Throwable t) {
                    Log.w(TAG, "display mode applier " + applier.getName() + " failed", t);
                }
            }
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "Could not apply display mode " + MODE_RESIZABLE, t);
            return false;
        }
    }

    /**
     * The display-mode appliers, in candidate order: {@code ms.bo(int, byte)} (the game's
     * own settings path), then {@code ne}/{@code nw}/{@code nv}(int) on the client class.
     */
    private static Method[] modeAppliers(Object client, ClassLoader cl) {
        if (modeAppliers != null) {
            return modeAppliers;
        }
        java.util.List<Method> found = new java.util.ArrayList<>(4);
        try {
            Method bo = Class.forName("ms", false, cl).getDeclaredMethod("bo", int.class, byte.class);
            bo.setAccessible(true);
            found.add(bo);
        } catch (Throwable t) {
            Log.w(TAG, "ms.bo(int, byte) unavailable: " + t);
        }
        for (String name : new String[]{"ne", "nw", "nv"}) {
            try {
                Method m = client.getClass().getDeclaredMethod(name, int.class);
                m.setAccessible(true);
                found.add(m);
            } catch (Throwable t) {
                Log.w(TAG, name + "(int) unavailable: " + t);
            }
        }
        modeAppliers = found.toArray(new Method[0]);
        StringBuilder names = new StringBuilder();
        for (Method m : modeAppliers) {
            names.append(names.length() == 0 ? "" : ", ").append(m.getName());
        }
        Log.i(TAG, "display mode appliers: [" + names + "]");
        return modeAppliers;
    }

    /**
     * {@code {Client.getCanvasWidth(), Client.getCanvasHeight()}}: the client's own
     * canvas size, or null when it cannot be read.
     */
    static int[] canvasSize(Object client, ClassLoader cl) {
        if (client == null) {
            return null;
        }
        try {
            if (clientCanvasWidth == null) {
                Class<?> iface = cl.loadClass("net.runelite.api.Client");
                clientCanvasWidth = iface.getMethod("getCanvasWidth");
                clientCanvasHeight = iface.getMethod("getCanvasHeight");
            }
            return new int[]{
                (Integer) clientCanvasWidth.invoke(client),
                (Integer) clientCanvasHeight.invoke(client)};
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Sizes the applet/container and asks the client to re-run its own resize path
     * ({@code GameEngine.resizeCanvas()} -> {@code to.ahi}), which sets the canvas to
     * {@code min(max(container, 0), requestedMax)}, allocates a fresh frame buffer and
     * posts {@code CanvasSizeChanged}.
     *
     * <p>The container is the applet itself: {@code to.ahz()} returns the frame only when
     * one is set, and the port never sets one. The canvas's own size is not set here --
     * the client's resize path does that, and setting it first would only make the
     * client's own COMPONENT_RESIZED listener re-enter the same path.
     *
     * <p>Never call this for a canvas that already has the requested size: the client's
     * resize path re-derives its canvas fields through its own scrambled arithmetic, and
     * a redundant pass there threw {@code NegativeArraySizeException} out of the frame
     * buffer constructor, taking the client thread with it. {@link #canvasSize} is the
     * read-back that callers check first.
     *
     * @return {canvasWidth, canvasHeight} as the public API reports them afterwards, or
     *         null when the resize could not be attempted.
     */
    static int[] resizeCanvas(Object client, ClassLoader cl, int w, int h) {
        if (client == null || w <= 0 || h <= 0) {
            return null;
        }
        try {
            if (gameEngineResizeCanvas == null) {
                gameEngineResizeCanvas = cl.loadClass("net.runelite.api.GameEngine").getMethod("resizeCanvas");
            }
            if (clientCanvasWidth == null) {
                Class<?> iface = cl.loadClass("net.runelite.api.Client");
                clientCanvasWidth = iface.getMethod("getCanvasWidth");
                clientCanvasHeight = iface.getMethod("getCanvasHeight");
            }
            ((java.awt.Component) client).setSize(w, h);
            gameEngineResizeCanvas.invoke(client);
            return new int[]{
                (Integer) clientCanvasWidth.invoke(client),
                (Integer) clientCanvasHeight.invoke(client)};
        } catch (Throwable t) {
            Log.w(TAG, "Could not resize the client canvas to " + w + "x" + h, t);
            return null;
        }
    }

    /**
     * Turns RuneLite's stretch off: an active stretch shrinks the client's real
     * dimensions ({@code getRealDimensions()}) while the canvas stays screen-sized, and
     * the client's own size requests then use that smaller size -- which would undo the
     * fill frame. Both calls are on the public API.
     */
    static void disableStretch(Object client, ClassLoader cl) {
        if (client == null) {
            return;
        }
        try {
            if (clientSetStretched == null) {
                Class<?> iface = cl.loadClass("net.runelite.api.Client");
                clientSetStretched = iface.getMethod("setStretchedEnabled", boolean.class);
                clientInvalidateStretching = iface.getMethod("invalidateStretching", boolean.class);
            }
            clientSetStretched.invoke(client, false);
            clientInvalidateStretching.invoke(client, false);
        } catch (Throwable t) {
            Log.w(TAG, "Could not disable stretch", t);
        }
    }

    /**
     * {@code Client.isResized()}: the static boolean the display-mode appliers set (the
     * game's own notion of "resizable"), false when the probe itself fails.
     */
    static boolean isResized(Object client, ClassLoader cl) {
        if (client == null) {
            return false;
        }
        try {
            if (clientIsResized == null) {
                clientIsResized = cl.loadClass("net.runelite.api.Client").getMethod("isResized");
            }
            return Boolean.TRUE.equals(clientIsResized.invoke(client));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The client's own shutdown deadline, as {@code to.re} reports it: the static long
     * that the game loop ({@code to.run}) checks each iteration and exits on, set by the
     * {@code to.stop()/destroy()} Applet-lifecycle path and by {@code Client.stopNow()}.
     * Zero means "no deadline". It is a diagnostic: a frozen world whose client thread is
     * gone was a client that took its own shutdown or error path. The field is
     * version-specific -- see {@code docs/telemetry-assessment.md} §6.
     */
    static long shutdownDeadline(ClassLoader cl) {
        try {
            if (shutdownField == null) {
                shutdownField = cl.loadClass("to").getDeclaredField("re");
                shutdownField.setAccessible(true);
            }
            Object value = shutdownField.get(null);
            return value instanceof Long ? (Long) value : 0L;
        } catch (Throwable t) {
            return -1L;   // unreadable; the caller reports "ERR"
        }
    }

    private static java.lang.reflect.Field shutdownField;
}
