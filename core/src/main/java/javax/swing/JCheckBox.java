package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/**
 * Swing {@code JCheckBox}: a toggle button with a check-box glyph.
 *
 * <p>Hand-written: the shipped config panels are check boxes, and the port has to draw the
 * box itself (there is no {@code CheckBox.icon} image resource). A synthetic {@link Icon}
 * sized from {@code CheckBox.icon.size} (22) and filled from {@code CheckBox.icon.background}
 * is installed as the default icon, so the ordinary button layout/paint places it before the
 * text. {@code setIcon/setSelectedIcon/setDisabledIcon} still take precedence: the glyph icon
 * only reports the checked state when it is the icon actually in use.
 */
public class JCheckBox extends JToggleButton implements javax.accessibility.Accessible {

    public JCheckBox() {
        this(null, false);
    }

    public JCheckBox(String text) {
        this(text, false);
    }

    public JCheckBox(String text, boolean selected) {
        super(text, null, selected);
        setIcon(new CheckBoxIcon());
        setHorizontalTextPosition(TRAILING);
        setVerticalTextPosition(CENTER);
        setHorizontalAlignment(LEADING);
        // The glyph is the only painted part; the row stays transparent over the panel.
        setContentAreaFilled(false);
    }

    /**
     * The default check-box glyph: a square filled with {@code CheckBox.icon.background} and
     * outlined in the border colour, with a check mark when the model is selected. The size
     * comes from {@code CheckBox.icon.size}; the exact antialiasing of the mark is the obvious
     * hard-edged polyline (the desktop LAF's pixels are not specified).
     */
    static class CheckBoxIcon implements Icon {
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

            int x0 = x;
            int y0 = y;
            g.setColor(background != null ? background : Color.DARK_GRAY);
            g.fillRect(x0, y0, size, size);
            g.setColor(border != null ? border : Color.GRAY);
            g.drawRect(x0, y0, size - 1, size - 1);

            if (selected) {
                g.setColor(enabled && foreground != null ? foreground : Color.GRAY);
                int start = x0 + 5;
                int end = x0 + size - 6;
                int midY = y0 + size / 2;
                // An 8x8-ish check mark drawn as two strokes.
                g.drawLine(start, midY, start + 3, midY + 3);
                g.drawLine(start + 3, midY + 3, end, y0 + 5);
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
