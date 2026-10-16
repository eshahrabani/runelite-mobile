package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing {@code JLayeredPane}: children in layers, painted bottom layer first.
 *
 * <p>{@code add(comp, Integer.valueOf(layer))} places a component in a layer; painting
 * walks the layers in ascending order and, inside one layer, the Z-order of the children.
 * A custom layout manager can still be installed — the drag-and-drop reorder panel does
 * exactly that — in which case {@code doLayout()} delegates to it.
 */
public class JLayeredPane extends JComponent implements javax.accessibility.Accessible {
    public static final Integer DEFAULT_LAYER = 0;
    public static final Integer PALETTE_LAYER = 100;
    public static final Integer MODAL_LAYER = 200;
    public static final Integer POPUP_LAYER = 300;
    public static final Integer DRAG_LAYER = 400;
    public static final Integer FRAME_CONTENT_LAYER = -30000;

    private final Map<Component, Integer> layerOf = new HashMap<>();

    public JLayeredPane() {
        super();
        setLayout(null);
    }

    @Override
    public void add(Component comp, Object constraints) {
        add(comp, constraints, -1);
    }

    @Override
    public void add(Component comp, Object constraints, int index) {
        int layer = constraints instanceof Integer ? ((Integer) constraints) : DEFAULT_LAYER;
        if (comp instanceof JComponent) {
            ((JComponent) comp).putClientProperty("layeredContainerLayer", layer);
        }
        layerOf.put(comp, layer);
        super.addImpl(comp, null, index);
    }

    /** Places a component in the given layer (the JDK's {@code add(comp, Integer)} form). */
    public void addToLayer(Component comp, int layer) {
        add(comp, Integer.valueOf(layer), -1);
    }

    public void setLayer(Component c, int layer) {
        setLayer(c, layer, -1);
    }

    public void setLayer(Component c, int layer, int position) {
        Integer old = layerOf.get(c);
        if (old != null && old == layer) {
            return;
        }
        layerOf.put(c, layer);
        if (position >= 0) {
            setComponentZOrder(c, position);
        }
        repaint();
    }

    public int getLayer(Component c) {
        Integer layer = layerOf.get(c);
        if (layer != null) {
            return layer;
        }
        if (c instanceof JComponent) {
            Object prop = ((JComponent) c).getClientProperty("layeredContainerLayer");
            if (prop instanceof Integer) {
                return (Integer) prop;
            }
        }
        return DEFAULT_LAYER;
    }

    public int getLayer(JComponent c) {
        return getLayer((Component) c);
    }

    /** Index of {@code c} inside its own layer (the JDK's {@code getIndexOf}). */
    public int getIndexOf(Component c) {
        int layer = getLayer(c);
        int index = 0;
        for (Component other : getComponentsInLayer(layer)) {
            if (other == c) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /** Index of {@code c} inside its layer; the JDK's older name for {@link #getIndexOf}. */
    public int getPosition(Component c) {
        return getIndexOf(c);
    }

    /** Moves {@code c} to {@code position} inside its layer. */
    public void setPosition(Component c, int position) {
        int layer = getLayer(c);
        Component[] inLayer = getComponentsInLayer(layer);
        if (position < 0 || position >= inLayer.length || inLayer[position] == c) {
            return;
        }
        setComponentZOrder(c, position);
    }

    public void moveToFront(Component c) {
        setComponentZOrder(c, 0);
    }

    public void moveToBack(Component c) {
        setComponentZOrder(c, getComponentCount() - 1);
    }

    public void moveToLayer(Component c, int toLayer) {
        setLayer(c, toLayer);
    }

    public Component[] getComponentsInLayer(int layer) {
        List<Component> matches = new ArrayList<>();
        for (Component c : getComponents()) {
            if (getLayer(c) == layer) {
                matches.add(c);
            }
        }
        return matches.toArray(new Component[0]);
    }

    public int getComponentCountInLayer(int layer) {
        return getComponentsInLayer(layer).length;
    }

    public int highestLayer() {
        int highest = DEFAULT_LAYER;
        for (Component c : getComponents()) {
            highest = Math.max(highest, getLayer(c));
        }
        return highest;
    }

    public int lowestLayer() {
        int lowest = DEFAULT_LAYER;
        boolean first = true;
        for (Component c : getComponents()) {
            int layer = getLayer(c);
            lowest = first ? layer : Math.min(lowest, layer);
            first = false;
        }
        return lowest;
    }

    @Override
    public void doLayout() {
        LayoutManager lm = getLayout();
        if (lm != null) {
            lm.layoutContainer(this);
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        List<Component> ordered = new ArrayList<>();
        for (Component c : getComponents()) {
            ordered.add(c);
        }
        ordered.sort((a, b) -> Integer.compare(getLayer(a), getLayer(b)));
        for (Component c : ordered) {
            if (!c.isVisible() || c.getWidth() <= 0 || c.getHeight() <= 0) {
                continue;
            }
            Graphics gc = g.create();
            gc.translate(c.getX(), c.getY());
            gc.clipRect(0, 0, c.getWidth(), c.getHeight());
            c.paint(gc);
            gc.dispose();
        }
    }

    @Override
    public void remove(Component comp) {
        layerOf.remove(comp);
        super.remove(comp);
    }

    @Override
    public void removeAll() {
        layerOf.clear();
        super.removeAll();
    }

    @Override
    public void setLayout(LayoutManager mgr) {
        // The JDK's JLayeredPane uses null layout by default; a custom one is honoured.
        super.setLayout(mgr);
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }
}
