package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ProgressBarUI;

/**
 * Basic look-and-feel {@code JProgressBar} painter.
 *
 * <p>Hand-written because the splash screen installs a subclass of this that overrides
 * {@link #getSelectionBackground()}/{@link #getSelectionForeground()}, and the filled bar has
 * to be drawn inside the border insets from the component's value. The exact antialiasing is
 * not specified, so a plain rectangle fill is used.
 */
public class BasicProgressBarUI extends ProgressBarUI {

    protected JProgressBar progressBar;

    public static ComponentUI createUI(JComponent c) {
        return new BasicProgressBarUI();
    }

    public BasicProgressBarUI() {
        super();
    }

    @Override
    public void installUI(JComponent c) {
        progressBar = (JProgressBar) c;
    }

    @Override
    public void uninstallUI(JComponent c) {
        progressBar = null;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        JProgressBar bar = (JProgressBar) c;
        Insets insets = bar.getInsets();
        int width = bar.getWidth();
        int height = bar.getHeight();

        // The unfilled background is handled by ComponentUI.update when the bar is opaque.
        int amount = getAmountFull(insets, width, height);
        Color selectionBackground = getSelectionBackground();
        if (selectionBackground != null) {
            g.setColor(selectionBackground);
            if (bar.getOrientation() == JProgressBar.VERTICAL) {
                g.fillRect(insets.left, height - insets.bottom - amount,
                        Math.max(0, width - insets.left - insets.right), amount);
            } else {
                g.fillRect(insets.left, insets.top, amount,
                        Math.max(0, height - insets.top - insets.bottom));
            }
        }

        if (bar.isStringPainted() && !bar.isIndeterminate()) {
            paintString(g, bar, amount);
        }

        if (bar.isBorderPainted() && bar.getBorder() != null) {
            bar.getBorder().paintBorder(bar, g, 0, 0, width, height);
        }
    }

    private void paintString(Graphics g, JProgressBar bar, int amount) {
        String text = bar.getString();
        if (text == null || text.isEmpty()) {
            return;
        }
        FontMetrics fm = g.getFontMetrics(bar.getFont());
        Color color = getSelectionForeground();
        if (color == null) {
            color = bar.getForeground();
        }
        g.setColor(color != null ? color : Color.WHITE);
        int x = (bar.getWidth() - fm.stringWidth(text)) / 2;
        int y = (bar.getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
    }

    /** How many pixels of the long axis are filled for the current value. */
    protected int getAmountFull(Insets insets, int width, int height) {
        JProgressBar bar = progressBar;
        if (bar == null) {
            return 0;
        }
        if (bar.isIndeterminate()) {
            // No animation timer on this port: show a half-filled bar.
            if (bar.getOrientation() == JProgressBar.VERTICAL) {
                return Math.max(0, height - insets.top - insets.bottom) / 2;
            }
            return Math.max(0, width - insets.left - insets.right) / 2;
        }
        double percent = bar.getPercentComplete();
        if (bar.getOrientation() == JProgressBar.VERTICAL) {
            return (int) (percent * Math.max(0, height - insets.top - insets.bottom));
        }
        return (int) (percent * Math.max(0, width - insets.left - insets.right));
    }

    /** The colour of the filled region; overridden by the splash screen's UI. */
    protected Color getSelectionBackground() {
        Color color = javax.swing.UIManager.getColor("ProgressBar.selectionBackground");
        if (color == null && progressBar != null) {
            color = progressBar.getForeground();
        }
        return color != null ? color : Color.WHITE;
    }

    /** The colour of the painted string; overridden by the splash screen's UI. */
    protected Color getSelectionForeground() {
        Color color = javax.swing.UIManager.getColor("ProgressBar.selectionForeground");
        if (color == null && progressBar != null) {
            color = progressBar.getForeground();
        }
        return color != null ? color : Color.BLACK;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        JProgressBar bar = (JProgressBar) c;
        Insets insets = bar.getInsets();
        if (bar.getOrientation() == JProgressBar.VERTICAL) {
            return new Dimension(14 + insets.left + insets.right, 100 + insets.top + insets.bottom);
        }
        return new Dimension(100 + insets.left + insets.right, 14 + insets.top + insets.bottom);
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }
}
