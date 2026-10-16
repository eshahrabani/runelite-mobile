package javax.swing;

import javax.swing.plaf.ButtonUI;

/**
 * Swing {@code JToggleButton}: a two-state button (the sidebar's plugin/nav buttons and
 * the config UI's {@code PluginToggleButton} are built from this, and callers depend on a
 * real {@code isSelected()}).
 *
 * <p>Hand-written: the JDK's {@code ToggleButtonModel} is reproduced here because the whole
 * point of a toggle button is that a click flips the selection — the flip happens in the
 * model's {@code setPressed(false)}, so a normal press/release both fires the action event
 * and changes the selected state exactly once.
 */
public class JToggleButton extends AbstractButton implements javax.accessibility.Accessible {

    /**
     * The model that turns a release into a selection toggle, as in the desktop JDK.
     */
    public static class ToggleButtonModel extends DefaultButtonModel {
        public ToggleButtonModel() {
        }

        @Override
        public void setPressed(boolean b) {
            if (isPressed() == b || !isEnabled()) {
                return;
            }
            if (!b && isArmed()) {
                setSelected(!isSelected());
            }
            super.setPressed(b);
        }
    }

    public JToggleButton() {
        this(null, null, false);
    }

    public JToggleButton(Icon icon) {
        this(null, icon, false);
    }

    public JToggleButton(Icon icon, boolean selected) {
        this(null, icon, selected);
    }

    public JToggleButton(String text) {
        this(text, null, false);
    }

    public JToggleButton(String text, boolean selected) {
        this(text, null, selected);
    }

    public JToggleButton(String text, Icon icon) {
        this(text, icon, false);
    }

    public JToggleButton(String text, Icon icon, boolean selected) {
        super();
        setModel(new ToggleButtonModel());
        init(text, icon);
        setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        setHorizontalTextPosition(CENTER);
        setVerticalTextPosition(BOTTOM);
        java.awt.Color background = UIManager.getColor("ToggleButton.background");
        if (background != null) {
            setBackground(background);
        }
        java.awt.Color foreground = UIManager.getColor("ToggleButton.foreground");
        if (foreground != null) {
            setForeground(foreground);
        }
        if (selected) {
            setSelected(true);
        }
    }

    public void setUI(ButtonUI ui) {
        super.setUI(ui);
    }

    @Override
    public ButtonUI getUI() {
        return (ButtonUI) super.getUI();
    }

    @Override
    public String getUIClassID() {
        return "ToggleButtonUI";
    }
}
