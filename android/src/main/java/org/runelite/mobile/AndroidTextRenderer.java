package org.runelite.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.runelite.mobile.bridge.TextBridge;

/**
 * Android implementation of {@link TextBridge.TextRenderer}.
 *
 * <p>Core's {@code java.awt.Font}/{@code FontMetrics} hold the TTF bytes and
 * forward every measurement or draw through {@link TextBridge}; this class
 * turns those bytes into an {@code android.graphics.Typeface} and rasterises
 * text with a {@code Paint}. Text is drawn into a single reusable scratch
 * {@link Bitmap}, then composited source-over into the destination ARGB array
 * that backs the game frame.
 *
 * <p>Thread safety: this class is called from the client thread only. It makes
 * no Android UI calls (no {@code View}/{@code Canvas}-from-a-View) and guards
 * its paint cache and scratch bitmap with a private lock.
 */
public final class AndroidTextRenderer implements TextBridge.TextRenderer {

    /** Scratch bitmap hard limits; text wider than this is clipped. */
    private static final int MAX_SCRATCH_WIDTH = 1024;
    private static final int MAX_SCRATCH_HEIGHT = 64;

    private final Object lock = new Object();

    /** One paint per (family, style, sizePx). */
    private final HashMap<String, Handle> cache = new HashMap<>();

    private Bitmap scratch;
    private Canvas scratchCanvas;
    private int[] scratchPixels;

    /** Opaque font handle: the platform typeface plus its configured paint. */
    private static final class Handle {
        final Typeface typeface;
        final Paint paint;

        Handle(Typeface typeface, Paint paint) {
            this.typeface = typeface;
            this.paint = paint;
        }
    }

    @Override
    public Object createFont(byte[] ttf, int style, int sizePx, String familyName) {
        int size = sizePx > 0 ? sizePx : 1;
        String key = familyName + "\u0000" + style + "\u0000" + size;
        synchronized (lock) {
            Handle existing = cache.get(key);
            if (existing != null) {
                return existing;
            }
            Typeface typeface = buildTypeface(ttf, style, familyName);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(size);
            paint.setTypeface(typeface);
            Handle handle = new Handle(typeface, paint);
            cache.put(key, handle);
            return handle;
        }
    }

    @Override
    public int stringWidth(Object font, String text) {
        Handle handle = asHandle(font);
        if (handle == null || text == null) {
            return text == null ? 0 : text.length() * 6;
        }
        synchronized (lock) {
            return Math.round(handle.paint.measureText(text));
        }
    }

    @Override
    public int height(Object font) {
        Handle handle = asHandle(font);
        if (handle == null) {
            return 0;
        }
        synchronized (lock) {
            Paint.FontMetrics fm = handle.paint.getFontMetrics();
            return Math.round(fm.bottom - fm.top);
        }
    }

    @Override
    public int ascent(Object font) {
        Handle handle = asHandle(font);
        if (handle == null) {
            return 0;
        }
        synchronized (lock) {
            return -Math.round(handle.paint.getFontMetrics().ascent);
        }
    }

    @Override
    public int descent(Object font) {
        Handle handle = asHandle(font);
        if (handle == null) {
            return 0;
        }
        synchronized (lock) {
            return Math.round(handle.paint.getFontMetrics().descent);
        }
    }

    @Override
    public void drawString(Object font, int[] dest, int destW, int destH, String text,
                           int x, int y, int argb, boolean shadow) {
        if (dest == null || text == null || text.isEmpty() || destW <= 0 || destH <= 0) {
            return;
        }
        Handle handle = asHandle(font);
        if (handle == null) {
            return;
        }
        synchronized (lock) {
            Paint.FontMetrics fm = handle.paint.getFontMetrics();
            int ascent = -Math.round(fm.ascent);
            int descent = Math.round(fm.descent);
            int needW = (int) Math.ceil(handle.paint.measureText(text)) + 2;
            int needH = ascent + descent + 2;

            Bitmap bitmap = ensureScratch(needW, needH);
            if (bitmap == null) {
                return;
            }
            int sw = bitmap.getWidth();
            int sh = bitmap.getHeight();
            Canvas canvas = scratchCanvas;
            bitmap.eraseColor(0);

            float baseline = ascent;
            if (shadow) {
                handle.paint.setColor(0xFF000000);
                canvas.drawText(text, 1f, baseline + 1f, handle.paint);
            }
            handle.paint.setColor(argb);
            canvas.drawText(text, 0f, baseline, handle.paint);

            if (scratchPixels == null || scratchPixels.length < sw * sh) {
                scratchPixels = new int[sw * sh];
            }
            bitmap.getPixels(scratchPixels, 0, sw, 0, 0, sw, sh);

            int destX = x;
            int destY = y - ascent;
            composite(scratchPixels, sw, sh, dest, destW, destH, destX, destY);
        }
    }

