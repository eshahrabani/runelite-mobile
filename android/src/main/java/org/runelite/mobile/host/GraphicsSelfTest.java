package org.runelite.mobile.host;

import android.util.Log;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Self-test for the {@code java.awt} draw surface the RuneLite overlays use.
 *
 * <p>Everything a plugin draws goes through these stubs: {@code Graphics2D} shapes and
 * text, {@code drawImage} of sprite {@code BufferedImage}s, and -- the invariant that
 * matters most -- the 1:1 opaque blit of the game frame, which must stay a plain
 * {@code System.arraycopy} (the software rasteriser writes alpha-0 pixels and the frame
 * is presented with {@code setHasAlpha(false)}; a blending blit would leave the previous
 * frame on screen).
 *
 * <p>Run once from {@link RuneLiteHost} at startup: the result is a single logcat line,
 * {@code GFX SELFTEST PASS} or {@code GFX SELFTEST FAIL <case>}, and the same string is
 * exposed for the side panel's Host tab.
 */
public final class GraphicsSelfTest {

    private static final String TAG = "RuneLiteHost";

    private GraphicsSelfTest() {
    }

    /** @return null when everything passed, otherwise the first failing case. */
    public static String run() {
        try {
            String failure = testFillRect();
            if (failure == null) {
                failure = testFrameBlitIsOpaqueCopy();
            }
            if (failure == null) {
                failure = testAlphaBlend();
            }
            if (failure == null) {
                failure = testText();
            }
            if (failure != null) {
                Log.e(TAG, "GFX SELFTEST FAIL " + failure);
                return failure;
            }
            Log.i(TAG, "GFX SELFTEST PASS");
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "GFX SELFTEST FAIL exception", t);
            return "exception: " + t;
        }
    }

    private static String testFillRect() {
        // Opaque destination (like a sprite canvas): the fill lands verbatim.
        int[] px = new int[64 * 64];
        java.util.Arrays.fill(px, 0xFF000000);
        Image img = new Image(px, 64, 64);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setColor(new Color(0x11, 0x22, 0x33));
        g.fillRect(4, 4, 8, 8);
        int inside = px[5 * 64 + 5];
        if (inside != 0xFF112233) {
            return "fillRect inside=" + Integer.toHexString(inside);
        }
        if (px[0] != 0xFF000000) {
            return "fillRect bled outside=" + Integer.toHexString(px[0]);
        }

        // Frame-like destination (alpha 0 everywhere, presented with setHasAlpha(false)):
        // the RGB channels must be written and the alpha byte preserved, because the
        // software rasteriser's ground/tree pixels rely on staying alpha 0.
        int[] frame = new int[16 * 16];
        Image frameImage = new Image(frame, 16, 16);
        Graphics2D fg = (Graphics2D) frameImage.getGraphics();
        fg.setColor(new Color(0x44, 0x55, 0x66));
        fg.fillRect(0, 0, 2, 2);
        if (frame[0] != 0x00445566) {
            return "frame fill changed alpha: " + Integer.toHexString(frame[0]);
        }
        return null;
    }

    /**
     * The game-frame invariant: an opaque source is copied verbatim, including pixels
     * whose alpha byte is 0.
     */
    private static String testFrameBlitIsOpaqueCopy() {
        int[] src = new int[64 * 64];
        for (int i = 0; i < src.length; i++) {
            src[i] = 0xFF000000 | (i * 7);
        }
        src[0] = 0x00123456; // alpha 0: the rasteriser's ground pixels look like this
        Image source = new Image(src, 64, 64);
        int[] dst = new int[64 * 64];
        Image dest = new Image(dst, 64, 64);
        Graphics g = dest.getGraphics();
        g.drawImage(source, 0, 0, null);
        if (dst[0] != src[0]) {
            return "frame blit dropped an alpha-0 pixel: " + Integer.toHexString(dst[0]);
        }
        for (int i = 0; i < dst.length; i++) {
            if (dst[i] != src[i]) {
                return "frame blit differs at " + i + ": " + Integer.toHexString(dst[i]);
            }
        }
        return null;
    }

    /** An ARGB sprite must blend source-over, not overwrite. */
    private static String testAlphaBlend() {
        int[] dst = new int[16 * 16];
        for (int i = 0; i < dst.length; i++) {
            dst[i] = 0xFFFFFFFF;
        }
        Image dest = new Image(dst, 16, 16);
        int[] spritePx = new int[4 * 4];
        for (int i = 0; i < spritePx.length; i++) {
            spritePx[i] = 0x80000000; // 50% black
        }
        BufferedImage sprite = new BufferedImage(spritePx, 4, 4);
        Graphics g = dest.getGraphics();
        g.drawImage(sprite, 0, 0, 4, 4, null);
        int blended = dst[0];
        int red = (blended >>> 16) & 0xFF;
        if (blended == 0xFFFFFFFF) {
            return "alpha blend did not draw (pixel unchanged)";
        }
        if (red < 0x70 || red > 0x90) {
            return "alpha blend red=" + Integer.toHexString(red) + " (expected ~0x7F)";
        }
        if (dst[8 * 16 + 8] != 0xFFFFFFFF) {
            return "alpha blend bled outside the sprite";
        }
        return null;
    }

    /** Overlay text needs a real platform font through TextBridge. */
    private static String testText() throws Exception {
        ClassLoader loader = RuneLiteHost.clientLoader();
        if (loader == null) {
            return "no client loader (host not started)";
        }
        Font font = null;
        try (InputStream in = loader.getResourceAsStream("net/runelite/client/ui/runescape.ttf")) {
            if (in != null) {
                font = Font.createFont(Font.TRUETYPE_FONT, in);
            }
        }
        if (font == null) {
            return "runescape.ttf not readable from the client loader";
        }
        font = font.deriveFont(14f);
        int[] px = new int[64 * 64];
        Image img = new Image(px, 64, 64);
        Graphics2D g = (Graphics2D) img.getGraphics();
        FontMetrics metrics = g.getFontMetrics(font);
        if (metrics == null) {
            return "Graphics.getFontMetrics returned null";
        }
        int width = metrics.stringWidth("test");
        if (width <= 0) {
            return "FontMetrics.stringWidth returned " + width;
        }
        g.setFont(font);
        g.setColor(Color.WHITE);
        g.drawString("Test", 2, 30);
        for (int y = 14; y < 34; y++) {
            for (int x = 2; x < 40; x++) {
                if (px[y * 64 + x] != 0) {
                    return null;
                }
            }
        }
        return "drawString painted nothing";
    }
}
