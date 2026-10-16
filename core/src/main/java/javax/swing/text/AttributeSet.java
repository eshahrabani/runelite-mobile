package javax.swing.text;

import java.util.Enumeration;

/**
 * Hand-written {@code javax.swing.text.AttributeSet}: the immutable attribute map a
 * {@link Document}'s {@link Element}s and {@link AbstractDocument}'s runs carry.
 *
 * <p>Why hand-written: {@code core/} replaces the desktop JRE classes the pre-compiled
 * RuneLite jars link against, and {@code javax.text} is one of the packages RuneLite's
 * text widgets really use (the color-picker's hex field installs a
 * {@link DocumentFilter}; Notes registers a document listener). The generated data-only
 * stub could not store a single attribute, so the whole map is implemented here.
 *
 * <p>Deviation from the JDK: nothing is synchronised and {@link #getAttributeNames()}
 * returns a snapshot copy — the port never mutates an attribute set from a second thread.
 */
public interface AttributeSet {

    /** The number of attributes this set defines locally (not counting its parent). */
    int getAttributeCount();

    /** True when {@code attrName} is defined here or in this set's resolve parent. */
    boolean isDefined(Object attrName);

    /** True when {@code attr} defines exactly the same attributes as this set. */
    boolean isEqual(AttributeSet attr);

    /** A mutable copy of this set, detached from its resolve parent. */
    AttributeSet copyAttributes();

    /** The value of {@code key}, resolved through the resolve parent when absent here. */
    Object getAttribute(Object key);

    /** True when every attribute of {@code attributes} is present here with the same value. */
    boolean containsAttributes(AttributeSet attributes);

    /** True when {@code name} is bound to {@code value} in this set (or its parent). */
    boolean containsAttribute(Object name, Object value);

    /** This set's resolve parent, or null when it has none. */
    AttributeSet getResolveParent();

    /** The attribute keys defined by this set and its resolve parents. */
    Enumeration<?> getAttributeNames();

    /** Marker type signature for keys that select a font (JDK contract). */
    interface FontAttribute {
    }

    /** Marker type signature for keys that select a colour (JDK contract). */
    interface ColorAttribute {
    }

    /** Marker type signature for keys that select a character attribute (JDK contract). */
    interface CharacterAttribute {
    }

    /** Marker type signature for keys that select a paragraph attribute (JDK contract). */
    interface ParagraphAttribute {
    }
}
