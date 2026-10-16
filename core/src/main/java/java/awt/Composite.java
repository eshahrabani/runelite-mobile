package java.awt;

/**
 * The port's {@code java.awt.Composite}.
 *
 * <p>Hand-written because {@code Graphics2D.setComposite} takes one and the client
 * only ever passes an {@link AlphaComposite}.
 *
 * <p>Only {@link AlphaComposite} is used: it carries the Porter-Duff rule and the
 * extra alpha that {@code Graphics.blendPixel} applies. The JDK's
 * {@code CompositeContext} pipeline does not exist here, so this stays a marker
 * interface.
 */
public interface Composite {
}
