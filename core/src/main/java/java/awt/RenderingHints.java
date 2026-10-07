package java.awt;

import java.util.HashMap;

/**
 * AWT RenderingHints compatibility stub for Android runtime.
 *
 * <p>Implemented as a plain key/value map so {@code getRenderingHints()} can be
 * saved and restored by overlays. Anti-aliasing is not implemented for shapes
 * (the mobile rasteriser draws hard-edged geometry); text anti-aliasing is done
 * by the platform text bridge.
 */
public class RenderingHints extends HashMap<Object, Object> {
    private static final long serialVersionUID = 1L;

    /** Opaque hint key; identity equality is inherited from Object. */
    public static final class Key {
        private final String name;

        private Key(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final Key KEY_ANTIALIASING = new Key("Antialiasing");
    public static final Key KEY_TEXT_ANTIALIASING = new Key("TextAntialiasing");
    public static final Key KEY_RENDERING = new Key("Rendering");
    public static final Key KEY_COLOR_RENDERING = new Key("ColorRendering");
    public static final Key KEY_ALPHA_INTERPOLATION = new Key("AlphaInterpolation");
    public static final Key KEY_FRACTIONALMETRICS = new Key("FractionalMetrics");
    public static final Key KEY_INTERPOLATION = new Key("Interpolation");
    public static final Key KEY_STROKE_CONTROL = new Key("StrokeControl");
    public static final Key KEY_DITHERING = new Key("Dithering");

    public static final Object VALUE_ANTIALIAS_ON = new Object();
    public static final Object VALUE_ANTIALIAS_OFF = new Object();
    public static final Object VALUE_ANTIALIAS_DEFAULT = new Object();
    public static final Object VALUE_TEXT_ANTIALIAS_ON = new Object();
    public static final Object VALUE_TEXT_ANTIALIAS_OFF = new Object();
    public static final Object VALUE_TEXT_ANTIALIAS_DEFAULT = new Object();
    public static final Object VALUE_TEXT_ANTIALIAS_GASP = new Object();
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_HRGB = new Object();
    public static final Object VALUE_INTERPOLATION_NEAREST_NEIGHBOR = new Object();
    public static final Object VALUE_INTERPOLATION_BILINEAR = new Object();
    public static final Object VALUE_INTERPOLATION_BICUBIC = new Object();
    public static final Object VALUE_STROKE_PURE = new Object();
    public static final Object VALUE_STROKE_NORMALIZE = new Object();
    public static final Object VALUE_STROKE_DEFAULT = new Object();

    public RenderingHints() {
    }

    public RenderingHints(RenderingHints.Key key, Object value) {
        put(key, value);
    }
}
