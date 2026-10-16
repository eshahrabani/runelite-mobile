package javax.swing;

import java.awt.Color;
import java.awt.Font;
import java.util.Hashtable;

/**
 * Swing {@code UIDefaults}: the key/value table behind {@link UIManager}.
 *
 * <p>Plain {@link Hashtable} semantics plus the typed accessors Swing code uses
 * ({@code getColor}, {@code getFont}, {@code getInt}, {@code getBorder}, …). Keys follow
 * the desktop names ({@code "Panel.background"}, {@code "ScrollBar.width"}, …) so a
 * plugin that reads them sees the port's dark theme.
 */
public class UIDefaults extends Hashtable<Object, Object> {
    private static final long serialVersionUID = 7343568809761415631L;

    public UIDefaults() {
        super();
    }

    public Color getColor(Object key) {
        Object value = get(key);
        return value instanceof Color ? (Color) value : null;
    }

    public Font getFont(Object key) {
        Object value = get(key);
        return value instanceof Font ? (Font) value : null;
    }

    public javax.swing.border.Border getBorder(Object key) {
        Object value = get(key);
        return value instanceof javax.swing.border.Border ? (javax.swing.border.Border) value : null;
    }

    public Icon getIcon(Object key) {
        Object value = get(key);
        return value instanceof Icon ? (Icon) value : null;
    }

    public String getString(Object key) {
        Object value = get(key);
        return value instanceof String ? (String) value : null;
    }

    public int getInt(Object key) {
        Object value = get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public boolean getBoolean(Object key) {
        Object value = get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    public javax.swing.plaf.ComponentUI getUI(JComponent target) {
        return UIManager.getUI(target);
    }

    public Class<?> getUIClass(String uiClassID) {
        Object value = get(uiClassID);
        if (value instanceof Class) {
            return (Class<?>) value;
        }
        if (value instanceof String) {
            try {
                return Class.forName((String) value);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    public void putDefaults(Object[] keyValueList) {
        for (int i = 0; i + 1 < keyValueList.length; i += 2) {
            if (keyValueList[i + 1] != null) {
                put(keyValueList[i], keyValueList[i + 1]);
            }
        }
    }

    public void addResourceBundle(String bundleName) {
        // Resource bundles are a desktop i18n concern; the port ships one language.
    }
}
