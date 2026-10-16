package java.awt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.runelite.mobile.bridge.UiBridge;

/**
 * AWT {@code GraphicsEnvironment}: the port's font registry and its single screen device.
 *
 * <p>It holds the font families RuneLite's {@code FontManager} registers (through
 * {@code registerFont}, which is how the client's three TrueType fonts become available
 * to {@code new Font(family, style, size)}), the Android logical families, and one
 * {@link GraphicsDevice} whose default configuration reports the app's screen bounds.
 */
public class GraphicsEnvironment {

    private static final GraphicsEnvironment LOCAL = new GraphicsEnvironment();

    /** Family name -> TTF bytes of the first font registered for that family. */
    private static final Map<String, byte[]> FONT_DATA = new LinkedHashMap<>();
    /** Registered family names, in registration order. */
    private static final Set<String> FAMILIES = new LinkedHashSet<>();

    private final GraphicsDevice screenDevice = new ScreenDevice();

    private GraphicsEnvironment() {
    }

    public static GraphicsEnvironment getLocalGraphicsEnvironment() {
        return LOCAL;
    }

    /**
     * Registers a font with the environment. Always returns true; a font with no TTF
     * bytes still contributes its family name to the available list.
     */
    public boolean registerFont(Font font) {
        if (font == null) {
            return true;
        }
        String family = font.getFamily();
        if (family == null || family.isEmpty()) {
            return true;
        }
        synchronized (FONT_DATA) {
            FAMILIES.add(family);
            byte[] data = font.getFontData();
            if (data != null && data.length > 0 && !FONT_DATA.containsKey(family)) {
                FONT_DATA.put(family, data);
            }
        }
        return true;
    }

    /** Registered families plus the Android logical families and their JDK aliases. */
    public String[] getAvailableFontFamilyNames() {
        List<String> names = new ArrayList<>();
        synchronized (FONT_DATA) {
            names.addAll(FAMILIES);
        }
        for (String logical : new String[] {"sans-serif", "serif", "monospace",
                "Dialog", "DialogInput", "SansSerif", "Serif", "Monospaced"}) {
            if (!names.contains(logical)) {
                names.add(logical);
            }
        }
        return names.toArray(new String[0]);
    }

    public String[] getAvailableFontFamilyNames(java.util.Locale l) {
        return getAvailableFontFamilyNames();
    }

    /** The one virtual screen. */
    public GraphicsDevice[] getScreenDevices() {
        return new GraphicsDevice[] {screenDevice};
    }

    public GraphicsDevice getDefaultScreenDevice() {
        return screenDevice;
    }

    /** One {@link Font} per registered family, in registration order. */
    public Font[] getAllFonts() {
        List<Font> fonts = new ArrayList<>();
        synchronized (FONT_DATA) {
            for (String family : FAMILIES) {
                fonts.add(new Font(family, Font.PLAIN, 12));
            }
        }
        return fonts.toArray(new Font[0]);
    }

    public boolean isHeadless() {
        return false;
    }

    public static boolean isHeadlessInstance() {
        return false;
    }

    /** Package-private: TTF bytes previously registered for a family, or null. */
    static byte[] lookupFontData(String family) {
        if (family == null) {
            return null;
        }
        synchronized (FONT_DATA) {
            return FONT_DATA.get(family);
        }
    }

    /** The port's screen; there is exactly one, with no full-screen support. */
    private static final class ScreenDevice extends GraphicsDevice {
        private final GraphicsConfiguration configuration = new ScreenConfiguration(this);

        @Override
        public int getType() {
            return TYPE_RASTER_SCREEN;
        }

        @Override
        public String getIDstring() {
            return "RLMobileDisplay";
        }

        @Override
        public GraphicsConfiguration getDefaultConfiguration() {
            return configuration;
        }

        @Override
        public GraphicsConfiguration[] getConfigurations() {
            return new GraphicsConfiguration[] {configuration};
        }

        @Override
        public Window getFullScreenWindow() {
            return null;
        }

        @Override
        public boolean isFullScreenSupported() {
            return false;
        }

        @Override
        public void setFullScreenWindow(Window w) {
            // The app owns the surface; a Swing window cannot take the whole screen.
        }
    }

    /** The screen's configuration: the app's display bounds, 1:1 transform. */
    private static final class ScreenConfiguration extends GraphicsConfiguration {
        private final GraphicsDevice device;

        ScreenConfiguration(GraphicsDevice device) {
            this.device = device;
        }

        @Override
        public GraphicsDevice getDevice() {
            return device;
        }

        @Override
        public Rectangle getBounds() {
            return new Rectangle(0, 0, UiBridge.screenWidth(), UiBridge.screenHeight());
        }

        @Override
        public java.awt.image.ColorModel getColorModel() {
            return java.awt.image.ColorModel.getRGBdefault();
        }

        @Override
        public java.awt.image.ColorModel getColorModel(int transparency) {
            return java.awt.image.ColorModel.getRGBdefault();
        }
    }
}
