package java.awt;

/**
 * AWT GraphicsDevice compatibility stub for Android runtime.
 *
 * <p>There is one synthetic screen device; {@code getFullScreenWindow} always
 * returns null because there is no desktop windowing system.
 */
public abstract class GraphicsDevice {
    public static final int TYPE_RASTER_SCREEN = 0;
    public static final int TYPE_PRINTER = 1;
    public static final int TYPE_IMAGE_BUFFER = 2;

    protected GraphicsDevice() {
    }

    public abstract GraphicsConfiguration getDefaultConfiguration();

    public abstract Window getFullScreenWindow();

    public String getIDstring() {
        return "mobile";
    }

    /** The port has a single configuration per device. */
    public GraphicsConfiguration[] getConfigurations() {
        return new GraphicsConfiguration[] {getDefaultConfiguration()};
    }

    /** No native full-screen mode; the app owns the surface. */
    public boolean isFullScreenSupported() {
        return false;
    }

    public void setFullScreenWindow(Window w) {
        throw new UnsupportedOperationException("no full-screen mode on this port");
    }

    public void setFullScreenWindow(Window w, java.awt.image.BufferStrategy strategy) {
        throw new UnsupportedOperationException("no full-screen mode on this port");
    }

    public int getType() {
        return TYPE_RASTER_SCREEN;
    }
}
