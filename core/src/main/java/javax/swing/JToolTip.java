package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

/**
 * Swing {@code JToolTip}: the small dark label the long-press/tooltip path raises for a
 * component with a {@code setToolTipText}.
 *
 * <p>Hand-written: the port never shows the desktop hover popup, but the app's long-press
 * bridge does build and paint one, so it has to size and draw itself from the
 * {@code ToolTip.background}/{@code ToolTip.foreground} defaults. There is no
 * {@code ToolTipUI} in this slice; the component paints its own box.
 */
public class JToolTip extends JComponent implements javax.accessibility.Accessible {

    private String tipText;
    private JComponent component;

    public JToolTip() {
        super();
        setOpaque(true);
        Color background = UIManager.getColor("ToolTip.background");
        setBackground(background != null ? background : new Color(46, 46, 46));
        Color foreground = UIManager.getColor("ToolTip.foreground");
        if (foreground != null) {
            setForeground(foreground);
        }
    }

    public String getTipText() {
        return tipText;
    }

    public void setTipText(String tipText) {
        String old = this.tipText;
        this.tipText = tipText;
        firePropertyChange("tiptext", old, tipText);
        revalidate();
        repaint();
    }

    public JComponent getComponent() {
        return component;
    }

    public void setComponent(JComponent c) {
        JComponent old = this.component;
        this.component = c;
        firePropertyChange("component", old, c);
    }

    @Override
    public Dimension getPreferredSize() {
        Font font = getFont();
        FontMetrics metrics = getFontMetrics(font);
        String text = tipText == null ? "" : tipText;
        int width = metrics != null ? metrics.stringWidth(text) : text.length() * 6;
        int height = metrics != null ? metrics.getHeight() : 14;
        return new Dimension(width + 8, height + 4);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = getBackground();
        g.setColor(background != null ? background : Color.DARK_GRAY);
        g.fillRect(0, 0, getWidth(), getHeight());

        Color foreground = getForeground();
        g.setColor(foreground != null ? foreground : Color.LIGHT_GRAY);
        g.fillRect(0, 0, getWidth(), 1);
        g.fillRect(0, getHeight() - 1, getWidth(), 1);

        if (tipText != null && !tipText.isEmpty()) {
            Font font = getFont();
            if (font != null) {
                g.setFont(font);
            }
            FontMetrics metrics = g.getFontMetrics(font);
            int ascent = metrics != null ? metrics.getAscent() : 10;
            g.drawString(tipText, 4, 2 + ascent);
        }
    }

    @Override
    public Insets getInsets() {
        return new Insets(2, 4, 2, 4);
    }
}
