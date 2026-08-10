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
