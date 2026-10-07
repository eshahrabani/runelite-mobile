package javax.swing.text;

import java.awt.Font;

/**
 * Hand-written {@code javax.swing.text.StyleContext}: RuneLite's {@code FontManager}
 * static initialiser is the only user, and it does
 *
 * <pre>
 *   StyleContext styleContext = StyleContext.getDefaultStyleContext();
 *   runescapeFont = styleContext.getFont("RuneScape", Font.PLAIN, 16);
 * </pre>
 *
 * <p>A generated data-only stub returns {@code null} from the static factory, and the
 * next call then throws {@code NullPointerException} inside {@code FontManager.<clinit>}
 * -- which poisons that class for the whole process ({@code NoClassDefFoundError} for
 * every later user, overlays included). So the singleton has to be real.
 *
 * <p>Font resolution is deliberately simple: the port's {@code java.awt.Font} carries the
 * TTF bytes registered with {@code GraphicsEnvironment} (the three RuneScape fonts the
 * client jar ships), and {@code new Font(family, style, size)} picks those up by name.
 */
public class StyleContext {

    private static final StyleContext DEFAULT = new StyleContext();

    public static StyleContext getDefaultStyleContext() {
        return DEFAULT;
    }

    public Font getFont(String family, int style, int size) {
        return new Font(family, style, size);
    }

    public Font getFont(java.util.Map<?, ?> attributes) {
        return new Font("Dialog", Font.PLAIN, 12);
    }

    public Font getFont(String family, int style, int size, java.util.Map<?, ?> attributes) {
        return getFont(family, style, size);
    }
}
