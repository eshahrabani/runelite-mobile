package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.Element}: one node of a {@link Document}'s element
 * tree (a paragraph, a line, or a leaf run).
 *
 * <p>Why hand-written: {@link AbstractDocument} really builds this tree so that
 * {@code getDefaultRootElement()} — called by RuneLite's notes/hub panels through the
 * document — answers with offsets instead of throwing; the generated stub declared no
 * members.
 */
public interface Element {

    /** The document this element belongs to. */
    Document getDocument();

    /** The enclosing element, or null for the root. */
    Element getParentElement();

    /** The element's name ({@link AbstractDocument#ParagraphElementName} etc.). */
    String getName();

    /** The element's attributes, or null when it has none. */
    AttributeSet getAttributes();

    /** The offset in the document where this element starts (inclusive). */
    int getStartOffset();

    /** The offset in the document where this element ends (exclusive). */
    int getEndOffset();

    /**
     * The index of the child that best represents {@code offset}: the child containing it,
     * or the closest child when the offset is outside this element's range.
     */
    int getElementIndex(int offset);

    /** The number of child elements. */
    int getElementCount();

    /** The child at {@code index}. */
    Element getElement(int index);

    /** True when this element has no children. */
    boolean isLeaf();
}
