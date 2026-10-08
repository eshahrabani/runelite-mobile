package java.awt.image;

/**
 * AWT RenderedImage compatibility stub for Android runtime.
 *
 * <p>Every method is a {@code default}, like {@code java.awt.Shape}, so the one
 * implementor in this port ({@link BufferedImage}) only overrides what it can
 * answer exactly and stays verifiable without implementing anything new.
 *
 * <p>The pre-compiled RuneLite client never calls a member of this interface: it
 * only needs the type to exist so that
 * {@code ImageIO.write(RenderedImage, String, File)} links, and
 * {@link BufferedImage} must be assignable to it because the client's call site
 * ({@code ImageCapture.saveScreenshot}) passes a {@code BufferedImage} where the
 * JDK declared a {@code RenderedImage} - without the relationship the verifier
 * rejects that method on the device. The declared members are therefore only the
 * plausible subset {@link BufferedImage} already answers.
 */
public interface RenderedImage {
    /** Image width, or 0 when the implementor cannot answer. */
    default int getWidth() {
        return 0;
    }

    /** Image height, or 0 when the implementor cannot answer. */
    default int getHeight() {
        return 0;
    }

    /** Colour model, or null when the implementor has none. */
    default ColorModel getColorModel() {
        return null;
    }
}
