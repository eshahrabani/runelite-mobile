package java.awt;

/**
 * AWT Paint compatibility stub for Android runtime.
 *
 * <p>Marker interface for the fill source of a {@link Graphics2D}. The mobile
 * surface only implements solid colours; {@link GradientPaint} is accepted so
 * overlay code links, but gradients are ignored when drawing.
 */
public interface Paint {
}
