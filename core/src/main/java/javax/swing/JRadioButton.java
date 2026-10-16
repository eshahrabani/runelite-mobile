package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/**
 * Swing {@code JRadioButton}: a toggle button with a radio glyph, used with a
 * {@link ButtonGroup} for one-of-many rows (the loot-tracker panel).
 *
 * <p>Hand-written for the same reason as {@link JCheckBox}: a synthetic {@link Icon} draws
 * the circle-and-dot from {@code CheckBox.icon.background}/{@code size}, and the ordinary
 * button layout/paint places it before the text. Client-set icons still win.
 */
public class JRadioButton extends JToggleButton implements javax.accessibility.Accessible {

    public JRadioButton() {
        this(null, false);
    }

    public JRadioButton(String text) {
        this(text, false);
    }

    public JRadioButton(String text, boolean selected) {
        super(text, null, selected);
        setIcon(new RadioButtonIcon());
        setHorizontalTextPosition(TRAILING);
        setVerticalTextPosition(CENTER);
        setHorizontalAlignment(LEADING);
        setContentAreaFilled(false);
    }

    /**
     * The default radio glyph: a circle filled with {@code CheckBox.icon.background} and
     * outlined in the border colour, with a filled centre dot when selected.
     */
    static class RadioButtonIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int size = UIManager.getInt("CheckBox.icon.size");
            if (size <= 0) {
                size = 22;
            }
            Color background = UIManager.getColor("CheckBox.icon.background");
            if (background == null) {
                background = UIManager.getColor("Button.background");
            }
            Color border = UIManager.getColor("Button.borderColor");
            if (border == null) {
                border = UIManager.getColor("Component.borderColor");
            }
            Color foreground = c != null ? c.getForeground() : null;
            if (foreground == null) {
                foreground = UIManager.getColor("Button.foreground");
            }
            boolean selected = c instanceof AbstractButton && ((AbstractButton) c).isSelected();
            boolean enabled = c == null || c.isEnabled();

            g.setColor(background != null ? background : Color.DARK_GRAY);
            g.fillOval(x, y, size, size);
            g.setColor(border != null ? border : Color.GRAY);
            g.drawOval(x, y, size - 1, size - 1);

            if (selected) {
                g.setColor(enabled && foreground != null ? foreground : Color.GRAY);
                int inset = size / 3;
                g.fillOval(x + inset, y + inset, size - 2 * inset, size - 2 * inset);
            }
        }

        @Override
        public int getIconWidth() {
            int size = UIManager.getInt("CheckBox.icon.size");
            return size > 0 ? size : 22;
        }

        @Override
        public int getIconHeight() {
            return getIconWidth();
        }
    }
}
