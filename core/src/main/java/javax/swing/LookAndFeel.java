package javax.swing;

/**
 * Swing {@code LookAndFeel}: the port ships exactly one theme, seeded straight into
 * {@link UIManager}'s defaults. The class exists so a plugin that asks for or installs a
 * look and feel links and runs; installing another one is accepted (its
 * {@link #installDefaults} is called) but the seeded values stay.
 */
public abstract class LookAndFeel {

    public static String getID() {
        return "RuneLiteMobile";
    }

    public static String getName() {
        return "RuneLite Mobile";
    }

    public String getID0() {
        return getID();
    }

    public String getDescription() {
        return "RuneLite's dark theme on the port's Swing runtime";
    }

    public boolean isNativeLookAndFeel() {
        return false;
    }

    public boolean isSupportedLookAndFeel() {
        return true;
    }

    /** Called by {@link UIManager#setLookAndFeel}; the host theme wins over any additions. */
    public void installDefaults(UIDefaults defaults) {
        UIDefaults newDefaults = getDefaults();
        if (newDefaults != null) {
            for (java.util.Map.Entry<Object, Object> entry : newDefaults.entrySet()) {
                defaults.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public UIDefaults getDefaults() {
        return null;
    }

    public void initialize() {
    }

    /**
     * Sets a component property only when the component still has the look and feel's
     * default for it (the JDK's contract: it must not clobber a value the application set).
     * The port has no per-LAF default tracking, so it applies the value only when the
     * current one is null or equals the seeded default.
     */
    public static void installProperty(JComponent c, String propertyName, Object value) {
        if (c == null || propertyName == null) {
            return;
        }
        Object current = c.getClientProperty("JComponent." + propertyName);
        if (current == null) {
            applyProperty(c, propertyName, value);
        }
    }

    private static void applyProperty(JComponent c, String propertyName, Object value) {
        switch (propertyName) {
            case "opaque":
                if (value instanceof Boolean) {
                    c.setOpaque((Boolean) value);
                }
                return;
            case "background":
                if (value instanceof java.awt.Color) {
                    c.setBackground((java.awt.Color) value);
                }
                return;
            case "foreground":
                if (value instanceof java.awt.Color) {
                    c.setForeground((java.awt.Color) value);
                }
                return;
            case "font":
                if (value instanceof java.awt.Font) {
                    c.setFont((java.awt.Font) value);
                }
                return;
            case "border":
                if (value instanceof javax.swing.border.Border) {
                    c.setBorder((javax.swing.border.Border) value);
                }
                return;
            default:
                c.putClientProperty(propertyName, value);
        }
    }

    public void uninitialize() {
    }

    public void provideErrorFeedback(java.awt.Component component) {
    }

    public String toString() {
        return getName();
    }
}
