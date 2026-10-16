package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.MutableAttributeSet}: an {@link AttributeSet} whose
 * attributes can be added and removed.
 *
 * <p>Why hand-written: the {@link SimpleAttributeSet} the port's text components use for
 * typed-run attributes and the {@code StyledEditorKit} input attributes both need a real
 * mutable set; the generated stub did not exist at all (the client jar only ever reads
 * attributes through {@code AttributeSet}).
 */
public interface MutableAttributeSet extends AttributeSet {

    /** Binds {@code name} to {@code value}. */
    void addAttribute(Object name, Object value);

    /** Adds every attribute of {@code attributes}. */
    void addAttributes(AttributeSet attributes);

    /** Unbinds {@code name}. */
    void removeAttribute(Object name);

    /** Removes every attribute defined by {@code names} of {@code attributes}. */
    void removeAttributes(AttributeSet attributes);

    /** Unbinds the attributes named in {@code names}. */
    void removeAttributes(java.util.Enumeration<?> names);

    /** Replaces the resolve parent (null removes it). */
    void setResolveParent(AttributeSet parent);
}
