package javax.swing;

import javax.swing.plaf.ButtonUI;

/**
 * Swing {@code JButton}: a push button.
 *
 * <p>Hand-written because the config panels and dialogs are full of them, and a tap has to
 * fire exactly one {@code ActionEvent}. The behaviour lives in {@link AbstractButton}; this
 * class only picks the default UI ({@code BasicButtonUI}) and the centring defaults, and
 * keeps the {@code setUI(ButtonUI)} overload the shipped jars link against.
 */
public class JButton extends AbstractButton implements javax.accessibility.Accessible {

    public JButton() {
        this(null, null);
    }

    public JButton(String text) {
        this(text, null);
    }

    public JButton(Icon icon) {
        this(null, icon);
    }

    public JButton(String text, Icon icon) {
        super();
        setModel(new DefaultButtonModel());
        init(text, icon);
        setUI(new javax.swing.plaf.basic.BasicButtonUI());
        setAlignmentY(0.5f);
        java.awt.Color background = UIManager.getColor("Button.background");
        if (background != null) {
            setBackground(background);
        }
        java.awt.Color foreground = UIManager.getColor("Button.foreground");
        if (foreground != null) {
            setForeground(foreground);
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
        return "ButtonUI";
    }
}
