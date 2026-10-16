package java.awt;

/**
 * Hand-written {@code java.awt.MouseInfo} for this port.
 *
 * <p>The desktop JDK asks the window system where the pointer is. There is no window
 * system here: the sidebar and the virtual windows live in one stage, and the app
 * forwards touches as {@link java.awt.event.MouseEvent}s. So the port remembers the
 * last pointer position the host delivered (see {@link #setPointerLocation}) and
 * reports it in stage coordinates — the same space {@link Component#getLocationOnScreen}
 * returns, which is what {@code SwingUtilities.convertPointFromScreen} expects.
 *
 * <p>{@link #getPointerInfo()} never returns {@code null} (the JDK can, on a headless
 * machine): RuneLite's {@code PluginListItem} dereferences the result inside a tooltip
 * handler, and a fabricated-but-defined position is friendlier than an NPE. Before the
 * first touch it reports the stage origin.
 */
public class MouseInfo {

    /** The shared {@link PointerInfo}; its location is updated in place. */
    private static final PointerInfo POINTER_INFO = new PointerInfo();

    private MouseInfo() {
    }

    /**
     * Records the pointer position, in stage coordinates (relative to the sidebar root
     * or to the top-left of the top-most window's chrome).
     *
     * <p>Called by the port's input path — {@code org.runelite.mobile.bridge.UiBridge}
     * invokes this for every dispatched mouse event before routing it to a component.
     */
    public static void setPointerLocation(int x, int y) {
        POINTER_INFO.setLocation(x, y);
    }

    /** The pointer position holder; never {@code null}. */
    public static PointerInfo getPointerInfo() {
        return POINTER_INFO;
    }
}
