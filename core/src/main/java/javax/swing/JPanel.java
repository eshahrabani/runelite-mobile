package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.LayoutManager;

/**
 * Swing {@code JPanel}: an opaque container with a layout manager and the panel
 * background colour. Every RuneLite panel is built from this class.
 */
public class JPanel extends JComponent implements javax.accessibility.Accessible {

    public JPanel() {
        this(new FlowLayout());
        setOpaque(true);
    }

    public JPanel(LayoutManager layout) {
        super();
        setLayout(layout);
        setOpaque(true);
        Color background = UIManager.getColor("Panel.background");
        if (background != null) {
            setBackground(background);
        }
    }

    public JPanel(boolean isDoubleBuffered) {
        this();
    }

    public JPanel(LayoutManager layout, boolean isDoubleBuffered) {
        this(layout);
    }
}
