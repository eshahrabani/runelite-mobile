package javax.swing.text;

/**
 * Hand-written {@code javax.swing.text.ViewFactory}: builds the {@link View}s of a document's
 * elements.
 *
 * <p>Why hand-written: {@link EditorKit#getViewFactory()} returns one, and the client's HTML
 * support ({@code HTMLEditorKit}) implements it. The generated stub declared no methods.
 *
 * <p>Port note: the port has no real view tree — {@code JTextComponent} lays text out itself —
 * so this interface exists for API completeness and for the HTML kit's own use.
 */
public interface ViewFactory {

    /** A view for {@code elem}, or null when this factory cannot handle that element. */
    View create(Element elem);
}
