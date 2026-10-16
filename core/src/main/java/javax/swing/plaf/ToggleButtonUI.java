package javax.swing.plaf;

/**
 * Swing {@code ToggleButtonUI}: the {@code ButtonUI} variant installed by
 * {@link javax.swing.JToggleButton} (and therefore by {@code JCheckBox}/{@code JRadioButton}).
 *
 * <p>Hand-written because the shipped loot-tracker panel installs a
 * {@code ToggleButtonUI} subclass and {@code BasicToggleButtonUI} needs a base of this exact
 * type for the {@code setUI(ToggleButtonUI)} overload to resolve.
 */
public abstract class ToggleButtonUI extends ButtonUI {

    protected ToggleButtonUI() {
        super();
    }
}
