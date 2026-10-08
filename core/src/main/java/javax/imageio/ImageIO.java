package javax.imageio;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
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

    /**
     * Convenience method used by the client: ImageIO.read(File). Delegates to
     * {@link #read(java.io.InputStream)} so there is exactly one decode path.
     */
    public static BufferedImage read(java.io.File input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }
        try (java.io.InputStream is = new java.io.FileInputStream(input)) {
            return read(is);
        }
    }

    /**
     * Convenience method used by the client: ImageIO.write(RenderedImage, String, File).
     *
     * <p>This has a live caller on the port - RuneLite's
     * {@code ImageCapture.saveScreenshot}, which RaidsPlugin reaches - so it is
     * real rather than a no-op. Like the read path it goes through Android's
     * bitmap codec by reflection (core must not reference {@code android.*}):
     * the image's ARGB pixels become a mutable ARGB_8888 {@code Bitmap}, which is
     * compressed with the {@code Bitmap.CompressFormat} matching {@code formatName}.
     *
     * <p>Returns false - the documented "no writer found for this format" answer -
     * for a format Android has no encoder for or an image that is not a
     * pixel-backed {@link BufferedImage}, and never leaves a zero-byte or
     * placeholder file behind (the encoded bytes are buffered and only written to
     * the file once compression produced output; an I/O failure still throws, as
     * the JDK contract requires).
     */
    public static boolean write(RenderedImage image, String formatName, java.io.File output) throws IOException {
        if (image == null || formatName == null || output == null) {
            throw new IllegalArgumentException("image == null! formatName == null! output == null!");
        }
        if (!(image instanceof BufferedImage)) {
            return false;
        }
        BufferedImage buffered = (BufferedImage) image;
        int width = buffered.getWidth();
        int height = buffered.getHeight();
        if (width <= 0 || height <= 0 || buffered.getPixels() == null) {
            return false;
        }
        // getRGB, not getPixels(): it runs the pixels through the colour model, so
        // an opaque image with a 3-mask model (the game frame) comes back as opaque
        // ARGB instead of the alpha-0 ints it stores.
        int[] pixels = buffered.getRGB(0, 0, width, height, null, 0, width);
        if (pixels == null) {
            return false;
        }
        byte[] bytes;
        try {
            bytes = encode(pixels, width, height, formatName);
        } catch (Exception e) {
            System.err.println("ImageIO.write: failed to encode image: " + e.getMessage());
            return false;
        }
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        try (java.io.OutputStream os = new java.io.FileOutputStream(output)) {
            os.write(bytes);
        }
        return true;
    }

    /**
     * Encodes ARGB pixels with Android's bitmap codec through reflection, or
     * returns null when the format has no encoder. Separated so the reflective
     * part can fail with {@code false} while a genuine file-write failure still
     * propagates as {@link IOException}.
     */
    private static byte[] encode(int[] pixels, int width, int height, String formatName) throws Exception {
        Object compressFormat = compressFormat(formatName);
        if (compressFormat == null) {
            return null;
        }
        Class<?> bitmapClass = Class.forName("android.graphics.Bitmap");
        Class<?> configClass = Class.forName("android.graphics.Bitmap$Config");
        Object bitmap = null;
        try {
            Object config = configClass.getField("ARGB_8888").get(null);
            bitmap = bitmapClass
                    .getMethod("createBitmap", int[].class, int.class, int.class, int.class, int.class, configClass)
                    .invoke(null, pixels, 0, width, width, height, config);
            if (bitmap == null) {
                return null;
            }
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            Boolean compressed = (Boolean) bitmapClass
                    .getMethod("compress", Class.forName("android.graphics.Bitmap$CompressFormat"), int.class,
                            java.io.OutputStream.class)
                    .invoke(bitmap, compressFormat, 100, buffer);
            if (compressed == null || !compressed) {
                return null;
            }
            byte[] bytes = buffer.toByteArray();
            return bytes.length == 0 ? null : bytes;
        } finally {
            if (bitmap != null) {
                try {
                    bitmapClass.getMethod("recycle").invoke(bitmap);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * The {@code Bitmap.CompressFormat} constant for {@code formatName}
     * (PNG/JPEG/WEBP/JPG, case-insensitive), or null when Android has no encoder.
     */
    private static Object compressFormat(String formatName) throws Exception {
        String name = formatName.toUpperCase(java.util.Locale.ROOT);
        if ("JPG".equals(name)) {
            name = "JPEG";
        }
        for (Object constant : Class.forName("android.graphics.Bitmap$CompressFormat").getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }
}
