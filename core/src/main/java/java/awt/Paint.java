package java.awt;

/**
 * The port's {@code java.awt.Paint}.
 *
 * <p>Hand-written because {@code Graphics2D.setPaint} takes one and the client
 * passes both {@link Color}s and {@link GradientPaint}s.
 *
 * <p>Marker interface for the fill source of a {@link Graphics2D}. Two kinds are
 * rasterised: {@link Color} (solid) and {@link GradientPaint} (two-stop linear,
 * see {@code Graphics.syncPaint}); any other {@code Paint} falls back to the
 * last solid colour, the documented deviation of this port.
 */
public interface Paint {
}
