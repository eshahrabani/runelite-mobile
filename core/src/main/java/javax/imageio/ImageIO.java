package javax.imageio;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

/**
 * ImageIO compatibility stub for Android runtime.
 */
public class ImageIO {
    public static Iterator<ImageReader> getImageReadersByFormatName(String formatName) {
        ImageReader reader = new ImageReader() {
            private Object input;

            @Override
            public void setInput(Object input, boolean seekForwardOnly) {
                this.input = input;
            }

            @Override
            public BufferedImage read(int imageIndex) throws IOException {
                if (input instanceof javax.imageio.stream.MemoryCacheImageInputStream) {
                    java.io.InputStream is = ((javax.imageio.stream.MemoryCacheImageInputStream) input).getStream();
                    if (is != null) {
                        try {
                            Class<?> bitmapFactoryClass = Class.forName("android.graphics.BitmapFactory");
                            Class<?> bitmapClass = Class.forName("android.graphics.Bitmap");

                            java.lang.reflect.Method decodeStreamMethod = bitmapFactoryClass.getMethod("decodeStream", java.io.InputStream.class);
                            Object bitmap = decodeStreamMethod.invoke(null, is);

                            if (bitmap != null) {
                                int width = (Integer) bitmapClass.getMethod("getWidth").invoke(bitmap);
                                int height = (Integer) bitmapClass.getMethod("getHeight").invoke(bitmap);

                                int[] pixels = new int[width * height];
                                java.lang.reflect.Method getPixelsMethod = bitmapClass.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class, int.class, int.class);
                                getPixelsMethod.invoke(bitmap, pixels, 0, width, 0, 0, width, height);

                                try {
                                    bitmapClass.getMethod("recycle").invoke(bitmap);
                                } catch (Exception ignored) {}

                                return new BufferedImage(pixels, width, height);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to decode image via reflection: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                // Fallback to dummy empty BufferedImage
                return new BufferedImage();
            }
        };
        return Collections.singletonList(reader).iterator();
    }

    public static void setUseCache(boolean useCache) {}

    /**
     * Convenience method used by the client: ImageIO.read(InputStream).
     * Decodes PNG/JPEG via Android's BitmapFactory (reflection: core module
     * must not reference android.* directly).
     */
    public static BufferedImage read(java.io.InputStream is) throws IOException {
        if (is == null) {
            throw new IllegalArgumentException("input == null!");
        }
        try {
            Class<?> bitmapFactoryClass = Class.forName("android.graphics.BitmapFactory");
            Class<?> bitmapClass = Class.forName("android.graphics.Bitmap");

            java.lang.reflect.Method decodeStreamMethod = bitmapFactoryClass.getMethod("decodeStream", java.io.InputStream.class);
            Object bitmap = decodeStreamMethod.invoke(null, is);

            if (bitmap != null) {
                int width = (Integer) bitmapClass.getMethod("getWidth").invoke(bitmap);
                int height = (Integer) bitmapClass.getMethod("getHeight").invoke(bitmap);

                int[] pixels = new int[width * height];
                java.lang.reflect.Method getPixelsMethod = bitmapClass.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class, int.class, int.class);
                getPixelsMethod.invoke(bitmap, pixels, 0, width, 0, 0, width, height);

                try {
                    bitmapClass.getMethod("recycle").invoke(bitmap);
                } catch (Exception ignored) {}

                return new BufferedImage(pixels, width, height);
            }
        } catch (Exception e) {
            System.err.println("ImageIO.read: failed to decode via BitmapFactory: " + e.getMessage());
        }
        return new BufferedImage();
    }
}
