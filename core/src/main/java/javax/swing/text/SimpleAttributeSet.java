package javax.swing.text;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hand-written {@code javax.swing.text.SimpleAttributeSet}: the real attribute map behind
 * {@link MutableAttributeSet}.
 *
 * <p>Why hand-written: {@code core/} replaces the desktop JRE classes the pre-compiled
 * RuneLite jars link against. The client resolves run attributes through
 * {@link AttributeSet}, so the port needs a set that actually stores values; the generated
 * stub had no members at all.
 *
 * <p>Deviation from the JDK: iteration order is insertion order (the JDK uses a hash
 * table) — irrelevant to observers, useful when printing a set.
 */
public class SimpleAttributeSet implements MutableAttributeSet, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /** The shared empty set handed out by {@link #EMPTY}. */
    public static final AttributeSet EMPTY = new SimpleAttributeSet();

    private Map<Object, Object> attributes;
    private AttributeSet resolveParent;

    public SimpleAttributeSet() {
    }

    public SimpleAttributeSet(AttributeSet source) {
        addAttributes(source);
    }

    /** True when the set holds no local attribute (a past-the-end marker in the JDK API). */
    public boolean isEmpty() {
        return attributes == null || attributes.isEmpty();
    }

    @Override
    public int getAttributeCount() {
        return attributes == null ? 0 : attributes.size();
    }

    @Override
    public boolean isDefined(Object attrName) {
        if (attributes != null && attributes.containsKey(attrName)) {
            return true;
        }
        return resolveParent != null && resolveParent.isDefined(attrName);
    }

    @Override
    public boolean isEqual(AttributeSet attr) {
        return getAttributeCount() == attr.getAttributeCount() && containsAttributes(attr);
    }

    @Override
    public AttributeSet copyAttributes() {
        SimpleAttributeSet copy = new SimpleAttributeSet();
        if (attributes != null) {
            copy.attributes = new LinkedHashMap<>(attributes);
        }
        copy.resolveParent = resolveParent;
        return copy;
    }

    @Override
    public Object getAttribute(Object key) {
        Object value = attributes == null ? null : attributes.get(key);
        if (value == null && resolveParent != null) {
            return resolveParent.getAttribute(key);
        }
        return value;
    }

    @Override
    public boolean containsAttribute(Object name, Object value) {
        return value == null ? getAttribute(name) == null : value.equals(getAttribute(name));
    }

    @Override
    public boolean containsAttributes(AttributeSet attrs) {
        if (attrs == null) {
            return true;
        }
        Enumeration<?> names = attrs.getAttributeNames();
        while (names.hasMoreElements()) {
            Object name = names.nextElement();
            if (!containsAttribute(name, attrs.getAttribute(name))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public AttributeSet getResolveParent() {
        return resolveParent;
    }

    @Override
    public void setResolveParent(AttributeSet parent) {
        this.resolveParent = parent;
    }

    @Override
    public void addAttribute(Object name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("attribute name may not be null");
        }
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, value);
    }

    /** Adds every attribute of {@code attrs}; a null source adds nothing. */
    @Override
    public void addAttributes(AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        Enumeration<?> names = attrs.getAttributeNames();
        while (names.hasMoreElements()) {
            Object name = names.nextElement();
            addAttribute(name, attrs.getAttribute(name));
        }
    }

    @Override
    public void removeAttribute(Object name) {
        if (attributes != null) {
            attributes.remove(name);
        }
    }

    @Override
    public void removeAttributes(AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        removeAttributes(attrs.getAttributeNames());
    }

    @Override
    public void removeAttributes(Enumeration<?> names) {
        while (names.hasMoreElements()) {
            removeAttribute(names.nextElement());
        }
    }

    @Override
    public Enumeration<?> getAttributeNames() {
        Map<Object, Object> copy = attributes == null ? Collections.emptyMap() : new LinkedHashMap<>(attributes);
        return Collections.enumeration(copy.keySet());
    }

    @Override
    public Object clone() {
        return copyAttributes();
    }

    @Override
    public int hashCode() {
        int hash = 0;
        Enumeration<?> names = getAttributeNames();
        while (names.hasMoreElements()) {
            Object name = names.nextElement();
            Object value = getAttribute(name);
            hash += name.hashCode() ^ (value == null ? 0 : value.hashCode());
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AttributeSet && isEqual((AttributeSet) obj);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        Enumeration<?> names = getAttributeNames();
        boolean first = true;
        while (names.hasMoreElements()) {
            Object name = names.nextElement();
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(name).append('=').append(getAttribute(name));
        }
        return sb.append('}').toString();
    }
}
