package java.awt;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * AWT GraphicsEnvironment compatibility stub for Android runtime.
 *
 * <p>There is no desktop display; this holds the font-family registry that
 * RuneLite's {@code FontManager} populates ({@code registerFont}) and queries
 * ({@code getAvailableFontFamilyNames}), plus the local-environment singleton.
 * The TTF bytes of a registered font are kept so that a later
 * {@code new Font(family, style, size)} can recover the real glyphs (see
 * {@link Font#Font(String, int, int)}).
 */
public class GraphicsEnvironment {

    private static final GraphicsEnvironment LOCAL = new GraphicsEnvironment();

    /** Family name -> TTF bytes of the first font registered for that family. */
    private static final Map<String, byte[]> FONT_DATA = new LinkedHashMap<>();
    /** Registered family names, in registration order. */
    private static final Set<String> FAMILIES = new LinkedHashSet<>();

    private GraphicsEnvironment() {
    }

    public static GraphicsEnvironment getLocalGraphicsEnvironment() {
        return LOCAL;
    }

    /**
     * Registers a font with the environment. Always returns true; a font with
     * no TTF bytes still contributes its family name to the available list.
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

    /** @return the registered family names; never null. */
    public String[] getAvailableFontFamilyNames() {
        synchronized (FONT_DATA) {
            return FAMILIES.toArray(new String[0]);
        }
    }

    /** No desktop display is available; always an empty array. */
    public GraphicsDevice[] getScreenDevices() {
        return new GraphicsDevice[0];
    }

    /** @return the registered fonts; empty in this stub (plan B4). */
    public Font[] getAllFonts() {
        return new Font[0];
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
}
