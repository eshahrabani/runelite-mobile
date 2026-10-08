package java.awt;

import java.util.ArrayList;
import java.util.List;

/**
 * AWT Container compatibility stub for Android runtime.
 */
public class Container extends Component {
    protected final List<Component> components = new ArrayList<>();

    public Container() {}

    public Component add(Component comp) {
        components.add(comp);
        return comp;
    }

    public void add(Component comp, Object constraints) {
        // Nothing is laid out on this port, so the constraint form is a no-op.
    }

    public Component getComponent(int index) {
        return components.get(index);
    }

    public Insets getInsets() {
        // No native window decorations exist on this port.
        return new Insets(0, 0, 0, 0);
    }

    public void invalidate() {
        // No layout pass runs on this port.
    }

    public void revalidate() {
        // No layout pass runs on this port.
    }

    public void remove(Component comp) {
        components.remove(comp);
    }

    public void removeAll() {
        components.clear();
    }

    public Component[] getComponents() {
        return components.toArray(new Component[0]);
    }

    public int getComponentCount() {
        return components.size();
    }

    public void setFocusCycleRoot(boolean focusCycleRoot) {}

    public void setLayout(LayoutManager mgr) {}
    public LayoutManager getLayout() { return null; }
}
