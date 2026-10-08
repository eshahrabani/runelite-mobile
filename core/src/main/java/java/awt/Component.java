package java.awt;

import java.awt.event.FocusListener;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AWT Component compatibility stub for Android runtime.
 */
public class Component implements Serializable {
    /**
     * One shared tree lock for the whole stub component tree, matching real AWT
     * semantics where every component of a tree synchronizes on the same object.
     */
    protected static final Object TREE_LOCK = new Object();

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected Color background;
    protected Color foreground;
    protected Font font;
    protected Cursor cursor;
    protected Container parent;

    protected final List<MouseListener> mouseListeners = new ArrayList<>();
    protected final List<MouseMotionListener> mouseMotionListeners = new ArrayList<>();
    protected final List<MouseWheelListener> mouseWheelListeners = new ArrayList<>();
    protected final List<KeyListener> keyListeners = new ArrayList<>();
    protected final List<FocusListener> focusListeners = new ArrayList<>();
    protected final List<java.awt.event.ComponentListener> componentListeners = new ArrayList<>();
    protected final List<HierarchyListener> hierarchyListeners = new ArrayList<>();

    public Component() {}

    public Container getParent() { return parent; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Dimension getSize() { return new Dimension(width, height); }
    public void setSize(int width, int height) { this.width = width; this.height = height; }
    public void setSize(Dimension d) { setSize(d.width, d.height); }
    
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public void setBounds(Rectangle r) { setBounds(r.x, r.y, r.width, r.height); }
    public Rectangle getBounds() { return new Rectangle(x, y, width, height); }

    public void setLocation(int x, int y) { this.x = x; this.y = y; }
    public void setLocation(Point p) { setLocation(p.x, p.y); }
    public Point getLocation() { return new Point(x, y); }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Color getBackground() { return background; }
    public void setBackground(Color c) { this.background = c; }
    public Color getForeground() { return foreground; }
    public void setForeground(Color c) { this.foreground = c; }
    public Font getFont() { return font; }
    public void setFont(Font f) { this.font = f; }

    public void setCursor(Cursor cursor) { this.cursor = cursor; }
    public Cursor getCursor() { return cursor; }

    public void validate() {}

    public Object getTreeLock() { return TREE_LOCK; }

    public boolean isDisplayable() {
        // No native peer or displayable window is ever realised on this port.
        return false;
    }

    public boolean isValid() {
        // No layout pass runs on this port (validate() is a no-op), so the component
        // never reaches AWT's "laid out" state.
        return false;
    }

    public void setIgnoreRepaint(boolean ignoreRepaint) {
        // No repaint machinery exists on this port; there is nothing for the flag to affect.
    }

    public Dimension getPreferredSize() {
        // No layout manager runs, but callers dereference the result, so return the
        // component's own stored size instead of null.
        return getSize();
    }

    public void removeNotify() {
        // No native peer to detach on this port.
    }

    public synchronized void addMouseListener(MouseListener l) { mouseListeners.add(l); }
    public synchronized void removeMouseListener(MouseListener l) { mouseListeners.remove(l); }
    public synchronized void addMouseMotionListener(MouseMotionListener l) { mouseMotionListeners.add(l); }
    public synchronized void removeMouseMotionListener(MouseMotionListener l) { mouseMotionListeners.remove(l); }
    public synchronized void addMouseWheelListener(MouseWheelListener l) { mouseWheelListeners.add(l); }
    public synchronized void removeMouseWheelListener(MouseWheelListener l) { mouseWheelListeners.remove(l); }
    public synchronized void addKeyListener(KeyListener l) { keyListeners.add(l); }
    public synchronized void removeKeyListener(KeyListener l) { keyListeners.remove(l); }
    public synchronized void addFocusListener(FocusListener l) { focusListeners.add(l); }
    public synchronized void removeFocusListener(FocusListener l) { focusListeners.remove(l); }
    public synchronized void addComponentListener(java.awt.event.ComponentListener l) { componentListeners.add(l); }
    public synchronized void removeComponentListener(java.awt.event.ComponentListener l) { componentListeners.remove(l); }
    public synchronized void addHierarchyListener(HierarchyListener l) { hierarchyListeners.add(l); }
    public synchronized void removeHierarchyListener(HierarchyListener l) { hierarchyListeners.remove(l); }

    public synchronized MouseListener[] getMouseListeners() {
        return mouseListeners.toArray(new MouseListener[0]);
    }

    public synchronized MouseMotionListener[] getMouseMotionListeners() {
        return mouseMotionListeners.toArray(new MouseMotionListener[0]);
    }

    public synchronized MouseWheelListener[] getMouseWheelListeners() {
        return mouseWheelListeners.toArray(new MouseWheelListener[0]);
    }

    public synchronized KeyListener[] getKeyListeners() {
        return keyListeners.toArray(new KeyListener[0]);
    }

    public Graphics getGraphics() {
        if (org.runelite.mobile.bridge.AWTBridge.activePixels != null) {
            return new Graphics2D(
                org.runelite.mobile.bridge.AWTBridge.activePixels,
                org.runelite.mobile.bridge.AWTBridge.activeWidth,
                org.runelite.mobile.bridge.AWTBridge.activeHeight
            );
        }
        return new Graphics2D();
    }
    public Image createImage(int width, int height) {
        int[] pixels = new int[width * height];
        return new Image(pixels, width, height);
    }
    public void repaint() {}
    public void paint(Graphics g) {}
    public void update(Graphics g) {
        paint(g);
    }
    public void requestFocus() {}
    public boolean requestFocusInWindow() { return true; }
    public void setFocusTraversalKeysEnabled(boolean focusTraversalKeysEnabled) {}
    public Toolkit getToolkit() { return Toolkit.getDefaultToolkit(); }
    public FontMetrics getFontMetrics(Font f) { return null; }
}
