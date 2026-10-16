package java.awt;

import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.ArrayList;
import java.util.List;

/**
 * The port's {@code java.awt.Container}: the child list, the layout pass and the
 * child painting of every Swing panel.
 *
 * <p>Layout contract (JDK): {@code add} records the component and passes any constraint
 * to the layout manager; {@code invalidate()} walks up the tree; {@code validate()}
 * lays out this container and its subtree ({@code doLayout()} → the layout manager's
 * {@code layoutContainer}), and {@code getPreferredSize()} asks the layout manager
 * first, then the superclass. A component is laid out at the size the layout manager
 * gave it before its first paint.
 *
 * <p>Painting contract (JDK): {@code paint} paints the container's own background
 * (when opaque) and then every visible child in Z-order, each translated into its own
 * coordinate space and clipped to its own bounds.
 */
public class Container extends Component {
    /** Children in Z-order; index 0 is painted first (bottom-most). */
    protected final List<Component> components = new ArrayList<>();

    private LayoutManager layout;
    private final List<ContainerListener> containerListeners = new ArrayList<>();

    public Container() {
    }

    // ------------------------------------------------------------- layout

    public void setLayout(LayoutManager mgr) {
        this.layout = mgr;
        invalidate();
    }

    public LayoutManager getLayout() {
        return layout;
    }

    @Override
    public void doLayout() {
        LayoutManager lm = layout;
        if (lm != null) {
            lm.layoutContainer(this);
        }
    }

    @Override
    public void invalidate() {
        // A container without a layout manager never changes size from its children.
        if (layout != null && !valid) {
            return;
        }
        if (layout != null) {
            valid = false;
        }
        // Walk up unconditionally: the ancestor's layout may still be affected.
        if (parent != null) {
            parent.invalidate();
        }
    }

    @Override
    public void validate() {
        if (valid) {
            return;
        }
        validateTree();
        valid = true;
    }

    /** Lays out this container and every invalid descendant. */
    public void validateTree() {
        doLayout();
        for (Component c : snapshotComponents()) {
            if (c instanceof Container) {
                ((Container) c).validateTree();
            } else {
                c.validate();
            }
        }
        valid = true;
    }

