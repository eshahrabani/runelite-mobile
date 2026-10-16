package javax.swing.plaf;

/**
 * Swing {@code ButtonUI}: the delegate a {@link javax.swing.AbstractButton} paints through.
 *
 * <p>Hand-written so the concrete {@link javax.swing.plaf.basic.BasicButtonUI} (and its
 * toggle-button subclass) have a real base class to extend; {@code AbstractButton} keeps a
 * field of this type.
 */
public abstract class ButtonUI extends ComponentUI {

    protected ButtonUI() {
        super();
    }
}
