package javax.swing;

import java.awt.Color;
import java.awt.Font;

/**
 * Swing {@code UIManager}: the port's single theme table.
 *
 * <p>Instead of a pluggable look and feel the port seeds the values RuneLite's dark
 * theme expects (this is what {@code RuneLiteLAF} would have set on the desktop) and
 * answers the rest from the same table. {@link #getUI(JComponent)} keeps the desktop
 * mechanism — the component's {@code UIClassID} names a class whose static
 * {@code createUI(JComponent)} builds the delegate — so {@code updateUI()} and
 * {@link SwingUtilities#updateComponentTreeUI} work for widgets that install their UI
 * through the table.
 */
public class UIManager {
    private static final UIDefaults DEFAULTS = new UIDefaults();
    private static final UIDefaults LOOK_AND_FEEL_DEFAULTS = new UIDefaults();
    private static LookAndFeel lookAndFeel;

    static {
        seedTheme();
    }

    private UIManager() {
    }

    // ------------------------------------------------------------- theme

    /** RuneLite's dark theme values, as the desktop LookAndFeel installs them. */
    private static void seedTheme() {
        Color darkGray = new Color(40, 40, 40);
        Color darkerGray = new Color(30, 30, 30);
        Color darkerGrayHover = new Color(60, 60, 60);
        Color control = new Color(30, 30, 30);
        Color border = new Color(23, 23, 23);
        Color text = new Color(198, 198, 198);
        Color selection = new Color(77, 77, 77);

        put("Panel.background", darkGray);
        put("Panel.foreground", text);
        put("Component.background", darkGray);
        put("Component.foreground", text);
        put("Component.font", new Font("Dialog", Font.PLAIN, 12));
        put("Component.borderColor", border);
        put("Component.arc", 0);
        put("Component.innerFocusWidth", 0);
        put("Component.innerOutlineWidth", 0);
        put("Label.background", darkGray);
        put("Label.foreground", text);
        put("Label.font", smallFont());
        put("Button.background", control);
        put("Button.foreground", Color.WHITE);
        put("Button.borderColor", border);
        put("Button.arc", 0);
        put("Button.font", smallFont());
        put("CheckBox.background", control);
        put("CheckBox.foreground", text);
        put("CheckBox.font", smallFont());
        put("CheckBox.icon.background", control);
        put("CheckBox.icon.size", 22);
        put("CheckBox.icon.focusWidth", 0);
        put("RadioButton.background", control);
        put("RadioButton.foreground", text);
        put("RadioButton.font", smallFont());
        put("ToggleButton.background", control);
        put("ToggleButton.foreground", text);
        put("ComboBox.background", control);
        put("ComboBox.foreground", text);
        put("ComboBox.font", smallFont());
        put("ComboBox.selectionBackground", selection);
        put("ComboBox.selectionForeground", Color.WHITE);
        put("TextField.background", control);
        put("TextField.foreground", text);
        put("TextField.font", smallFont());
        put("TextField.selectionBackground", selection);
        put("TextField.selectionForeground", Color.WHITE);
        put("FormattedTextField.background", control);
        put("FormattedTextField.foreground", text);
        put("FormattedTextField.font", smallFont());
        put("PasswordField.background", control);
        put("PasswordField.foreground", text);
        put("TextArea.background", control);
        put("TextArea.foreground", text);
        put("TextArea.font", smallFont());
        put("List.background", control);
        put("List.foreground", text);
        put("List.font", smallFont());
        put("List.selectionBackground", selection);
        put("List.selectionForeground", Color.WHITE);
        put("Spinner.background", control);
        put("Spinner.foreground", text);
        put("Spinner.font", smallFont());
        put("ProgressBar.background", control);
        put("ProgressBar.foreground", new Color(0, 160, 0));
        put("ProgressBar.selectionBackground", Color.WHITE);
        put("ProgressBar.selectionForeground", Color.WHITE);
        put("ProgressBar.arc", 0);
        put("ScrollBar.width", 7);
        put("ScrollBar.minimumButtonSize", new java.awt.Dimension(7, 7));
        put("ScrollBar.minimumThumbSize", new java.awt.Dimension(7, 7));
        put("ScrollBar.track", new Color(25, 25, 25));
        put("ScrollBar.thumb", selection);
        put("ScrollBar.background", new Color(25, 25, 25));
        put("ScrollBar.thumbDarkShadow", new Color(25, 25, 25));
        put("ScrollBar.thumbHighlight", new Color(25, 25, 25));
        put("ScrollBar.thumbShadow", new Color(25, 25, 25));
        put("ScrollBar.trackHighlight", new Color(25, 25, 25));
        put("ScrollPane.background", darkGray);
        put("ScrollPane.border", BorderFactory.createEmptyBorder());
        put("Viewport.background", darkGray);
        put("TabbedPane.font", smallFont());
        put("TabbedPane.background", darkerGray);
        put("TabbedPane.foreground", text);
        put("TabbedPane.tabBackground", darkerGray);
        put("TabbedPane.selectedTabBackground", darkerGrayHover);
        put("TabbedPane.tabHeight", 26);
        put("TabbedPane.tabInsets", new java.awt.Insets(2, 5, 2, 5));
        put("TabbedPane.evenlyWrapTabs", Boolean.TRUE);
        put("TabbedPane.expandWrappedTabs", Boolean.FALSE);
        put("ToolTip.background", new Color(46, 46, 46));
        put("ToolTip.foreground", text);
        put("ToolTip.font", smallFont());
        put("Menu.background", control);
        put("Menu.foreground", text);
        put("Menu.font", smallFont());
        put("Menu.selectionBackground", selection);
        put("Menu.selectionForeground", Color.WHITE);
        put("MenuItem.background", control);
        put("MenuItem.foreground", text);
        put("MenuItem.font", smallFont());
        put("MenuItem.selectionBackground", selection);
        put("MenuItem.selectionForeground", Color.WHITE);
        put("PopupMenu.background", control);
        put("PopupMenu.foreground", text);
        put("PopupMenu.border", BorderFactory.createLineBorder(border));
        put("MenuBar.background", new Color(60, 60, 60));
        put("MenuBar.foreground", text);
        put("TitlePane.font", new Font("Dialog", Font.BOLD, 12));
        put("OptionPane.background", darkerGray);
        put("OptionPane.foreground", text);
        put("OptionPane.messageForeground", text);
        put("OptionPane.buttonFont", smallFont());
        put("Separator.foreground", border);
        put("Separator.background", darkGray);
        put("SplitPane.background", darkGray);
        put("SplitPane.dividerSize", 8);
        put("Panel.font", smallFont());
        put("EditorPane.background", control);
        put("EditorPane.foreground", text);
        put("EditorPane.font", smallFont());
        put("Tree.background", control);
        put("Tree.foreground", text);
        put("Tree.font", smallFont());
        put("Table.background", control);
        put("Table.foreground", text);
        put("Table.font", smallFont());
        put("Table.selectionBackground", selection);
        put("Table.selectionForeground", Color.WHITE);
        put("TableHeader.background", darkerGray);
        put("TableHeader.foreground", text);
    }

