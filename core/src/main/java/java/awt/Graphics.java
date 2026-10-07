package java.awt;

import java.awt.image.ImageObserver;

/**
 * AWT Graphics compatibility stub for Android runtime.
 */
public class Graphics {
    protected int[] destPixels;
    protected int destWidth;
    protected int destHeight;

    public Graphics() {}

    public Graphics(Image image) {
        if (image != null) {
            this.destPixels = image.getPixels();
            this.destWidth = image.getWidth();
            this.destHeight = image.getHeight();
        }
    }

    public Graphics(int[] destPixels, int destWidth, int destHeight) {
        this.destPixels = destPixels;
        this.destWidth = destWidth;
        this.destHeight = destHeight;
    }

    public void drawLine(int x1, int y1, int x2, int y2) {}

    public void fillRect(int x, int y, int width, int height) {
        if (destPixels == null) return;
        // Simple fillRect implementation for debugging or background clearing
        int colorVal = 0xFF000000; // default black or similar
        for (int cy = y; cy < y + height; cy++) {
            if (cy < 0 || cy >= destHeight) continue;
            for (int cx = x; cx < x + width; cx++) {
                if (cx < 0 || cx >= destWidth) continue;
                destPixels[cy * destWidth + cx] = colorVal;
            }
        }
    }

    public void drawRect(int x, int y, int width, int height) {}

    public void drawString(String str, int x, int y) {}

    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        if (img == null) return false;
        return drawImage(img, x, y, img.getWidth(), img.getHeight(), observer);
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        if (img == null || destPixels == null || img.getPixels() == null) return false;

        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        int[] srcPixels = img.getPixels();
        if (srcWidth <= 0 || srcHeight <= 0 || width <= 0 || height <= 0) return true;

        int dstX0 = Math.max(0, x);
        int dstY0 = Math.max(0, y);
        int dstX1 = Math.min(destWidth, x + width);
        int dstY1 = Math.min(destHeight, y + height);
        if (dstX0 >= dstX1 || dstY0 >= dstY1) return true;

        if (width == srcWidth && height == srcHeight) {
            // 1:1 copy — the game's every-frame path (drawImage(img, 0, 0, null)
            // with the source already at the display size). One System.arraycopy
            // per row, a native runtime call even while the class is interpreted.
            int copyW = dstX1 - dstX0;
            for (int dy = dstY0; dy < dstY1; dy++) {
                System.arraycopy(srcPixels, (dy - y) * srcWidth + (dstX0 - x),
                                 destPixels, dy * destWidth + dstX0, copyW);
            }
            return true;
        }

        // Scaled (the splash logo): nearest-neighbour, stepping the source row
        // and column with accumulators instead of two divisions per pixel.
        int copyW = dstX1 - dstX0;
        int xDelta = dstX0 - x;
        int srcX0 = xDelta * srcWidth / width;
        int srcXAcc = xDelta * srcWidth % width;
        int yDelta = dstY0 - y;
        int srcY = yDelta * srcHeight / height;
        int srcYAcc = yDelta * srcHeight % height;
        int destRow = dstY0 * destWidth + dstX0;
        for (int dy = dstY0; dy < dstY1; dy++) {
            int srcRow = srcY * srcWidth;
            int srcX = srcX0;
            int acc = srcXAcc;
            for (int i = 0; i < copyW; i++) {
                destPixels[destRow + i] = srcPixels[srcRow + srcX];
                acc += srcWidth;
                while (acc >= width) {
                    acc -= width;
                    srcX++;
                }
            }
            destRow += destWidth;
            srcYAcc += srcHeight;
            while (srcYAcc >= height) {
                srcYAcc -= height;
                srcY++;
            }
        }
        return true;
    }

    public void setColor(Color c) {}

    public Color getColor() { return null; }

    public void setFont(Font f) {}

    public Font getFont() { return null; }

    public void dispose() {}

    public void clearRect(int x, int y, int width, int height) {
        fillRect(x, y, width, height);
    }

    public FontMetrics getFontMetrics() { return null; }

    public FontMetrics getFontMetrics(Font f) { return null; }

    public Rectangle getClipBounds() {
        return new Rectangle(0, 0, destWidth, destHeight);
    }
}
