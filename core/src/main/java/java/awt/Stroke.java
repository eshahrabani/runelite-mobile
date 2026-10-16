package java.awt;

/**
 * The port's {@code java.awt.Stroke}.
 *
 * <p>Hand-written for the same reason as the rest of this package: the desktop
 * toolkit is absent, and the only stroke the client builds or reads is
 * {@link BasicStroke}.
 *
 * <p>A marker interface: the JDK declares {@code createStrokedShape} here, but
 * this runtime strokes shapes directly in the rasteriser
 * ({@code Graphics2D.draw} consults {@link BasicStroke}'s width, caps and joins),
 * and no shipped code asks a stroke for its outline.
 */
public interface Stroke {
}
