package java.awt.image;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;

/**
 * AWT BufferedImage compatibility stub for Android runtime.
 *
 * <p>Backed by a non-premultiplied ARGB {@code int[]} shared with its
 * {@link WritableRaster} and {@code DataBufferInt}. {@link #getGraphics()}
 * returns a {@code Graphics2D} bound to those pixels, which is how the
 * injected client and {@code Hooks} render overlays straight into the frame.
 *
 * <p>{@code hasAlpha} mirrors the colour model's alpha component: the game
 * frame's {@code DirectColorModel} has no alpha mask, so its image stays on
 * {@code Graphics.drawImage}'s opaque arraycopy fast path, while sprites built
 * as {@code TYPE_INT_ARGB} blend.
 */
public class BufferedImage extends Image implements java.awt.image.RenderedImage {
    public static final int TYPE_INT_RGB = 1;
    public static final int TYPE_INT_ARGB = 2;
    public static final int TYPE_INT_ARGB_PRE = 3;
    public static final int TYPE_INT_BGR = 4;
    public static final int TYPE_3BYTE_BGR = 5;
    public static final int TYPE_4BYTE_ABGR = 6;
    public static final int TYPE_4BYTE_ABGR_PRE = 7;
    public static final int TYPE_USHORT_565_RGB = 8;
    public static final int TYPE_BYTE_GRAY = 10;
    public static final int TYPE_BYTE_BINARY = 12;
    public static final int TYPE_BYTE_INDEXED = 13;

    protected ColorModel colorModel;
    protected WritableRaster raster;
    private final int imageType;

    public BufferedImage() {
        this(0, 0, TYPE_INT_ARGB);
    }

    public BufferedImage(int width, int height, int imageType) {
        super(null, width, height);
        this.imageType = imageType;
        boolean alpha = imageType == TYPE_INT_ARGB || imageType == TYPE_INT_ARGB_PRE
                || imageType == TYPE_4BYTE_ABGR || imageType == TYPE_4BYTE_ABGR_PRE;
        int[] pixels = new int[Math.max(0, width * height)];
        this.pixels = pixels;
        this.hasAlpha = alpha;
        this.colorModel = new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF,
                alpha ? 0xFF000000 : 0);
        SampleModel sm = colorModel.createCompatibleSampleModel(Math.max(1, width), Math.max(1, height));
        this.raster = Raster.createWritableRaster(sm, new DataBufferInt(pixels, pixels.length), new Point(0, 0));
    }

    public BufferedImage(ColorModel cm, WritableRaster raster, boolean isRasterPremultiplied,
                         java.util.Hashtable<?, ?> properties) {
        super(null, rasterWidth(raster), rasterHeight(raster));
        this.colorModel = cm;
        this.raster = raster;
        this.imageType = cm != null && cm.hasAlpha() ? TYPE_INT_ARGB : TYPE_INT_RGB;
        this.hasAlpha = cm != null && cm.hasAlpha();
        if (raster != null && raster.getDataBuffer() instanceof DataBufferInt) {
            this.pixels = ((DataBufferInt) raster.getDataBuffer()).getData();
        }
    }

    /** Legacy convenience constructor over a caller-supplied pixel array. */
    public BufferedImage(int[] pixels, int width, int height) {
        super(pixels, width, height);
        this.imageType = TYPE_INT_ARGB;
        this.hasAlpha = true;
        this.colorModel = new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
        SampleModel sm = colorModel.createCompatibleSampleModel(Math.max(1, width), Math.max(1, height));
        this.raster = Raster.createWritableRaster(sm, new DataBufferInt(pixels, pixels == null ? 0 : pixels.length),
                new Point(0, 0));
    }

    private static int rasterWidth(WritableRaster raster) {
        return raster != null && raster.getSampleModel() != null ? raster.getSampleModel().getWidth() : 0;
    }

    private static int rasterHeight(WritableRaster raster) {
        return raster != null && raster.getSampleModel() != null ? raster.getSampleModel().getHeight() : 0;
    }

    public ColorModel getColorModel() {
        return colorModel;
    }

    public WritableRaster getRaster() {
        return raster;
    }

    public int getType() {
        return imageType;
    }

    public Graphics getGraphics() {
        return new Graphics2D(this);
    }

    public Graphics2D createGraphics() {
        return new Graphics2D(this);
    }

    public DataBuffer getDataBuffer() {
        return raster == null ? null : raster.getDataBuffer();
    }

    public int getRGB(int x, int y) {
        if (pixels == null || x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        int p = pixels[y * width + x];
        return colorModel != null ? colorModel.getRGB(p) : p;
    }

    public int[] getRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
        if (rgbArray == null) {
            rgbArray = new int[w * h];
        }
        if (pixels == null) {
            return rgbArray;
        }
        for (int y = 0; y < h; y++) {
            int srcY = startY + y;
            for (int x = 0; x < w; x++) {
                int srcX = startX + x;
                int p = (srcX < 0 || srcY < 0 || srcX >= width || srcY >= height) ? 0 : pixels[srcY * width + srcX];
                rgbArray[offset + y * scansize + x] = colorModel != null ? colorModel.getRGB(p) : p;
            }
        }
        return rgbArray;
    }

    public void setRGB(int x, int y, int rgb) {
        if (pixels == null || x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        pixels[y * width + x] = rgb;
    }

    public void setRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
        if (pixels == null || rgbArray == null) {
            return;
        }
        for (int y = 0; y < h; y++) {
            int dstY = startY + y;
            if (dstY < 0 || dstY >= height) {
                continue;
            }
            for (int x = 0; x < w; x++) {
                int dstX = startX + x;
                if (dstX < 0 || dstX >= width) {
                    continue;
                }
                pixels[dstY * width + dstX] = rgbArray[offset + y * scansize + x];
            }
        }
    }

    public WritableRaster copyData(WritableRaster out) {
        if (out == null || pixels == null || !(out.getDataBuffer() instanceof DataBufferInt)) {
            return out;
        }
        int[] dest = ((DataBufferInt) out.getDataBuffer()).getData();
        int n = Math.min(dest.length, pixels.length);
        System.arraycopy(pixels, 0, dest, 0, n);
        return out;
    }

    public BufferedImage getSubimage(int x, int y, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, imageType);
        if (pixels == null) {
            return out;
        }
        for (int yy = 0; yy < h; yy++) {
            int srcY = y + yy;
            if (srcY < 0 || srcY >= height) {
                continue;
            }
            for (int xx = 0; xx < w; xx++) {
                int srcX = x + xx;
                if (srcX < 0 || srcX >= width) {
                    continue;
                }
                out.setRGB(xx, yy, pixels[srcY * width + srcX]);
            }
        }
        return out;
    }

    public Image getScaledInstance(int width, int height, int hints) {
        BufferedImage out = new BufferedImage(Math.max(1, width), Math.max(1, height), TYPE_INT_ARGB);
        if (pixels == null || this.width <= 0 || this.height <= 0) {
            return out;
        }
        for (int dy = 0; dy < height; dy++) {
            int sy = dy * this.height / height;
            for (int dx = 0; dx < width; dx++) {
                int sx = dx * this.width / width;
                out.setRGB(dx, dy, pixels[sy * this.width + sx]);
            }
        }
        return out;
    }
}
