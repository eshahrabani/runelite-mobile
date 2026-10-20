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

    // ═══════════════════════════════════════════════════════════════════════
    // Portrait band: 70% game view, 30% tabbed HUD
    // ═══════════════════════════════════════════════════════════════════════

    /*
     * Measured on client 1.13.2 (resizable display mode, canvas 765x1751). The ids are
     * interface-definition data, not jar symbols, so a client bump can move them; the child
     * dump this class logs on its first application is what they are re-derived from
     * (docs/telemetry-assessment.md §6).
     *
     *   tli 161  the resizable toplevel -- what getTopLevelInterfaceId() reports while the
     *            play layout is up (the welcome/ad screen reports 165, the fullscreen
     *            container; 164 is unused by this build).
     *   161      children 0-18 are the scene's layout layers, 16/17 the mainmodal/maincrm
     *            (512x334, centred in the 515x1586 modal area), 19-48 the minimap pieces,
     *            the side panel's slot (child 38, 200x281 at 544,1443, with the two paging
     *            arrows either side) and the side-tab icon row (children 41-48 at y=1714).
     *   162      the chatbox: root child 0 is the 519x165 chat parent at (0,1586) -- the
     *            band's bottom-left; its rows (child 1 onwards) are its *siblings*, not its
     *            children (the root reports no children at all).
     *
     * The client's layout owns all of that geometry: it rewrites the panels' positions and
     * sizes on its own layout runs, and neither a {@code setPos}/{@code setSize} nor a
     * forced offset survives (measured: a size set on the chat root reset between frames,
     * an offset moved the root out from under its rows, and re-running the panels' scripts
     * left the side panel empty). So nothing here writes a widget. What the portrait band
     * needs from the client are the two panel rects, and the host does the rest: it clips
     * the 3D scene at the band's top and masks everything in the band except the selected
     * panel, which is what makes the band a one-panel-at-a-time HUD.
     */

    /** The chatbox interface ({@code InterfaceID.CHATBOX}) and its parent child. */
    private static final int GROUP_CHAT = 162, CHILD_CHAT = 0;
    /** Frame rows the app's tab strip reserves at the top of the band (56 -> 28 dp @ 1.5x). */
    static final int TAB_ROWS = 56;
    /** The band is at least this tall, so the side panel's 281 rows still fit. */
    private static final int MIN_BAND = 360;
    /** Side-slot discovery: the panel root sits in the right-hand column, in the band. */
    private static final int SIDE_SLOT_X_NUM = 2, SIDE_SLOT_X_DEN = 3;   // x > 2/3 frameW
    private static final int SIDE_SLOT_Y_NUM = 1, SIDE_SLOT_Y_DEN = 3;   // y > 1/3 frameH

    /** First frame row of the band as last measured, or 0 when the layout is not active. */
    private static volatile int appliedTopRow;
    /** First frame row below the tab strip: where the band's panel content starts. */
    private static volatile int appliedContentTop;
    /** The panels' rects in frame coordinates ({@code x, y, width, height}), or null. */
    private static volatile int[] chatRect, sideRect;

    private static int lastFrameW, lastFrameH, lastTli, lastTab, layoutTicks, dumpedTli;
    private static Object lastChat;

    // Resolved once per class loader: a client restart builds a new DexClassLoader, and
    // the cached Method objects of the old one cannot be invoked on the new widgets.
    private static ClassLoader widgetLoader;
    private static Method clientGetWidget, clientTopLevelInterfaceId;
    private static Method widgetGetWidth, widgetGetHeight, widgetGetOriginalX,
        widgetGetOriginalY, widgetGetOriginalWidth, widgetGetOriginalHeight, widgetGetType,
        widgetGetContentType;

    /** The band's top frame row, or 0 when the portrait layout is not applied. */
    static int appliedTopRow() {
        return appliedTopRow;
    }

    /** The band's content top (below the app's tab strip), or 0 when not applied. */
    static int appliedContentTop() {
        return appliedContentTop;
    }

    /** The chatbox' rect in frame coordinates, or null. The host masks with it. */
    static int[] appliedChatRect() {
        return chatRect;
    }

    /** The side panel's rect in frame coordinates, or null. The host masks with it. */
    static int[] appliedSideRect() {
        return sideRect;
    }

    /**
     * Measures the portrait band: the split row ({@link #appliedTopRow()}), the content top
     * below the tab strip, and the two panels' rects. Returns {@code "ok"}, {@code
     * "pending"} (the chatbox is not loaded yet), {@code "inactive"} (no resizable layout is
     * drawn -- the welcome screen, a loading state or a fullscreen interface: nothing is
     * touched and the band must not be cleared) or {@code "unavailable: <reason>"}.
     *
     * <p>Client thread only. It reads widgets; the caller must not expect any of them to
     * have been changed.
     */
    static String applyPortraitLayout(Object client, ClassLoader cl, int frameW, int frameH, int tab) {
        if (client == null || cl == null || frameW <= 0 || frameH <= 0) {
            return "pending";
        }
        try {
            resolveWidgetApi(cl);
            int tli = intOf(clientTopLevelInterfaceId, client);
            if (tli != 161) {
                appliedTopRow = 0;
                appliedContentTop = 0;
                chatRect = null;
                sideRect = null;
                return "inactive";
            }
            Object chat = widget(client, GROUP_CHAT, CHILD_CHAT);
            if (chat == null) {
                appliedTopRow = 0;
                appliedContentTop = 0;
                return "pending";    // the chatbox is not loaded yet
            }
            int bandTop = Math.round(frameH * 0.70f);
            if (frameH - bandTop < MIN_BAND) {
                bandTop = Math.max(1, frameH - MIN_BAND);
            }
            int contentTop = bandTop + TAB_ROWS;
            int contentH = Math.max(1, frameH - contentTop);

            boolean sizeChanged = frameW != lastFrameW || frameH != lastFrameH;
            boolean reload = chat != lastChat || tli != lastTli || tab != lastTab;
            boolean periodic = layoutTicks++ % 60 == 0;

            // The side panel is a toplevel child (161:38 on 1.13.2) found by its geometry
            // (right-hand column, panel-sized) rather than a pinned child id, and re-found
            // now and then because the client rebuilds it as tabs are switched.
            if (reload || periodic || sideRect == null) {
                sideRect = rectOf(findSideSlot(client, tli, frameW, frameH));
            }
            chatRect = rectOf(chat);
            appliedTopRow = bandTop;
            appliedContentTop = contentTop;
            lastChat = chat;
            if (sizeChanged || reload) {
                Log.i(TAG, "portrait layout: tli " + tli + " frame " + frameW + "x" + frameH
                    + " band " + bandTop + "+" + TAB_ROWS + " content " + contentTop + "+" + contentH
                    + " tab " + (tab == 0 ? "chat" : "side")
                    + " chat " + boundsOf(chat) + " side " + boundsOf(sideRect));
            }
            if (dumpedTli != tli) {
                dumpedTli = tli;
                dumpLayoutChildren(client, tli);
            }
            lastFrameW = frameW;
            lastFrameH = frameH;
            lastTli = tli;
            lastTab = tab;
            return "ok";
        } catch (Throwable t) {
            appliedTopRow = 0;
            appliedContentTop = 0;
            chatRect = null;
            sideRect = null;
            Log.w(TAG, "portrait layout unavailable: " + t);
            return "unavailable: " + t;
        }
    }

    /**
     * The side panel's slot: the toplevel child in the right-hand column, in the band, and
     * panel-sized (on 1.13.2 that is {@code 161:38}, 200x281 at 544,1443). Scanned over the
     * toplevel's children only, and only on a layout change / periodically.
     */
    private static Object findSideSlot(Object client, int tli, int frameW, int frameH) throws Throwable {
        int minX = frameW * SIDE_SLOT_X_NUM / SIDE_SLOT_X_DEN;
        int minY = frameH * SIDE_SLOT_Y_NUM / SIDE_SLOT_Y_DEN;
        for (int child = 0; child < 128; child++) {
            Object w;
            try {
                w = clientGetWidget.invoke(client, tli, child);
            } catch (Throwable t) {
                continue;
            }
            int[] r = rectOf(w);
            if (r != null && r[0] >= minX && r[1] >= minY && r[2] >= 120 && r[3] >= 120) {
                return w;
            }
        }
        return null;
    }

    private static int[] rectOf(Object w) {
        if (w == null) {
            return null;
        }
        try {
            java.awt.Rectangle r = (java.awt.Rectangle) w.getClass().getMethod("getBounds").invoke(w);
            return new int[]{r.x, r.y, r.width, r.height};
        } catch (Throwable t) {
            return null;
        }
    }

    private static String boundsOf(Object w) {
        int[] r = rectOf(w);
        return r == null ? "null" : r[0] + "," + r[1] + "x" + r[2] + "x" + r[3];
    }

    private static String boundsOf(int[] r) {
        return r == null ? "null" : r[0] + "," + r[1] + "x" + r[2] + "x" + r[3];
    }

    private static Object widget(Object client, int group, int child) throws Throwable {
        return clientGetWidget.invoke(client, group, child);
    }

    private static int intOf(Method method, Object target) throws Throwable {
        return ((Integer) method.invoke(target)).intValue();
    }

    /**
     * One-shot dump of the toplevel's children, {@code id:type/contentType:x,y,w,h}. The
     * child ids above are interface data, not jar symbols, so this line is what they are
     * re-derived from after a client bump (docs/telemetry-assessment.md §6).
     */
    private static void dumpLayoutChildren(Object client, int group) {
        try {
            StringBuilder sb = new StringBuilder("portrait layout children ").append(group).append(':');
            for (int child = 0; child < 128; child++) {
                Object w = widget(client, group, child);
                if (w == null) {
                    continue;
                }
                sb.append(' ').append(child)
                    .append(':').append(intOf(widgetGetType, w))
                    .append('/').append(intOf(widgetGetContentType, w))
                    .append('@').append(intOf(widgetGetOriginalX, w))
                    .append(',').append(intOf(widgetGetOriginalY, w))
                    .append('x').append(intOf(widgetGetOriginalWidth, w))
                    .append('x').append(intOf(widgetGetOriginalHeight, w));
            }
            Log.i(TAG, sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "portrait layout child dump failed: " + t);
        }
    }

    /** Resolves the public widget API once per class loader (a restart replaces it). */
    private static void resolveWidgetApi(ClassLoader cl) throws Throwable {
        if (widgetLoader == cl && clientGetWidget != null) {
            return;
        }
        Class<?> clientIface = cl.loadClass("net.runelite.api.Client");
        clientGetWidget = clientIface.getMethod("getWidget", int.class, int.class);
        clientTopLevelInterfaceId = clientIface.getMethod("getTopLevelInterfaceId");
        Class<?> w = cl.loadClass("net.runelite.api.widgets.Widget");
        widgetGetWidth = w.getMethod("getWidth");
        widgetGetHeight = w.getMethod("getHeight");
        widgetGetOriginalX = w.getMethod("getOriginalX");
        widgetGetOriginalY = w.getMethod("getOriginalY");
        widgetGetOriginalWidth = w.getMethod("getOriginalWidth");
        widgetGetOriginalHeight = w.getMethod("getOriginalHeight");
        widgetGetType = w.getMethod("getType");
        widgetGetContentType = w.getMethod("getContentType");
        widgetLoader = cl;
        // A previous loader's widgets are gone: force a fresh measure and dump.
        lastChat = null;
        lastTli = 0;
        lastTab = -1;
        lastFrameW = 0;
        lastFrameH = 0;
        chatRect = null;
        sideRect = null;
        dumpedTli = 0;
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
