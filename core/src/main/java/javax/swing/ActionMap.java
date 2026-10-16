package javax.swing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing {@code ActionMap}: maps the keys an {@link InputMap} binds to {@link Action}s,
 * with an optional parent map.
 */
public class ActionMap {
    private final Map<Object, Action> bindings = new HashMap<>();
    private ActionMap parent;

    public ActionMap() {
    }

    public void put(Object key, Action action) {
        if (key == null) {
            return;
        }
        if (action == null) {
            bindings.remove(key);
        } else {
            bindings.put(key, action);
        }
    }

    public Action get(Object key) {
        Action action = bindings.get(key);
        if (action == null && parent != null) {
            return parent.get(key);
        }
        return action;
    }

    public void remove(Object key) {
        bindings.remove(key);
    }

    public void clear() {
        bindings.clear();
    }

    public Object[] keys() {
        return bindings.keySet().toArray();
    }

    public Object[] allKeys() {
        List<Object> all = new ArrayList<>(bindings.keySet());
        if (parent != null) {
            for (Object k : parent.allKeys()) {
                if (!all.contains(k)) {
                    all.add(k);
                }
            }
        }
        return all.toArray();
    }

    public int size() {
        return bindings.size();
    }

    public ActionMap getParent() {
        return parent;
    }

    public void setParent(ActionMap map) {
        this.parent = map;
    }
}
