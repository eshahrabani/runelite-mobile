package javax.swing;

import java.awt.Component;
import java.awt.Point;

/**
 * Swing's {@code Popup}: a component shown above the rest of the UI at a fixed position.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop}. On the
 * desktop a popup is either a heavyweight native window or a lightweight component held
 * by a {@code JWindow}; the port has neither, so the {@link PopupFactory} returns an
 * instance of this class whose {@code show/hide} register the contents in
 * {@code org.runelite.mobile.bridge.PopupLayer} — the stage's overlay stack, which the
 * host paints after the base layer and consults first when hit-testing.
 *
 * <p>Port deviation: the JDK's {@code x}/{@code y} are <em>screen</em> coordinates. The
 * port's screen is the stage being painted (the sidebar, or the top-most window), so the
 * same numbers are stage-logical coordinates: {@link JPopupMenu#show} converts the
 * invoker-relative point with {@code getLocationOnScreen()} before handing it over.
 */
public class Popup {

    private Component owner;
    private Component contents;
    private int x;
    private int y;

    /** For subclasses that set their state later (the JDK shape). */
    protected Popup() {
    }

    protected Popup(Component owner, Component contents, int x, int y) {
        if (contents == null) {
            throw new IllegalArgumentException("Popup contents must be non-null");
        }
        this.owner = owner;
        this.contents = contents;
        this.x = x;
        this.y = y;
    }

    /** Resets the popup to a new owner/contents/location (the JDK's package-private hook). */
    void reset(Component owner, Component contents, int x, int y) {
        if (contents == null) {
            throw new IllegalArgumentException("Popup contents must be non-null");
        }
        this.owner = owner;
        this.contents = contents;
        this.x = x;
        this.y = y;
    }

    /**
     * Makes the popup visible. The base class has nothing to show (the JDK's
     * heavyweight implementation shows a window); the port's in-stage popup overrides it.
     */
    public void show() {
    }

    /** Hides the popup and frees it. Same split as the JDK: the subclass hides itself. */
    public void hide() {
    }

    /** The component being shown (the JDK calls it the popup's component). */
    public Component getComponent() {
        return contents;
    }

    public Component getContents() {
        return contents;
    }

    /** The component the popup belongs to, or null. */
    public Component getOwner() {
        return owner;
    }

    /** The x position in stage coordinates. */
    public int getX() {
        return x;
    }

    /** The y position in stage coordinates. */
    public int getY() {
        return y;
    }

    public Point getLocation() {
        return new Point(x, y);
    }
}
