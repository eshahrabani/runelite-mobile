package java.awt;

import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The port's {@code java.awt.Component}.
 *
 * <p>Hand-written, because the RuneLite Swing shell runs on real layout and paint
 * machinery here: a component owns a position and size, joins a {@link Container} tree,
 * is laid out by a {@link LayoutManager} and painted by walking that tree
 * ({@code paint} → {@code paintChildren} in {@link Container}, or
 * {@code JComponent.paint} → {@code paintComponent}/{@code paintBorder}/{@code paintChildren}).
 *
 * <p>Two invariants keep the game client working, and both are easy to break:
 * <ol>
 * <li>{@link #getGraphics()} keeps the port's original body: it hands out a
 *     {@code Graphics2D} over {@code AWTBridge.activePixels}, which is how the game's
 *     own pixel code writes into the displayed frame.</li>
 * <li>{@link #repaint()} does nothing unless the component sits under a registered paint
 *     root (the sidebar or a virtual window). The game's components have none, so their
 *     repaint calls must stay no-ops; only the Swing stage repaints.</li>
 * </ol>
 *
 * <p>Mouse/key events bubble up the tree the way lightweight AWT components do, but only
 * for {@code JComponent} targets: the game's heavyweight components stay the single
 * recipient of their events (bubbling them would deliver one tap to two listeners).
 * {@code MOUSE_ENTERED}/{@code MOUSE_EXITED} never bubble: they are chain events that
 * {@code UiBridge.updateHover} already delivers to every component that enters or leaves.
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
    protected String name;
    /** True once the component has been laid out at its current size (AWT's isValid). */
    protected boolean valid;
    /** Whether the component paints its own background (Swing's opaque flag). */
    protected boolean opaque;
    /** Whether the component accepts keyboard focus. */
    protected boolean focusable = true;

    private boolean ignoreRepaint;
    private boolean hasFocus;
    private long eventMask;

    protected final List<MouseListener> mouseListeners = new ArrayList<>();
    protected final List<MouseMotionListener> mouseMotionListeners = new ArrayList<>();
    protected final List<MouseWheelListener> mouseWheelListeners = new ArrayList<>();
    protected final List<KeyListener> keyListeners = new ArrayList<>();
    protected final List<FocusListener> focusListeners = new ArrayList<>();
    protected final List<ComponentListener> componentListeners = new ArrayList<>();
    protected final List<HierarchyListener> hierarchyListeners = new ArrayList<>();

    public Component() {
    }

    // ------------------------------------------------------------- geometry

    public Container getParent() {
        return parent;
    }

    /** Package-private: the container tree maintains the back link. */
    void setParent(Container parent) {
        this.parent = parent;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Dimension getSize() {
        return new Dimension(width, height);
    }

    public Dimension size() {
        return getSize();
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public Rectangle bounds() {
        return getBounds();
    }

    public Point getLocation() {
        return new Point(x, y);
    }

    public Point location() {
        return getLocation();
    }

    public void setSize(int width, int height) {
        resize(width, height);
    }

    public void setSize(Dimension d) {
        if (d != null) {
            resize(d.width, d.height);
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        reshape(x, y, width, height);
    }

    public void setBounds(Rectangle r) {
        if (r != null) {
            setBounds(r.x, r.y, r.width, r.height);
        }
    }

    public void setLocation(int x, int y) {
        int oldX = this.x;
        int oldY = this.y;
        if (oldX == x && oldY == y) {
            return;
        }
        this.x = x;
        this.y = y;
        onMoved(oldX, oldY);
    }

    public void setLocation(Point p) {
        if (p != null) {
            setLocation(p.x, p.y);
        }
    }

    /** JDK-era alias still called by the game client and old plugins. */
    public void resize(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        onResized();
    }

    /** JDK-era alias still called by the game client and old plugins. */
    public void resize(Dimension d) {
        if (d != null) {
            resize(d.width, d.height);
        }
    }

    /** JDK-era alias still called by the game client and old plugins. */
    public void move(int x, int y) {
        setLocation(x, y);
    }

    /** JDK-era alias still called by the game client and old plugins. */
    public void reshape(int x, int y, int width, int height) {
        boolean resized = this.width != width || this.height != height;
        boolean moved = this.x != x || this.y != y;
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        if (resized) {
            onResized();
        } else if (moved) {
            onMoved(x, y);
        }
    }

    private void onResized() {
        invalidate();
        fireComponentEvent(ComponentEvent.COMPONENT_RESIZED);
        repaint();
    }

    private void onMoved(int oldX, int oldY) {
        // A move changes no layout -- the JDK's Component.setLocation does not invalidate
        // either. Invalidating here made every JViewport.setViewPosition (i.e. every wheel
        // notch of a scroll, and every tap's hit-test follow-up) mark the whole sidebar
        // invalid, so the next paint re-laid-out the entire tree (Container.validateTree
        // calls doLayout on every container unconditionally): scrolling a panel crawled.
        fireComponentEvent(ComponentEvent.COMPONENT_MOVED);
        if (parent != null) {
            parent.repaint();
        }
    }

    /** Absolute position of the component's origin in stage coordinates. */
    public Point getLocationOnScreen() {
        int sx = x;
        int sy = y;
        for (Container p = parent; p != null; p = p.parent) {
            sx += p.x;
            sy += p.y;
        }
        return new Point(sx, sy);
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean contains(Point p) {
        return p != null && contains(p.x, p.y);
    }

    /** JDK alias: same as {@link #contains(int, int)}. */
    public boolean inside(int x, int y) {
        return contains(x, y);
    }

    /** The deepest component of this subtree containing the point (self when none). */
    public Component locate(int x, int y) {
        return contains(x, y) ? this : null;
    }

    public Component getComponentAt(int x, int y) {
        return locate(x, y);
    }

    public Component getComponentAt(Point p) {
        return p == null ? null : locate(p.x, p.y);
    }

    // -------------------------------------------------------------- state

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        if (visible) {
            fireComponentEvent(ComponentEvent.COMPONENT_SHOWN);
            invalidate();
            revalidate();
        } else {
            if (parent != null) {
                parent.repaint();
            }
            fireComponentEvent(ComponentEvent.COMPONENT_HIDDEN);
        }
    }

    /** True when the component and every ancestor is visible. */
    public boolean isShowing() {
        if (!visible) {
            return false;
        }
        for (Container p = parent; p != null; p = p.parent) {
            if (!p.visible) {
                return false;
            }
        }
        return true;
    }

    public boolean isDisplayable() {
        if (!visible) {
            return false;
        }
        Component top = this;
        while (top.parent != null) {
            top = top.parent;
        }
        return top instanceof Window
                || org.runelite.mobile.bridge.UiBridge.isSidebarRoot(top);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        repaint();
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIgnoreRepaint(boolean ignoreRepaint) {
        this.ignoreRepaint = ignoreRepaint;
    }

    public boolean getIgnoreRepaint() {
        return ignoreRepaint;
    }

    public Object getTreeLock() {
        return TREE_LOCK;
    }

    // ------------------------------------------------------- colors/fonts

    public Color getBackground() {
        if (background != null) {
            return background;
        }
        return parent != null ? parent.getBackground() : null;
    }

    public void setBackground(Color c) {
        Color old = background;
        this.background = c;
        if (old == null ? c != null : !old.equals(c)) {
            repaint();
        }
    }

    public Color getForeground() {
        if (foreground != null) {
            return foreground;
        }
        return parent != null ? parent.getForeground() : null;
    }

    public void setForeground(Color c) {
        Color old = foreground;
        this.foreground = c;
        if (old == null ? c != null : !old.equals(c)) {
            repaint();
        }
    }

    public Font getFont() {
        if (font != null) {
            return font;
        }
        if (parent != null) {
            return parent.getFont();
        }
        return javax.swing.UIManager.getFont("Component.font");
    }

    public void setFont(Font f) {
        Font old = font;
        this.font = f;
        if (old == null ? f != null : !old.equals(f)) {
            invalidate();
            revalidate();
            repaint();
        }
    }

    public FontMetrics getFontMetrics(Font font) {
        return new FontMetrics(font != null ? font : getFont());
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }

    public Cursor getCursor() {
        if (cursor != null) {
            return cursor;
        }
        return parent != null ? parent.getCursor() : null;
    }

    public boolean isOpaque() {
        return opaque;
    }

    public void setOpaque(boolean opaque) {
        this.opaque = opaque;
    }

    // -------------------------------------------------------------- paint

    /**
     * Paints the component itself: the background when the component is opaque. Subclasses
     * (and {@code JComponent.paintComponent}) draw their content on top.
     */
    /** Device debugging of the paint path; off in shipped builds. */
    public static final boolean DEBUG_PAINT_PROBE = false;

    /** Debug: every component paint call, so a multiply-painted tree is visible. */
    public static final java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> PAINT_CALLS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Debug: components whose opaque background is larger than any real stage. */
    public static final java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> OVERSIZED_FILLS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public void paint(Graphics g) {
        PAINT_CALLS.computeIfAbsent(getClass().getSimpleName(),
            key -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        paintSelf(g);
    }

    /**
     * Paints this component alone (its background when it is opaque), never its children.
     *
     * <p>Split out of {@link #paint} because {@code JComponent.paintComponent} must fill its
     * own background without painting children: calling {@code super.paint(g)} from there
     * resolved to {@link Container#paint}, which paints the children <em>as well</em> — and
     * because every level did that, a 600-component panel tree painted 195,283 times per
     * frame instead of ~600 (the sidebar's paint cost 5 s and starved the runtime).
     */
    protected void paintSelf(Graphics g) {
        if (isOpaque()) {
            Color bg = getBackground();
            if (bg != null) {
                g.setColor(bg);
                g.fillRect(0, 0, width, height);
            }
        }
    }

    public void update(Graphics g) {
        paint(g);
    }

    /** Paints this component and (for containers) its children when it is showing. */
    public void paintAll(Graphics g) {
        if (!visible) {
            return;
        }
        paint(g);
    }

    /**
     * Requests a repaint of the whole component. With no registered paint root above it
     * (every component of the game client) this is a no-op by design — see the class doc.
     */
    public void repaint() {
        repaint(0, 0, 0, width, height);
    }

    public void repaint(long tm) {
        repaint(tm, 0, 0, width, height);
    }

    public void repaint(int x, int y, int width, int height) {
        repaint(0, x, y, width, height);
    }

    public void repaint(long tm, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0 || ignoreRepaint) {
            return;
        }
        javax.swing.RepaintManager.currentManager(null).markDirty(this, new Rectangle(x, y, width, height));
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

    public Toolkit getToolkit() {
        return Toolkit.getDefaultToolkit();
    }

    public GraphicsConfiguration getGraphicsConfiguration() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
    }

    // ----------------------------------------------------------- layout

    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public Dimension getMinimumSize() {
        return new Dimension(width, height);
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /** Marks this component (and its ancestors) as needing a new layout pass. */
    public void invalidate() {
        if (valid) {
            valid = false;
            if (parent != null) {
                parent.invalidate();
            }
        }
    }

    /** Lays out the component when it is invalid (containers override). */
    public void validate() {
        if (!valid) {
            valid = true;
        }
    }

    public void revalidate() {
        invalidate();
        javax.swing.RepaintManager.currentManager(null).deferValidate(this);
    }

    public boolean isValid() {
        return valid;
    }

    public void doLayout() {
    }

    public void addNotify() {
    }

    public void removeNotify() {
    }

    // ---------------------------------------------------------- dispatch

    /**
     * Dispatches an event to this component, bubbling it to the parent when the target is
     * a lightweight Swing component and nothing consumed it (AWT's lightweight semantics).
     *
     * <p>{@code MOUSE_ENTERED}/{@code MOUSE_EXITED} never bubble: AWT delivers hover to the
     * whole component chain itself (the port's {@code UiBridge.updateHover} walks it), so a
     * bubble would call an ancestor's {@code mouseEntered} once per descendant under the
     * pointer — RuneLite's world switcher rows brightened once per pointer entry that way
     * ({@code lastBackground} is overwritten by each extra enter, so the row never came back
     * to its real background).
     */
    public void dispatchEvent(AWTEvent e) {
        if (e == null) {
            return;
        }
        processEvent(e);
        if (e.isConsumed() || parent == null) {
            return;
        }
        int id = e.getID();
        boolean bubblable = (e instanceof MouseEvent || e instanceof KeyEvent)
                && this instanceof javax.swing.JComponent
                && id != MouseEvent.MOUSE_ENTERED && id != MouseEvent.MOUSE_EXITED;
        if (bubblable) {
            parent.dispatchEvent(e);
        }
    }

    protected void processEvent(AWTEvent e) {
        if (e instanceof FocusEvent) {
            processFocusEvent((FocusEvent) e);
        } else if (e instanceof MouseWheelEvent) {
            processMouseWheelEvent((MouseWheelEvent) e);
        } else if (e instanceof MouseEvent) {
            switch (e.getID()) {
                case MouseEvent.MOUSE_DRAGGED:
                case MouseEvent.MOUSE_MOVED:
                    processMouseMotionEvent((MouseEvent) e);
                    break;
                default:
                    processMouseEvent((MouseEvent) e);
                    break;
            }
        } else if (e instanceof KeyEvent) {
            processKeyEvent((KeyEvent) e);
        } else if (e instanceof ComponentEvent) {
            processComponentEvent((ComponentEvent) e);
        } else if (e instanceof HierarchyEvent) {
            processHierarchyEvent((HierarchyEvent) e);
        }
    }

    protected void processMouseEvent(MouseEvent e) {
        if (mouseListeners.isEmpty() && (eventMask & AWTEvent.MOUSE_EVENT_MASK) == 0) {
            return;
        }
        for (MouseListener l : snapshot(mouseListeners)) {
            switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED:
                    l.mousePressed(e);
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    l.mouseReleased(e);
                    break;
                case MouseEvent.MOUSE_CLICKED:
                    l.mouseClicked(e);
                    break;
                case MouseEvent.MOUSE_ENTERED:
                    l.mouseEntered(e);
                    break;
                case MouseEvent.MOUSE_EXITED:
                    l.mouseExited(e);
                    break;
                default:
                    break;
            }
        }
    }

    protected void processMouseMotionEvent(MouseEvent e) {
        if (mouseMotionListeners.isEmpty() && (eventMask & AWTEvent.MOUSE_MOTION_EVENT_MASK) == 0) {
            return;
        }
        for (MouseMotionListener l : snapshot(mouseMotionListeners)) {
            if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
                l.mouseDragged(e);
            } else {
                l.mouseMoved(e);
            }
        }
    }

    protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (mouseWheelListeners.isEmpty() && (eventMask & AWTEvent.MOUSE_WHEEL_EVENT_MASK) == 0) {
            return;
        }
        for (MouseWheelListener l : snapshot(mouseWheelListeners)) {
            l.mouseWheelMoved(e);
        }
    }

    protected void processKeyEvent(KeyEvent e) {
        if (keyListeners.isEmpty() && (eventMask & AWTEvent.KEY_EVENT_MASK) == 0) {
            return;
        }
        for (KeyListener l : snapshot(keyListeners)) {
            switch (e.getID()) {
                case KeyEvent.KEY_PRESSED:
                    l.keyPressed(e);
                    break;
                case KeyEvent.KEY_RELEASED:
                    l.keyReleased(e);
                    break;
                case KeyEvent.KEY_TYPED:
                    l.keyTyped(e);
                    break;
                default:
                    break;
            }
        }
    }

    protected void processFocusEvent(FocusEvent e) {
        if (e.getID() == FocusEvent.FOCUS_GAINED) {
            hasFocus = true;
        } else if (e.getID() == FocusEvent.FOCUS_LOST) {
            hasFocus = false;
        }
        for (FocusListener l : snapshot(focusListeners)) {
            if (e.getID() == FocusEvent.FOCUS_GAINED) {
                l.focusGained(e);
            } else {
                l.focusLost(e);
            }
        }
    }

    protected void processComponentEvent(ComponentEvent e) {
        for (ComponentListener l : snapshot(componentListeners)) {
            switch (e.getID()) {
                case ComponentEvent.COMPONENT_RESIZED:
                    l.componentResized(e);
                    break;
                case ComponentEvent.COMPONENT_MOVED:
                    l.componentMoved(e);
                    break;
                case ComponentEvent.COMPONENT_SHOWN:
                    l.componentShown(e);
                    break;
                case ComponentEvent.COMPONENT_HIDDEN:
                    l.componentHidden(e);
                    break;
                default:
                    break;
            }
        }
    }

    protected void processHierarchyEvent(HierarchyEvent e) {
        for (HierarchyListener l : snapshot(hierarchyListeners)) {
            l.hierarchyChanged(e);
        }
    }

    /** Enables the given event mask so a subclass's {@code processXxxEvent} runs. */
    public void enableEvents(long events) {
        eventMask |= events;
    }

    public void disableEvents(long events) {
        eventMask &= ~events;
    }

    private static <T> List<T> snapshot(List<T> list) {
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    void fireComponentEvent(int id) {
        if (componentListeners.isEmpty()) {
            return;
        }
        processComponentEvent(new ComponentEvent(this, id));
    }

    // -------------------------------------------------------------- focus

    public boolean hasFocus() {
        return hasFocus;
    }

    public void requestFocus() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().requestFocus(this);
    }

    public boolean requestFocusInWindow() {
        requestFocus();
        return hasFocus;
    }

    public void transferFocus() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().nextFocus(this);
    }

    public boolean isRequestFocusEnabled() {
        return true;
    }

    public void setRequestFocusEnabled(boolean requestFocusEnabled) {
        // Focus is not a user-visible concept on a touch port; stored for API completeness.
    }

    public void setFocusTraversalKeysEnabled(boolean focusTraversalKeysEnabled) {
    }

    // ---------------------------------------------------------- listeners

    public synchronized void addMouseListener(MouseListener l) {
        mouseListeners.add(l);
    }

    public synchronized void removeMouseListener(MouseListener l) {
        mouseListeners.remove(l);
    }

    public synchronized void addMouseMotionListener(MouseMotionListener l) {
        mouseMotionListeners.add(l);
    }

    public synchronized void removeMouseMotionListener(MouseMotionListener l) {
        mouseMotionListeners.remove(l);
    }

    public synchronized void addMouseWheelListener(MouseWheelListener l) {
        mouseWheelListeners.add(l);
    }

    public synchronized void removeMouseWheelListener(MouseWheelListener l) {
        mouseWheelListeners.remove(l);
    }

    public synchronized void addKeyListener(KeyListener l) {
        keyListeners.add(l);
    }

    public synchronized void removeKeyListener(KeyListener l) {
        keyListeners.remove(l);
    }

    public synchronized void addFocusListener(FocusListener l) {
        focusListeners.add(l);
    }

    public synchronized void removeFocusListener(FocusListener l) {
        focusListeners.remove(l);
    }

    public synchronized void addComponentListener(ComponentListener l) {
        componentListeners.add(l);
    }

    public synchronized void removeComponentListener(ComponentListener l) {
        componentListeners.remove(l);
    }

    public synchronized void addHierarchyListener(HierarchyListener l) {
        hierarchyListeners.add(l);
    }

    public synchronized void removeHierarchyListener(HierarchyListener l) {
        hierarchyListeners.remove(l);
    }

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

    public synchronized FocusListener[] getFocusListeners() {
        return focusListeners.toArray(new FocusListener[0]);
    }

    public synchronized ComponentListener[] getComponentListeners() {
        return componentListeners.toArray(new ComponentListener[0]);
    }

    /** All listeners of the given type (the JDK's {@code getListeners} contract). */
    public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        List<T> matches = new ArrayList<>();
        collectListeners(listenerType, matches);
        @SuppressWarnings("unchecked")
        T[] result = (T[]) java.lang.reflect.Array.newInstance(listenerType, matches.size());
        return matches.toArray(result);
    }

    private <T> void collectListeners(Class<T> type, List<T> out) {
        for (Object l : snapshot(mouseListeners)) {
            addIfInstance(type, l, out);
        }
        for (Object l : snapshot(mouseMotionListeners)) {
            addIfInstance(type, l, out);
        }
        for (Object l : snapshot(mouseWheelListeners)) {
            addIfInstance(type, l, out);
        }
        for (Object l : snapshot(keyListeners)) {
            addIfInstance(type, l, out);
        }
        for (Object l : snapshot(focusListeners)) {
            addIfInstance(type, l, out);
        }
        for (Object l : snapshot(componentListeners)) {
            addIfInstance(type, l, out);
        }
        for (Object l : snapshot(hierarchyListeners)) {
            addIfInstance(type, l, out);
        }
    }

    private static <T> void addIfInstance(Class<T> type, Object listener, List<T> out) {
        if (type.isInstance(listener)) {
            out.add(type.cast(listener));
        }
    }

    /** The pointer position in this component's coordinates, or null. */
    public Point getMousePosition() {
        return org.runelite.mobile.bridge.UiBridge.mousePositionIn(this);
    }

    public String toString() {
        return getClass().getName() + "[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height
                + (name != null ? ",name=" + name : "") + "]";
    }

    /** No-op hook so {@code InputEvent}s can be inspected without importing the class. */
    static int modifiersOf(InputEvent e) {
        return e.getModifiersEx();
    }
}
