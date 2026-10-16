package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;

/**
 * Swing {@code JRootPane}: the container a window (or a popup) hangs its content on.
 *
 * <p>Holds the {@code glassPane} → {@code layeredPane} (with the optional menu bar and
 * the {@code contentPane}) hierarchy the JDK's windows expose, and lays it out the same
 * way: the menu bar at the top, the layered pane filling the rest, the content pane
 * filling the layered pane.
 */
public class JRootPane extends JComponent implements javax.accessibility.Accessible {
    public static final int NONE = 0;
    public static final int FRAME = 1;
    public static final int PLAIN_DIALOG = 2;
    public static final int INFORMATION_DIALOG = 3;
    public static final int ERROR_DIALOG = 4;
    public static final int COLOR_CHOOSER_DIALOG = 5;
    public static final int FILE_CHOOSER_DIALOG = 6;
    public static final int QUESTION_DIALOG = 7;
    public static final int WARNING_DIALOG = 8;

    private static final String DEFAULT_GLASS_PANE_PROPERTY = "JRootPane.defaultGlassPane";

    private JLayeredPane layeredPane;
    private Container contentPane;
    private Component glassPane;
    private JMenuBar menuBar;
    private int windowDecorationStyle = NONE;

    public JRootPane() {
        super();
        setLayout(null);
        setOpaque(false);
        layeredPane = new JLayeredPane();
        layeredPane.setBounds(0, 0, 0, 0);
        add(layeredPane);
        contentPane = new JPanel();
        contentPane.setBounds(0, 0, 0, 0);
        layeredPane.add(contentPane, JLayeredPane.FRAME_CONTENT_LAYER);
        glassPane = new JPanel();
        glassPane.setVisible(false);
        glassPane.setBounds(0, 0, 0, 0);
        setGlassPane(glassPane);
    }

    public JLayeredPane getLayeredPane() {
        return layeredPane;
    }

    public void setLayeredPane(JLayeredPane layeredPane) {
        if (layeredPane == null) {
            throw new IllegalArgumentException("layeredPane must not be null");
        }
        remove(this.layeredPane);
        this.layeredPane = layeredPane;
        super.add(layeredPane, 0);
        if (menuBar != null) {
            layeredPane.add(menuBar, JLayeredPane.DEFAULT_LAYER);
        }
        layeredPane.add(contentPane, JLayeredPane.FRAME_CONTENT_LAYER);
        revalidate();
        repaint();
    }

    public Component getGlassPane() {
        return glassPane;
    }

    public void setGlassPane(Component glassPane) {
        if (glassPane == null) {
            throw new IllegalArgumentException("glassPane must not be null");
        }
        Component old = this.glassPane;
        if (old != null) {
            old.setVisible(false);
        }
        this.glassPane = glassPane;
        if (!(glassPane instanceof JComponent)) {
            glassPane.setVisible(false);
        }
        super.add(glassPane, 0);
        revalidate();
        repaint();
    }

    public Container getContentPane() {
        return contentPane;
    }

    public void setContentPane(Container contentPane) {
        if (contentPane == null) {
            throw new IllegalArgumentException("contentPane must not be null");
        }
        JLayeredPane layered = getLayeredPane();
        layered.remove(this.contentPane);
        this.contentPane = contentPane;
        layered.add(contentPane, JLayeredPane.FRAME_CONTENT_LAYER);
        layered.setLayer(contentPane, JLayeredPane.FRAME_CONTENT_LAYER);
        revalidate();
        repaint();
    }

    public JMenuBar getJMenuBar() {
        return menuBar;
    }

    public void setJMenuBar(JMenuBar menu) {
        JLayeredPane layered = getLayeredPane();
        if (menuBar != null) {
            layered.remove(menuBar);
        }
        menuBar = menu;
        if (menu != null) {
            layered.add(menu, JLayeredPane.DEFAULT_LAYER);
            layered.setLayer(menu, JLayeredPane.DEFAULT_LAYER);
        }
        revalidate();
        repaint();
    }

    public int getWindowDecorationStyle() {
        return windowDecorationStyle;
    }

    public void setWindowDecorationStyle(int windowDecorationStyle) {
        this.windowDecorationStyle = windowDecorationStyle;
    }

    public void registerKeyboardAction(ActionListener action, KeyStroke stroke, int condition) {
        JComponent component = getContentPane() instanceof JComponent ? (JComponent) getContentPane() : null;
        if (component != null) {
            component.registerKeyboardAction(action, stroke, condition);
        }
    }

    public boolean isValidateRoot() {
        return true;
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int h = getHeight();
        int menuHeight = 0;
        JMenuBar bar = menuBar;
        if (bar != null && bar.isVisible()) {
            Dimension pref = bar.getPreferredSize();
            menuHeight = pref == null ? 0 : pref.height;
            bar.setBounds(0, 0, w, menuHeight);
        }
        JLayeredPane layered = getLayeredPane();
        if (layered != null) {
            layered.setBounds(0, menuHeight, w, Math.max(0, h - menuHeight));
            // The layered pane runs with a null layout, so the content pane has to be
            // sized here: it fills the layered pane, which is what a window's content
            // area means. (Without this a virtual window would paint an empty frame.)
            Container content = getContentPane();
            if (content != null) {
                content.setBounds(0, 0, layered.getWidth(), layered.getHeight());
            }
            layered.validate();
            if (content instanceof JComponent) {
                ((JComponent) content).validate();
            }
        }
        Component glass = getGlassPane();
        if (glass != null) {
            glass.setBounds(0, 0, w, h);
            if (glass instanceof Container) {
                ((Container) glass).validate();
            }
        }
    }

    /** Lays out the layered pane's content pane to fill its parent. */
    void layoutLayeredPaneContent() {
        Container content = getContentPane();
        JLayeredPane layered = getLayeredPane();
        if (content != null && layered != null) {
            content.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Container content = getContentPane();
        Dimension d = content == null ? null : content.getPreferredSize();
        if (d == null || d.width <= 0 || d.height <= 0) {
            d = new Dimension(200, 100);
        }
        JMenuBar bar = getJMenuBar();
        int menuHeight = bar == null ? 0 : (bar.getPreferredSize() == null ? 0 : bar.getPreferredSize().height);
        return new Dimension(d.width, d.height + menuHeight);
    }

    @Override
    public Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }

    /** The layout delegates to the layered pane; a custom layout is not expected here. */
    @Override
    public void setLayout(LayoutManager mgr) {
        super.setLayout(mgr);
    }
}