    /**
     * Supplies the port's small UI font. The RuneLite fonts live in the asset dex
     * ({@code net.runelite.client.ui.FontManager}), which core cannot reference at
     * compile time, so the host installs this supplier before the sidebar is built.
     */
    private static volatile java.util.function.Supplier<Font> defaultFontSupplier;

    public static void setDefaultFontSupplier(java.util.function.Supplier<Font> supplier) {
        if (supplier != null && defaultFontSupplier == null) {
            defaultFontSupplier = supplier;
            // The theme was seeded before the RuneScape fonts existed (they come from the
            // asset dex), so every font key has to be re-seeded now that they do.
            seedFonts();
        } else {
            defaultFontSupplier = supplier;
        }
    }

    /** Re-puts every {@code *.font} key from the current supplier. */
    private static void seedFonts() {
        Font font = smallFont();
        Font bold = font.deriveFont(Font.BOLD);
        for (String key : FONT_KEYS) {
            DEFAULTS.put(key, key.contains("bButton") || key.contains("Title") ? bold : font);
        }
    }

    private static final String[] FONT_KEYS = {
        "Component.font", "Panel.font", "Label.font", "Button.font", "CheckBox.font",
        "RadioButton.font", "ToggleButton.font", "ComboBox.font", "TextField.font",
        "FormattedTextField.font", "TextArea.font", "List.font", "Spinner.font",
        "TabbedPane.font", "ToolTip.font", "Menu.font", "MenuItem.font", "EditorPane.font",
        "Tree.font", "Table.font", "OptionPane.buttonFont",
    };

