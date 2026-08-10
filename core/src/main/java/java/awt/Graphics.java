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

        for (int dy = 0; dy < height; dy++) {
            int destY = y + dy;
            if (destY < 0 || destY >= destHeight) continue;

            int srcY = dy * srcHeight / height;
            if (srcY < 0 || srcY >= srcHeight) continue;

            for (int dx = 0; dx < width; dx++) {
                int destX = x + dx;
                if (destX < 0 || destX >= destWidth) continue;

                int srcX = dx * srcWidth / width;
                if (srcX < 0 || srcX >= srcWidth) continue;

                destPixels[destY * destWidth + destX] = srcPixels[srcY * srcWidth + srcX];
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
