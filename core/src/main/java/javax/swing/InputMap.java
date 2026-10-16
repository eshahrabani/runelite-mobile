package javax.swing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing {@code InputMap}: maps {@link KeyStroke}s to action-map keys, with an optional
 * parent map that is consulted when this map has no binding.
 */
public class InputMap {
    private final Map<KeyStroke, Object> bindings = new HashMap<>();
    private InputMap parent;

    public InputMap() {
    }

    public void put(KeyStroke keyStroke, Object actionMapKey) {
        if (keyStroke == null) {
            return;
        }
        if (actionMapKey == null) {
            bindings.remove(keyStroke);
        } else {
            bindings.put(keyStroke, actionMapKey);
        }
    }

    public Object get(KeyStroke keyStroke) {
        Object value = bindings.get(keyStroke);
        if (value == null && parent != null) {
            return parent.get(keyStroke);
        }
        return value;
    }

    public void remove(KeyStroke keyStroke) {
        bindings.remove(keyStroke);
    }

    public void clear() {
        bindings.clear();
    }

    public KeyStroke[] keys() {
        return bindings.keySet().toArray(new KeyStroke[0]);
    }

    public KeyStroke[] allKeys() {
        List<KeyStroke> all = new ArrayList<>(bindings.keySet());
        if (parent != null) {
            for (KeyStroke k : parent.allKeys()) {
                if (!all.contains(k)) {
                    all.add(k);
                }
            }
        }
        return all.toArray(new KeyStroke[0]);
    }

    public int size() {
        return bindings.size();
    }

    public InputMap getParent() {
        return parent;
    }

    public void setParent(InputMap map) {
        this.parent = map;
    }
}
