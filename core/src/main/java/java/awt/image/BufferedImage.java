package java.awt.image;

import java.awt.Image;

/**
 * AWT BufferedImage compatibility stub for Android runtime.
 */
public class BufferedImage extends Image {
    public BufferedImage() {
        super();
    }

    public BufferedImage(int[] pixels, int width, int height) {
        super(pixels, width, height);
    }

    protected ColorModel colorModel;
    protected WritableRaster raster;

    public BufferedImage(ColorModel cm, WritableRaster raster, boolean isRasterPremultiplied, java.util.Hashtable<?,?> properties) {
        super(
            (raster != null && raster.getDataBuffer() instanceof DataBufferInt) 
                ? ((DataBufferInt) raster.getDataBuffer()).getData() 
                : null,
            (raster != null && raster.getSampleModel() != null) 
                ? raster.getSampleModel().getWidth() 
                : 0,
            (raster != null && raster.getSampleModel() != null) 
                ? raster.getSampleModel().getHeight() 
                : 0
        );
        this.colorModel = cm;
        this.raster = raster;
    }

    public ColorModel getColorModel() {
        return colorModel;
    }

    public WritableRaster getRaster() {
        return raster;
    }

    public void setRGB(int startX, int startY, int w, int h, int[] rgbArray, int offset, int scansize) {
        int[] dest = this.pixels;
        if (dest == null || rgbArray == null) return;
        for (int y = 0; y < h; y++) {
            int destY = startY + y;
            if (destY < 0 || destY >= this.height) continue;
            for (int x = 0; x < w; x++) {
                int destX = startX + x;
                if (destX < 0 || destX >= this.width) continue;
                dest[destY * this.width + destX] = rgbArray[offset + y * scansize + x];
            }
        }
    }

    public void setRGB(int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) return;
        if (this.pixels == null) return;
        this.pixels[y * this.width + x] = rgb;
    }
}
