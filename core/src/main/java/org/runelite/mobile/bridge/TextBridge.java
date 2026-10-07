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
        TextRenderer r = renderer;
        if (r == null || font == null || text == null || text.isEmpty() || dest == null) {
            return false;
        }
        try {
            r.drawString(font, dest, destW, destH, text, x, y, argb, shadow);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
