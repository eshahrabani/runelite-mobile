package javax.swing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.plaf.ProgressBarUI;

/**
 * Swing {@code JProgressBar}: a value/filled bar (the splash screen animates one).
 *
 * <p>Hand-written because the splash screen constructs one and installs its own
 * {@code BasicProgressBarUI} subclass; the component therefore has to keep real
 * minimum/maximum/value, a string, an orientation and the indeterminate flag, and the UI's
 * {@code getSelectionBackground()/getSelectionForeground()} hooks have to be consulted while
 * painting the filled region.
 */
public class JProgressBar extends JComponent implements SwingConstants, javax.accessibility.Accessible {

    private int minimum;
    private int maximum = 100;
    private int value;
    private int orientation = HORIZONTAL;

    private boolean stringPainted;
    private String string;
    private boolean indeterminate;
    private boolean borderPainted = true;

    private Color selectionBackground;
    private Color selectionForeground;

    public JProgressBar() {
        this(HORIZONTAL);
    }

    public JProgressBar(int orient) {
        super();
        this.orientation = orient == VERTICAL ? VERTICAL : HORIZONTAL;
        setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
        setOpaque(true);
        Color background = UIManager.getColor("ProgressBar.background");
        if (background != null) {
            setBackground(background);
        }
        Color foreground = UIManager.getColor("ProgressBar.foreground");
        if (foreground != null) {
            setForeground(foreground);
        }
    }

    public JProgressBar(int min, int max) {
        this(HORIZONTAL);
        setMinimum(min);
        setMaximum(max);
    }

    public void setUI(ProgressBarUI ui) {
        super.setUI(ui);
    }

    @Override
    public ProgressBarUI getUI() {
        return (ProgressBarUI) super.getUI();
    }

    @Override
    public String getUIClassID() {
        return "ProgressBarUI";
    }

    // ---------------------------------------------------------- range

    public int getMinimum() {
        return minimum;
    }

    public void setMinimum(int minimum) {
        int old = this.minimum;
        this.minimum = minimum;
        if (maximum < minimum) {
            maximum = minimum;
        }
        if (value < minimum) {
            value = minimum;
        }
        firePropertyChange("minimum", old, minimum);
        firePropertyChange("value", old, value);
        repaint();
    }

    public int getMaximum() {
        return maximum;
    }

    public void setMaximum(int maximum) {
        int old = this.maximum;
        this.maximum = maximum;
        if (minimum > maximum) {
            minimum = maximum;
        }
        if (value > maximum) {
            value = maximum;
        }
        firePropertyChange("maximum", old, maximum);
        repaint();
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        int old = this.value;
        this.value = Math.max(minimum, Math.min(maximum, value));
        firePropertyChange("value", old, this.value);
        repaint();
    }

    /** The completed fraction, 0..1 (the UI uses it to size the filled bar). */
    public double getPercentComplete() {
        long span = (long) maximum - minimum;
        if (span <= 0) {
            return 0.0;
        }
        return (double) (value - minimum) / span;
    }

    // ------------------------------------------------------ string

    public String getString() {
        return string != null ? string : (getPercentComplete() * 100.0) + "%";
    }

    public void setString(String string) {
        String old = this.string;
        this.string = string;
        firePropertyChange("string", old, string);
        repaint();
    }

    public boolean isStringPainted() {
        return stringPainted;
    }

    public void setStringPainted(boolean stringPainted) {
        boolean old = this.stringPainted;
        this.stringPainted = stringPainted;
        firePropertyChange("stringPainted", old, stringPainted);
        repaint();
    }

    // ---------------------------------------------------- orientation

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        int old = this.orientation;
        this.orientation = orientation == VERTICAL ? VERTICAL : HORIZONTAL;
        firePropertyChange("orientation", old, this.orientation);
        revalidate();
        repaint();
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public void setIndeterminate(boolean indeterminate) {
        boolean old = this.indeterminate;
        this.indeterminate = indeterminate;
        firePropertyChange("indeterminate", old, indeterminate);
        repaint();
    }

    // ------------------------------------------------------ border

    public boolean isBorderPainted() {
        return borderPainted;
    }

    public void setBorderPainted(boolean borderPainted) {
        boolean old = this.borderPainted;
        this.borderPainted = borderPainted;
        firePropertyChange("borderPainted", old, borderPainted);
        repaint();
    }

    // ------------------------------------------------- selection colours

    public Color getSelectionBackground() {
        return selectionBackground;
    }

    public void setSelectionBackground(Color selectionBackground) {
        this.selectionBackground = selectionBackground;
        repaint();
    }

    public Color getSelectionForeground() {
        return selectionForeground;
    }

    public void setSelectionForeground(Color selectionForeground) {
        this.selectionForeground = selectionForeground;
        repaint();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        repaint();
    }

    /** Local helper: the rectangle the UI paints the bar content into. */
    public Rectangle getContentRect() {
        java.awt.Insets insets = getInsets();
        return new Rectangle(insets.left, insets.top,
                Math.max(0, getWidth() - insets.left - insets.right),
                Math.max(0, getHeight() - insets.top - insets.bottom));
    }
}