    private List<Component> snapshotComponents() {
        synchronized (components) {
            return new ArrayList<>(components);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        LayoutManager lm = layout;
        if (lm instanceof LayoutManager2) {
            return ((LayoutManager2) lm).preferredLayoutSize(this);
        }
        if (lm != null) {
            return lm.preferredLayoutSize(this);
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        LayoutManager lm = layout;
        if (lm instanceof LayoutManager2) {
            return ((LayoutManager2) lm).minimumLayoutSize(this);
        }
        if (lm != null) {
            return lm.minimumLayoutSize(this);
        }
        return super.getMinimumSize();
    }

    @Override
    public Dimension getMaximumSize() {
        LayoutManager lm = layout;
        if (lm instanceof LayoutManager2) {
            return ((LayoutManager2) lm).maximumLayoutSize(this);
        }
        return super.getMaximumSize();
    }

    public Insets getInsets() {
        if (this instanceof javax.swing.JComponent) {
            javax.swing.border.Border border = ((javax.swing.JComponent) this).getBorder();
            if (border != null) {
                Insets insets = border.getBorderInsets(this);
                if (insets != null) {
                    return insets;
                }
            }
        }
        return new Insets(0, 0, 0, 0);
    }

    // ------------------------------------------------------------- children

    public Component add(Component comp) {
        addImpl(comp, null, -1);
        return comp;
    }

    public Component add(String name, Component comp) {
        addImpl(comp, name, -1);
        return comp;
    }

    public Component add(Component comp, int index) {
        addImpl(comp, null, index);
        return comp;
    }

    public void add(Component comp, Object constraints) {
        addImpl(comp, constraints, -1);
    }

    public void add(Component comp, Object constraints, int index) {
        addImpl(comp, constraints, index);
    }

    protected void addImpl(Component comp, Object constraints, int index) {
        if (comp == null) {
            return;
        }
        if (comp.parent != null) {
            comp.parent.remove(comp);
        }
        synchronized (components) {
            if (index < 0 || index > components.size()) {
                components.add(comp);
            } else {
                components.add(index, comp);
            }
        }
        comp.setParent(this);
        LayoutManager lm = layout;
        if (lm instanceof LayoutManager2) {
            ((LayoutManager2) lm).addLayoutComponent(comp, constraints);
        } else if (lm != null && constraints instanceof String) {
            lm.addLayoutComponent((String) constraints, comp);
        }
        comp.invalidate();
        invalidate();
        fireContainerEvent(ContainerEvent.COMPONENT_ADDED, comp);
        comp.addNotify();
        comp.revalidate();
    }

    public void remove(int index) {
        Component comp;
        synchronized (components) {
            comp = components.get(index);
            components.remove(index);
        }
        removeFromLayout(comp);
    }

    public void remove(Component comp) {
        if (comp == null) {
            return;
        }
        synchronized (components) {
            if (!components.remove(comp)) {
                return;
            }
        }
        removeFromLayout(comp);
    }

    public void removeAll() {
        List<Component> old;
        synchronized (components) {
            old = new ArrayList<>(components);
            components.clear();
        }
        for (Component comp : old) {
            removeFromLayout(comp);
        }
    }

    private void removeFromLayout(Component comp) {
        LayoutManager lm = layout;
        if (lm != null) {
            lm.removeLayoutComponent(comp);
        }
        comp.setParent(null);
        comp.removeNotify();
        invalidate();
        fireContainerEvent(ContainerEvent.COMPONENT_REMOVED, comp);
        repaint();
    }

    public int getComponentCount() {
        synchronized (components) {
            return components.size();
        }
    }

    public Component getComponent(int index) {
        synchronized (components) {
            return components.get(index);
        }
    }

    public Component[] getComponents() {
        synchronized (components) {
            return components.toArray(new Component[0]);
        }
    }

    public Component getComponentAt(int x, int y) {
        return locate(x, y);
    }

    public Component getComponentAt(Point p) {
        return p == null ? null : locate(p.x, p.y);
    }

    /** The deepest visible child containing the point (self when the point is in no child). */
    @Override
    public Component locate(int x, int y) {
        if (!contains(x, y)) {
            return null;
        }
        Component[] kids = getComponents();
        for (int i = kids.length - 1; i >= 0; i--) {
            Component child = kids[i];
            if (!child.isVisible()) {
                continue;
            }
            int cx = x - child.x;
            int cy = y - child.y;
            if (!child.contains(cx, cy)) {
                continue;
            }
            Component found = child.locate(cx, cy);
            if (found != null) {
                return found;
            }
        }
        return this;
    }

    /** JDK name for {@link #locate(int, int)} on a container. */
    public Component findComponentAt(int x, int y) {
        return locate(x, y);
    }

    public Component findComponentAt(Point p) {
        return p == null ? null : locate(p.x, p.y);
    }

    public int getComponentZOrder(Component comp) {
        synchronized (components) {
            return components.indexOf(comp);
        }
    }

    public void setComponentZOrder(Component comp, int index) {
        synchronized (components) {
            int old = components.indexOf(comp);
            if (old < 0 || old == index) {
                return;
            }
            components.remove(old);
            components.add(Math.max(0, Math.min(index, components.size())), comp);
        }
        invalidate();
        repaint();
    }

    public boolean isAncestorOf(Component c) {
        for (Component p = c == null ? null : c.getParent(); p != null; p = p.getParent()) {
            if (p == this) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- paint

    @Override
    public void paint(Graphics g) {
        paintSelf(g);
        paintComponents(g);
    }

    /** Paints every visible child in Z-order, clipped and translated to its own space. */
    public void paintComponents(Graphics g) {
        for (Component c : snapshotComponents()) {
            if (!c.isVisible() || c.width <= 0 || c.height <= 0) {
                continue;
            }
            Graphics gc = g.create();
            gc.translate(c.x, c.y);
            gc.clipRect(0, 0, c.width, c.height);
            c.paint(gc);
            gc.dispose();
        }
    }

    @Override
    public void paintAll(Graphics g) {
        if (!visible) {
            return;
        }
        paint(g);
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    public void addContainerListener(ContainerListener l) {
        synchronized (containerListeners) {
            containerListeners.add(l);
        }
    }

    public void removeContainerListener(ContainerListener l) {
        synchronized (containerListeners) {
            containerListeners.remove(l);
        }
    }

    public ContainerListener[] getContainerListeners() {
        synchronized (containerListeners) {
            return containerListeners.toArray(new ContainerListener[0]);
        }
    }

    protected void processContainerEvent(ContainerEvent e) {
        ContainerListener[] listeners;
        synchronized (containerListeners) {
            listeners = containerListeners.toArray(new ContainerListener[0]);
        }
        for (ContainerListener l : listeners) {
            if (e.getID() == ContainerEvent.COMPONENT_ADDED) {
                l.componentAdded(e);
            } else if (e.getID() == ContainerEvent.COMPONENT_REMOVED) {
                l.componentRemoved(e);
            }
        }
    }

    private void fireContainerEvent(int id, Component child) {
        if (containerListeners.isEmpty()) {
            return;
        }
        processContainerEvent(new ContainerEvent(this, id, child));
    }

    /** Forwarded to every child so the game client's screens keep their "component hidden" contract. */
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
    }

    public void setFocusCycleRoot(boolean focusCycleRoot) {
        // Focus traversal only matters for keyboard users; the port has none.
    }

    /** Debug helper mirroring the JDK's {@code Container.list}. */
    public void list(java.io.PrintStream out, int indent) {
        for (int i = 0; i < indent; i++) {
            out.print(" ");
        }
        out.println(this);
        for (Component c : snapshotComponents()) {
            if (c instanceof Container) {
                ((Container) c).list(out, indent + 2);
            } else {
                for (int i = 0; i < indent + 2; i++) {
                    out.print(" ");
                }
                out.println(c);
            }
        }
    }
}
