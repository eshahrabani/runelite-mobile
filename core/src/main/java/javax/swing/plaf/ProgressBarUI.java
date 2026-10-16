package javax.swing.plaf;

/**
 * Swing {@code ProgressBarUI}: the delegate a {@link javax.swing.JProgressBar} paints through.
 *
 * <p>Hand-written: the splash screen installs a {@code BasicProgressBarUI} subclass that
 * overrides {@code getSelectionBackground()/getSelectionForeground()}, so both the base class
 * and that protected hook have to exist.
 */
public abstract class ProgressBarUI extends ComponentUI {

    protected ProgressBarUI() {
        super();
    }
}
