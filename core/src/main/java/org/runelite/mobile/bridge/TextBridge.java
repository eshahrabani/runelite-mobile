package org.runelite.mobile.bridge;

/**
 * Bridge to the platform's text rasteriser.
 *
 * <p>RuneLite's overlay API draws text through {@code java.awt.Graphics.drawString}
 * with the three RuneScape TrueType fonts the client jar ships
 * ({@code net/runelite/client/ui/runescape{,_bold,_small}.ttf}). The core module
 * cannot write a TTF rasteriser and cannot reference {@code android.*} (it is
 * compiled with {@code --limit-modules java.base,jdk.unsupported}), so the
 * actual glyph rasterisation lives in the app dex
 * ({@code org.runelite.mobile.AndroidTextRenderer}) and is reached through this
 * interface, exactly like {@link AWTBridge} carries the display buffer.
 *
 * <p>A font is an opaque handle owned by the renderer ({@code android.graphics.Typeface}
 * + {@code Paint} in the app implementation). The core-side {@code java.awt.Font}
 * keeps the TTF bytes plus the requested style/size and asks the renderer for a
 * handle lazily; with no renderer installed every helper degrades to a
 * deterministic approximation instead of throwing, so overlay code never dies
 * because text is unavailable.
 */
public final class TextBridge {

    /** Implemented by the app module; installed once from MainActivity.onCreate. */
    public interface TextRenderer {
        /** Creates a platform font handle from raw TTF bytes; may return null on failure. */
        Object createFont(byte[] ttf, int style, int sizePx, String familyName);

        int stringWidth(Object font, String text);

        int height(Object font);

        int ascent(Object font);

        int descent(Object font);

        /**
         * Draws {@code text} with its baseline at (x, y) into an ARGB pixel array,
         * source-over. {@code shadow} requests a one-pixel offset black copy first.
         */
        void drawString(Object font, int[] dest, int destW, int destH, String text,
                        int x, int y, int argb, boolean shadow);

        /**
         * The same draw at a device scale: {@code x}/{@code y} are already device
         * pixels and the glyphs must be rasterised {@code scale} times larger than the
         * handle's logical size (the sidebar's UI scale).
         */
        default void drawString(Object font, int[] dest, int destW, int destH, String text,
                                int x, int y, int argb, boolean shadow, float scale) {
            if (scale == 1f) {
                drawString(font, dest, destW, destH, text, x, y, argb, shadow);
            } else {
                Object scaled = scaledFont(font, scale);
                drawString(scaled != null ? scaled : font, dest, destW, destH, text, x, y, argb, shadow);
            }
        }

        /** A handle whose glyphs are {@code scale} times the logical point size. */
        default Object scaledFont(Object font, float scale) {
            return font;
        }

        /** The logical point size a handle was created for. */
        default int sizeOf(Object font) {
            return font instanceof HandleSize ? ((HandleSize) font).logicalSize() : 0;
        }
    }

    /** Installed by the app module; null while the platform renderer is absent. */
    public static volatile TextRenderer renderer;

    private TextBridge() {}

    public static boolean available() {
        return renderer != null;
    }

    /** Fallback glyph width when no renderer is installed: 6 px per character. */
    public static int approximateWidth(String text, int sizePx) {
        if (text == null) {
            return 0;
        }
        return text.length() * 6;
    }

    public static Object createFont(byte[] ttf, int style, int sizePx, String familyName) {
        TextRenderer r = renderer;
        if (r == null || ttf == null) {
            return null;
        }
        try {
            return r.createFont(ttf, style, sizePx, familyName);
        } catch (Throwable t) {
            return null;
        }
    }

    public static int stringWidth(Object font, String text) {
        TextRenderer r = renderer;
        if (r == null || font == null || text == null) {
            return approximateWidth(text, 0);
        }
        try {
            return r.stringWidth(font, text);
        } catch (Throwable t) {
            return approximateWidth(text, 0);
        }
    }

    public static int height(Object font, int sizePx) {
        TextRenderer r = renderer;
        if (r == null || font == null) {
            return sizePx;
        }
        try {
            return r.height(font);
        } catch (Throwable t) {
            return sizePx;
        }
    }

    public static int ascent(Object font, int sizePx) {
        TextRenderer r = renderer;
        if (r == null || font == null) {
            return sizePx;
        }
        try {
            return r.ascent(font);
        } catch (Throwable t) {
            return sizePx;
        }
    }

    public static int descent(Object font, int sizePx) {
        TextRenderer r = renderer;
        if (r == null || font == null) {
            return 0;
        }
        try {
            return r.descent(font);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * @return true when the platform renderer painted the text; false means the
     *         caller should fall back (nothing is drawn in that case).
     */
    public static boolean drawString(Object font, int[] dest, int destW, int destH, String text,
                                     int x, int y, int argb, boolean shadow) {
        return drawString(font, dest, destW, destH, text, x, y, argb, shadow, 1f);
    }

    /**
     * Scale-aware draw: the caller has already mapped {@code x}/{@code y} to device
     * pixels, and {@code scale} tells the renderer how much larger than its logical
     * point size the glyphs must be rasterised (the sidebar's integer UI scale).
     */
    public static boolean drawString(Object font, int[] dest, int destW, int destH, String text,
                                     int x, int y, int argb, boolean shadow, float scale) {
        TextRenderer r = renderer;
        if (r == null || font == null || text == null || text.isEmpty() || dest == null) {
            return false;
        }
        try {
            r.drawString(font, dest, destW, destH, text, x, y, argb, shadow, scale <= 0f ? 1f : scale);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The device-resolution variant of a logical handle: glyphs are rasterised at
     * {@code logicalSize * scale} while the caller keeps measuring in logical units.
     * Returns the input handle when scaling is not possible.
     */
    public static Object scaledFont(Object font, float scale) {
        TextRenderer r = renderer;
        if (r == null || font == null || scale == 1f) {
            return font;
        }
        try {
            Object scaled = r.scaledFont(font, scale);
            return scaled != null ? scaled : font;
        } catch (Throwable t) {
            return font;
        }
    }

    /** Logical point size of a handle, or 0 when it cannot be queried. */
    public static int sizeOf(Object font) {
        TextRenderer r = renderer;
        if (r == null || font == null || !(font instanceof HandleSize)) {
            return font instanceof HandleSize ? ((HandleSize) font).logicalSize() : 0;
        }
        try {
            return r.sizeOf(font);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Implemented by a renderer's handle so the core can ask its logical size. */
    public interface HandleSize {
        int logicalSize();
    }
}
