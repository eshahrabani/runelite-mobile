package javax.swing.plaf;

/**
 * Swing {@code LabelUI}: the delegate a {@link javax.swing.JLabel} paints through.
 *
 * <p>Hand-written because {@link javax.swing.plaf.basic.BasicLabelUI} extends this class and
 * RuneLite installs a {@code BasicLabelUI} subclass ({@code JShadowedLabelUI}) to draw
 * shadowed text, so the abstract base has to exist outside the generated stubs.
 */
public abstract class LabelUI extends ComponentUI {

    protected LabelUI() {
        super();
    }
}
