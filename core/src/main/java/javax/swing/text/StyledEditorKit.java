package javax.swing.text;

import javax.swing.Action;

/**
 * Hand-written {@code javax.swing.text.StyledEditorKit}: the editor kit of styled text.
 *
 * <p>Why hand-written: RuneLite's HTML support ({@code HTMLEditorKit}, used by the info panel's
 * {@code JRichTextPane}) extends it, and the client jar references
 * {@code javax.swing.text.EditorKit}'s subclass. The generated stub declared only a constructor.
 *
 * <p>Deviations from the JDK: the character-attribute actions
 * ({@code BoldAction}/{@code ItalicAction}/…) are not provided — the port's rich text is
 * rendered by {@code javax.swing.text.html.HTMLRenderer}, which parses the markup itself
 * instead of editing attribute sets — and {@link #createDefaultDocument()} answers a
 * {@link PlainDocument} rather than a {@code DefaultStyledDocument}, because styled documents
 * and their attribute runs are not part of this port.
 */
public class StyledEditorKit extends DefaultEditorKit {

    private final MutableAttributeSet inputAttributes = new SimpleAttributeSet();

    public StyledEditorKit() {
    }

    /** The attributes applied to text typed at the caret. */
    public MutableAttributeSet getInputAttributes() {
        return inputAttributes;
    }

    @Override
    public Document createDefaultDocument() {
        return new PlainDocument();
    }

    @Override
    public Action[] getActions() {
        return super.getActions();
    }

    @Override
    public Object clone() {
        return super.clone();
    }
}
