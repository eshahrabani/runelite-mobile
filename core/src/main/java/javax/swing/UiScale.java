package javax.swing;

/**
 * The single UI scale of the port's Swing stage.
 *
 * <p>Fonts keep <em>logical</em> sizes — {@code Font.getSize()} and every
 * {@code FontMetrics} answer stay in logical units so that all layout arithmetic
 * (preferred sizes, text wrapping, icon placement) is scale-independent. Only the
 * rasteriser scales: {@code Graphics.drawString} multiplies the device coordinates and
 * the font size by this factor, which is what makes glyphs sharp on a high-density
 * screen instead of an upscaled bitmap of logical pixels.
 *
 * <p>Set by {@code UiBridge.setSidebarBounds} from the app's stage geometry; 1.0 until
 * then (the self-tests and any core-only use run at 1.0).
 */
public final class UiScale {
    private static volatile float scale = 1f;

    private UiScale() {
    }

    public static float scale() {
        return scale;
    }

    public static void setScale(float newScale) {
        scale = newScale <= 0f ? 1f : newScale;
    }

    /** Converts a logical length to device pixels (rounded). */
    public static int toDevice(int logical) {
        return Math.round(logical * scale);
    }

    /** Converts a device length back to logical units (rounded). */
    public static int toLogical(int device) {
        return Math.round(device / scale);
    }
}
