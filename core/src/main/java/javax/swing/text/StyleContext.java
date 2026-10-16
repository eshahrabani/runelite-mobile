package javax.swing.text;

import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hand-written {@code javax.swing.text.StyleContext}: a font/attribute context and the registry
 * of named styles.
 *
 * <p>Why this file is hand-written instead of generated: RuneLite's {@code FontManager} static
 * initialiser calls
 *
 * <pre>
 *   StyleContext styleContext = StyleContext.getDefaultStyleContext();
 *   runescapeFont = styleContext.getFont("RuneScape", Font.PLAIN, 16);
 * </pre>
 *
 * and a stub that returned null from the static factory poisoned {@code FontManager.<clinit>}
 * for the whole process ({@code NoClassDefFoundError} for every later user, overlays included).
 * This class therefore keeps that path real, and additionally implements
 * {@link AbstractDocument.AttributeContext} so a document built with it can merge attribute
 * sets, plus the named-style registry the interface implies.
 *
 * <p>Font resolution is deliberately simple: the port's {@link Font} carries the TTF bytes
 * registered with {@code GraphicsEnvironment} (the RuneScape fonts the client jar ships), and
 * {@code new Font(family, style, size)} picks those up by name.
 */
public class StyleContext implements AbstractDocument.AttributeContext, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private static final StyleContext DEFAULT = new StyleContext();

    private static final AttributeSet EMPTY = new SimpleAttributeSet();

    private final Map<String, AttributeSet> styles = new LinkedHashMap<>();

    /** The context every caller shares on this port. */
    public static StyleContext getDefaultStyleContext() {
        return DEFAULT;
    }

    public StyleContext() {
    }

    /** A font for the given logical family, style and size. */
    public Font getFont(String family, int style, int size) {
        return new Font(family, style, size);
    }

    /** The font described by an attribute set (family/style/size), or the default font. */
    public Font getFont(AttributeSet attr) {
        Object family = attr == null ? null : attr.getAttribute("family");
        Object size = attr == null ? null : attr.getAttribute("size");
        Object bold = attr == null ? null : attr.getAttribute("bold");
        Object italic = attr == null ? null : attr.getAttribute("italic");
        int style = (Boolean.TRUE.equals(bold) ? Font.BOLD : Font.PLAIN)
                | (Boolean.TRUE.equals(italic) ? Font.ITALIC : Font.PLAIN);
        int pointSize = size instanceof Number ? ((Number) size).intValue() : 12;
        return new Font(family == null ? "Dialog" : family.toString(), style, pointSize);
    }

    public Font getFont(java.util.Map<?, ?> attributes) {
        return getFont("Dialog", Font.PLAIN, 12);
    }

    public Font getFont(String family, int style, int size, java.util.Map<?, ?> attributes) {
        return getFont(family, style, size);
    }

    /** The named style, or null. */
    public AttributeSet getStyle(String nm) {
        return styles.get(nm);
    }

    /** Registers (or with a null value removes) a named style. */
    public void addStyle(String nm, AttributeSet parent) {
        if (nm == null) {
            return;
        }
        if (parent == null) {
            styles.remove(nm);
        } else {
            styles.put(nm, parent);
        }
    }

    /** The names of the registered styles, parent-first. */
    public java.util.Enumeration<?> getStyleNames() {
        return java.util.Collections.enumeration(new java.util.ArrayList<>(styles.keySet()));
    }

    /** Removes a named style. */
    public void removeStyle(String nm) {
        styles.remove(nm);
    }

    // ------------------------------------- AbstractDocument.AttributeContext

    @Override
    public AttributeSet addAttribute(AttributeSet old, Object name, Object value) {
        if (old instanceof MutableAttributeSet) {
            ((MutableAttributeSet) old).addAttribute(name, value);
            return old;
        }
        SimpleAttributeSet set = copyOf(old);
        set.addAttribute(name, value);
        return set;
    }

    @Override
    public AttributeSet addAttributes(AttributeSet old, AttributeSet attributes) {
        if (old instanceof MutableAttributeSet) {
            ((MutableAttributeSet) old).addAttributes(attributes);
            return old;
        }
        SimpleAttributeSet set = copyOf(old);
        set.addAttributes(attributes);
        return set;
    }

    @Override
    public AttributeSet removeAttribute(AttributeSet old, Object name) {
        if (old instanceof MutableAttributeSet) {
            ((MutableAttributeSet) old).removeAttribute(name);
            return old;
        }
        SimpleAttributeSet set = copyOf(old);
        set.removeAttribute(name);
        return set;
    }

    @Override
    public AttributeSet removeAttributes(AttributeSet old, java.util.Enumeration<?> names) {
        if (old == null) {
            return EMPTY;
        }
        SimpleAttributeSet set = copyOf(old);
        set.removeAttributes(names);
        return set;
    }

    @Override
    public AttributeSet removeAttributes(AttributeSet old, AttributeSet attributes) {
        if (old == null) {
            return EMPTY;
        }
        SimpleAttributeSet set = copyOf(old);
        set.removeAttributes(attributes);
        return set;
    }

    @Override
    public AttributeSet getEmptySet() {
        return EMPTY;
    }

    @Override
    public void reclaim(AttributeSet a) {
        // Immutable-by-convention sets are simply dropped on this port.
    }

    private static SimpleAttributeSet copyOf(AttributeSet source) {
        return source == null ? new SimpleAttributeSet() : new SimpleAttributeSet(source);
    }
}
