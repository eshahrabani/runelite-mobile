package javax.imageio;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * ImageReader compatibility stub for Android runtime.
 */
public abstract class ImageReader {
    protected ImageReader() {}

    public abstract void setInput(Object input, boolean seekForwardOnly);

    public abstract BufferedImage read(int imageIndex) throws IOException;
}
