package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.LabelUI;
import javax.swing.text.html.HTMLRenderer;

/**
 * Basic look-and-feel {@code JLabel} painter.
 *
 * <p>Hand-written because the labels are the text the user reads: it lays the icon and text
 * out with {@link SwingUtilities#layoutCompoundLabel}, draws them with the label's font and
 * (enabled/disabled) foreground, and paints the border — the port's paint pipeline routes a
 * UI-bearing component entirely through the UI, so the border is painted here rather than by
 * {@code JComponent.paintBorder}. HTML text is delegated to {@link HTMLRenderer}.
 *
 * <p>{@code paintEnabledText}/{@code paintDisabledText} are the override points RuneLite's
 * {@code JShadowedLabelUI} uses to draw a shadow.
 */
public class BasicLabelUI extends LabelUI implements PropertyChangeListener {

    public static ComponentUI createUI(JComponent c) {
        return new BasicLabelUI();
    }

    public BasicLabelUI() {
        super();
    }

    @Override
    public void installUI(JComponent c) {
        installDefaults((JLabel) c);
        installListeners((JLabel) c);
    }

    @Override
    public void uninstallUI(JComponent c) {
        uninstallListeners((JLabel) c);
        uninstallDefaults((JLabel) c);
    }

    protected void installDefaults(JLabel c) {
        // Font/foreground resolve through Component.getFont/getForeground, which fall back to
        // the UIManager defaults, so nothing is forced here (a caller-set value must survive).
    }

    protected void uninstallDefaults(JLabel c) {
    }

    protected void installListeners(JLabel c) {
        c.addPropertyChangeListener(this);
    }

    protected void uninstallListeners(JLabel c) {
        c.removePropertyChangeListener(this);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        JLabel label = (JLabel) c;
        String text = label.getText();
        Icon icon = label.getIcon();
        if (!label.isEnabled() && label.getDisabledIcon() != null) {
            icon = label.getDisabledIcon();
        }

        if (text != null && !text.isEmpty() && HTMLRenderer.isHtml(text) && g instanceof Graphics2D) {
            paintHtml((Graphics2D) g, label, text, icon);
        } else {
            paintPlain(g, label, text, icon);
        }
        paintBorder(label, g);
    }

    private void paintPlain(Graphics g, JLabel label, String text, Icon icon) {
        Font font = label.getFont();
        if (font != null) {
            g.setFont(font);
        }
        FontMetrics fm = g.getFontMetrics(font);
        Insets insets = label.getInsets();
        Rectangle viewR = new Rectangle(insets.left, insets.top,
                Math.max(0, label.getWidth() - insets.left - insets.right),
                Math.max(0, label.getHeight() - insets.top - insets.bottom));
        Rectangle iconR = new Rectangle();
        Rectangle textR = new Rectangle();
        String clipped = SwingUtilities.layoutCompoundLabel(label, fm, text, icon,
                label.getVerticalAlignment(), label.getHorizontalAlignment(),
                label.getVerticalTextPosition(), label.getHorizontalTextPosition(),
                viewR, iconR, textR, label.getIconTextGap());

        if (icon != null) {
            icon.paintIcon(label, g, iconR.x, iconR.y);
        }
        if (clipped != null && !clipped.isEmpty()) {
            int textX = textR.x;
            int textY = textR.y + fm.getAscent();
            if (label.isEnabled()) {
                paintEnabledText(label, g, clipped, textX, textY);
            } else {
                paintDisabledText(label, g, clipped, textX, textY);
            }
        }
    }

    private void paintHtml(Graphics2D g, JLabel label, String text, Icon icon) {
        Insets insets = label.getInsets();
        int originX = insets.left;
        int contentHeight = Math.max(0, label.getHeight() - insets.top - insets.bottom);
        if (icon != null) {
            int iconY = insets.top + Math.max(0, (contentHeight - icon.getIconHeight()) / 2);
            icon.paintIcon(label, g, originX, iconY);
            originX += icon.getIconWidth() + label.getIconTextGap();
        }
        int available = Math.max(0, label.getWidth() - insets.left - insets.right - (originX - insets.left));
        HTMLRenderer renderer = new HTMLRenderer(text, label.getFont());
        renderer.layout(available);
        Dimension pref = renderer.getPreferredSize();
        int textY = insets.top + Math.max(0, (contentHeight - pref.height) / 2);
        // HTMLRenderer draws runs without an explicit colour in whatever the Graphics already
        // carries; that is the background here, so set the label's text colour first (the
        // renderer saves/restores it).
        Color textColor = label.isEnabled() ? enabledTextColor(label) : disabledTextColor(label);
        g.setColor(textColor);
        renderer.paint(g, originX, textY);
    }

    protected void paintBorder(JLabel label, Graphics g) {
        javax.swing.border.Border border = label.getBorder();
        if (border != null) {
            border.paintBorder(label, g, 0, 0, label.getWidth(), label.getHeight());
        }
    }

    /** Draws enabled text; overridden by RuneLite's shadowed label UI. */
    protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
        g.setColor(enabledTextColor(l));
        g.drawString(s, textX, textY);
    }

    /** Draws disabled text (a blend of the foreground and background, as the JDK does). */
    protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY) {
        g.setColor(disabledTextColor(l));
        g.drawString(s, textX, textY);
    }

    /** The enabled text colour: the label foreground, falling back to {@code Label.foreground}. */
    private Color enabledTextColor(JLabel l) {
        Color foreground = l.getForeground();
        if (foreground == null) {
            foreground = javax.swing.UIManager.getColor("Label.foreground");
        }
        return foreground != null ? foreground : Color.LIGHT_GRAY;
    }

    /** The disabled text colour: the JDK's foreground/background blend. */
    private Color disabledTextColor(JLabel l) {
        Color foreground = l.getForeground();
        Color background = l.getBackground();
        if (foreground != null && background != null) {
            return new Color((foreground.getRed() + background.getRed()) / 2,
                    (foreground.getGreen() + background.getGreen()) / 2,
                    (foreground.getBlue() + background.getBlue()) / 2);
        }
        return Color.GRAY;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        JLabel label = (JLabel) c;
        String text = label.getText();
        Icon icon = label.getIcon();
        Insets insets = label.getInsets();
        Font font = label.getFont();

        if (text != null && !text.isEmpty() && HTMLRenderer.isHtml(text)) {
            HTMLRenderer renderer = new HTMLRenderer(text, font);
            renderer.layout(0);
            Dimension d = renderer.getPreferredSize();
            return new Dimension(d.width + insets.left + insets.right,
                    d.height + insets.top + insets.bottom);
        }

        FontMetrics fm = label.getFontMetrics(font);
        boolean hasText = text != null && !text.isEmpty();
        int textW = hasText ? fm.stringWidth(text) : 0;
        int textH = hasText ? fm.getHeight() : 0;
        int iconW = icon != null ? icon.getIconWidth() : 0;
        int iconH = icon != null ? icon.getIconHeight() : 0;
        int gap = (icon != null && hasText) ? label.getIconTextGap() : 0;

        int width = insets.left + insets.right + iconW + gap + textW;
        int height = insets.top + insets.bottom + Math.max(iconH, textH);
        return new Dimension(width, height);
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        Object source = e.getSource();
        if (source instanceof JLabel) {
            JLabel label = (JLabel) source;
            label.revalidate();
            label.repaint();
        }
    }
}