    /** Composite the scratch ARGB pixels into {@code dest} with source-over. */
    private void composite(int[] src, int sw, int sh, int[] dest, int destW, int destH,
                           int destX, int destY) {
        int startX = 0;
        if (destX < 0) {
            startX = -destX;
        }
        int startY = 0;
        if (destY < 0) {
            startY = -destY;
        }
        int endX = Math.min(sw, destW - destX);
        int endY = Math.min(sh, destH - destY);
        for (int sy = startY; sy < endY; sy++) {
            int srcRow = sy * sw;
            int destRow = (destY + sy) * destW;
            for (int sx = startX; sx < endX; sx++) {
                int s = src[srcRow + sx];
                int sa = s >>> 24;
                if (sa == 0) {
                    continue;
                }
                int di = destRow + destX + sx;
                if (sa == 255) {
                    dest[di] = s;
                    continue;
                }
                int d = dest[di];
                int da = d >>> 24;
                float a = sa / 255f;
                float inv = 1f - a;
                int r = Math.round(((s >> 16) & 0xff) * a + ((d >> 16) & 0xff) * inv);
                int g = Math.round(((s >> 8) & 0xff) * a + ((d >> 8) & 0xff) * inv);
                int b = Math.round((s & 0xff) * a + (d & 0xff) * inv);
                int oa = Math.round(sa + da * inv);
                dest[di] = (oa << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    private Bitmap ensureScratch(int needW, int needH) {
        int wantW = Math.min(MAX_SCRATCH_WIDTH, Math.max(needW, 256));
        int wantH = Math.min(MAX_SCRATCH_HEIGHT, Math.max(needH, 32));
        if (scratch != null && wantW <= scratch.getWidth() && wantH <= scratch.getHeight()) {
            return scratch;
        }
        int newW = scratch == null ? wantW : Math.max(wantW, scratch.getWidth());
        int newH = scratch == null ? wantH : Math.max(wantH, scratch.getHeight());
        try {
            scratch = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);
            scratchCanvas = new Canvas(scratch);
            scratchPixels = new int[newW * newH];
        } catch (Throwable t) {
            scratch = null;
            scratchCanvas = null;
            scratchPixels = null;
            return null;
        }
        return scratch;
    }

    /**
     * Directory the TTF bytes are materialised into. {@code Typeface.Builder} only reads
     * files (or file descriptors) - there is no ByteBuffer constructor - so the raw TTF
     * the core-side Font carries has to be written out once. Installed by MainActivity.
     */
    private static volatile java.io.File fontDir;

    public static void setFontDir(java.io.File dir) {
        fontDir = dir;
    }

    private static Typeface buildTypeface(byte[] ttf, int style, String familyName) {
        Typeface typeface = null;
        if (ttf != null && ttf.length > 0) {
            try {
                java.io.File file = materialise(ttf);
                if (file != null) {
                    typeface = new Typeface.Builder(file)
                        .setWeight(style == java.awt.Font.BOLD ? 700 : 400)
                        .setItalic(style == java.awt.Font.ITALIC)
                        .build();
                }
            } catch (Throwable t) {
                typeface = null;
            }
        }
        if (typeface == null) {
            try {
                typeface = Typeface.create(familyName, style);
            } catch (Throwable t) {
                typeface = null;
            }
        }
        if (typeface == null) {
            typeface = Typeface.DEFAULT;
        }
        return typeface;
    }

    /** Writes the TTF bytes to a stable, content-addressed file in the cache dir. */
    private static java.io.File materialise(byte[] ttf) throws java.io.IOException {
        java.io.File dir = fontDir;
        if (dir == null) {
            return null;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        String name = "rl-font-" + Integer.toHexString(java.util.Arrays.hashCode(ttf)) + "-" + ttf.length + ".ttf";
        java.io.File out = new java.io.File(dir, name);
        if (!out.isFile() || out.length() != ttf.length) {
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                os.write(ttf);
            }
        }
        return out;
    }

    private static Handle asHandle(Object font) {
        return font instanceof Handle ? (Handle) font : null;
    }
}
