package javax.swing.plaf;

/**
 * Swing {@code ScrollBarUI}: the pluggable delegate a {@link javax.swing.JScrollBar}
 * paints and hit-tests through.
 *
 * <p>Hand-written rather than generated: {@link javax.swing.JScrollBar} stores its UI as
 * this type and the RuneLite look-and-feel provides subclasses of it
 * ({@code javax.swing.plaf.basic.BasicScrollBarUI} here), so the type has to exist as a
 * real platform interface rather than as a data-only stub. The JDK's class body is empty
 * too — the behaviour lives in {@code BasicScrollBarUI} — so this file carries no members
 * beyond inheriting {@link ComponentUI}.
 *
 * <p>Port deviation: none.
 */
public abstract class ScrollBarUI extends ComponentUI {
}
