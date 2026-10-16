package javax.swing.plaf.basic;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

/**
 * Basic look-and-feel {@code JToggleButton}/{@code JCheckBox}/{@code JRadioButton} painter.
 *
 * <p>Hand-written only so the toggle widgets have a UI of their own type installed at
 * construction. The painting is {@link BasicButtonUI}'s: the check/radio glyph is an
 * {@link javax.swing.Icon} on the button, so the ordinary icon+text layout draws it; the
 * selected/rollover/disabled icon lookup already happens in
 * {@code AbstractButton.getIconForState()}.
 */
public class BasicToggleButtonUI extends BasicButtonUI {

    public static ComponentUI createUI(JComponent c) {
        return new BasicToggleButtonUI();
    }

    public BasicToggleButtonUI() {
        super();
    }
}
