package javax.imageio.stream;

import java.io.InputStream;

/**
 * ImageInputStream compatibility stub for Android runtime.
 */
public class MemoryCacheImageInputStream {
    private final InputStream stream;

    public MemoryCacheImageInputStream(InputStream stream) {
        this.stream = stream;
    }

    public InputStream getStream() {
        return stream;
    }
}
