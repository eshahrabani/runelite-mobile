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

    public int getType() {
        return TYPE_RASTER_SCREEN;
    }
}