    private static Font smallFont() {
        java.util.function.Supplier<Font> supplier = defaultFontSupplier;
        if (supplier != null) {
            try {
                Font font = supplier.get();
                if (font != null) {
                    return font;
                }
            } catch (Throwable ignored) {
                // Fall through to the logical default.
            }
        }
        return new Font("Dialog", Font.PLAIN, 12);
    }

    // ------------------------------------------------------------- access

    public static UIDefaults getDefaults() {
        return DEFAULTS;
    }

    public static void put(Object key, Object value) {
        DEFAULTS.put(key, value);
    }

    public static Object get(Object key) {
        return DEFAULTS.get(key);
    }

    public static Color getColor(Object key) {
        return DEFAULTS.getColor(key);
    }

    public static Font getFont(Object key) {
        return DEFAULTS.getFont(key);
    }

    public static javax.swing.border.Border getBorder(Object key) {
        return DEFAULTS.getBorder(key);
    }

    public static Icon getIcon(Object key) {
        return DEFAULTS.getIcon(key);
    }

    public static String getString(Object key) {
        return DEFAULTS.getString(key);
    }

    public static int getInt(Object key) {
        return DEFAULTS.getInt(key);
    }

    public static boolean getBoolean(Object key) {
        return DEFAULTS.getBoolean(key);
    }

    /** Builds a component's UI through its {@code UIClassID}'s {@code createUI}. */
    public static javax.swing.plaf.ComponentUI getUI(JComponent target) {
        if (target == null) {
            return null;
        }
        String classId = target.getUIClassID();
        if (classId == null) {
            return null;
        }
        Class<?> uiClass = DEFAULTS.getUIClass(classId);
        if (uiClass == null) {
            return null;
        }
        try {
            java.lang.reflect.Method create = uiClass.getMethod("createUI", JComponent.class);
            Object ui = create.invoke(null, target);
            return ui instanceof javax.swing.plaf.ComponentUI ? (javax.swing.plaf.ComponentUI) ui : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The InputMap for a component condition; the port keeps a per-class map. */
    public static InputMap getInputMap(JComponent component, int condition) {
        return getInputMap(component, condition, true);
    }

    public static InputMap getInputMap(JComponent component, int condition, boolean create) {
        if (condition == JComponent.WHEN_FOCUSED) {
            return component.getInputMap(JComponent.WHEN_FOCUSED);
        }
        InputMap map = INPUT_MAPS.get(component.getClass().getName() + "#" + condition);
        if (map == null && create) {
            map = new InputMap();
            INPUT_MAPS.put(component.getClass().getName() + "#" + condition, map);
        }
        return map;
    }

    private static final java.util.Map<String, InputMap> INPUT_MAPS = new java.util.concurrent.ConcurrentHashMap<>();

    // --------------------------------------------------- look and feel

    public static LookAndFeel getLookAndFeel() {
        return lookAndFeel;
    }

    public static void setLookAndFeel(LookAndFeel newLookAndFeel) {
        lookAndFeel = newLookAndFeel;
        if (newLookAndFeel != null) {
            newLookAndFeel.installDefaults(DEFAULTS);
        }
    }

    public static void setLookAndFeel(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            LookAndFeel laf = (LookAndFeel) clazz.getDeclaredConstructor().newInstance();
            setLookAndFeel(laf);
        } catch (Throwable t) {
            // Nothing installs a third-party look and feel on this port; ignore.
        }
    }

    public static String getCrossPlatformLookAndFeelClassName() {
        return "javax.swing.plaf.metal.MetalLookAndFeel";
    }

    public static String getSystemLookAndFeelClassName() {
        return getCrossPlatformLookAndFeelClassName();
    }

    public static void setInstalledLookAndFeels(javax.swing.UIManager.LookAndFeelInfo[] infos) {
    }

    public static LookAndFeelInfo[] getInstalledLookAndFeels() {
        return new LookAndFeelInfo[0];
    }

    /** The JDK's LAF descriptor; kept so plugins that iterate the list compile and run. */
    public static class LookAndFeelInfo {
        private final String name;
        private final String className;

        public LookAndFeelInfo(String name, String className) {
            this.name = name;
            this.className = className;
        }

        public String getName() {
            return name;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return getClass().getName() + "[" + name + "]";
        }
    }
}
