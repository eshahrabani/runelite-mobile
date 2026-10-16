package java.awt;

/**
 * Hand-written {@code java.awt.PointerInfo} for this port.
 *
 * <p>Describes the graphics device and the location of the pointer. The port has exactly
 * one "device" — the stage — so the interesting part is the position, which
 * {@link MouseInfo} updates through the package-private {@link #setLocation} from the
 * host's input path. The point is returned as a copy: Swing code (RuneLite's
 * {@code PluginListItem}) mutates the point it gets back via
 * {@code convertPointFromScreen}, which must not corrupt the tracked position.
 */
public class PointerInfo {

    private int x;
    private int y;

    /** Package-private: {@link MouseInfo} owns the single instance. */
    PointerInfo() {
    }

    /** The pointer's position in stage coordinates. */
    public Point getLocation() {
        return new Point(x, y);
    }

    void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
