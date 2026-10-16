package javax.swing.text.html;

import java.awt.Cursor;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.ViewFactory;

/**
 * Hand-written {@code javax.swing.text.html.HTMLEditorKit}: the content-type handler a
 * {@code JEditorPane} asks for when it is switched to {@code text/html}.
 *
 * <p>The generated data-only stub returned {@code null} from {@link #getStyleSheet()}
 * (and the class itself was {@code abstract}), so RuneLite's {@code JRichTextPane} —
 * which casts the result of
 * {@code getEditorKitForContentType("text/html")} to this type and calls
 * {@code getStyleSheet().addRule("a {color: #DDDDDD}")} — died with a
 * {@code NullPointerException} while {@code InfoPlugin} built its panel.
 *
 * <p>Port deviations, confined to this class: <b>there is no {@code View} hierarchy,
 * no {@code HTMLDocument} and no HTML parser</b>. {@link #getViewFactory()} therefore
 * returns {@code null} and {@link #createDefaultDocument()} a
 * {@link javax.swing.text.PlainDocument} (the JDK's {@code HTMLDocument} does not exist
 * in core) — which is enough here, because {@link javax.swing.JEditorPane} paints the
 * document's text through {@link HTMLRenderer} instead of through views.
 * {@code read}/{@code write}/{@code getActions}/{@code createCaret} are inherited from
 * {@code DefaultEditorKit} and so treat the content as plain text: nothing on this port
 * parses HTML into a document. Layout and painting of HTML strings happen in
 * {@link HTMLRenderer}, which the pane drives directly, and link activation is owned by
 * the pane as well (it wires its own mouse listener in its constructor), so
 * {@link #install(JEditorPane)} only applies the kit's default cursor.
 */
public class HTMLEditorKit extends javax.swing.text.StyledEditorKit
        implements javax.accessibility.Accessible {

    /** Cursor a pane showing this kit's content falls back to. */
    private Cursor defaultCursor = Cursor.getDefaultCursor();

    private StyleSheet styleSheet = new StyleSheet();

    public HTMLEditorKit() {
        super();
    }

    // ----------------------------------------------------------- style sheet

    /**
     * The style sheet this kit hands out. Never {@code null}: consumers add their own
     * rules to it even though {@link StyleSheet} does not cascade them.
     */
    public StyleSheet getStyleSheet() {
        return styleSheet;
    }

    /** Replaces the style sheet; a {@code null} argument is ignored (the JDK NPEs). */
    public void setStyleSheet(StyleSheet styleSheet) {
        if (styleSheet != null) {
            this.styleSheet = styleSheet;
        }
    }

    // ------------------------------------------------------------ EditorKit

    /** This kit serves {@code text/html} only. */
    public String getContentType() {
        return "text/html";
    }

    /**
     * A plain document: the JDK's {@code HTMLDocument} does not exist in this port, and
     * a {@code JEditorPane} renders the document's text as HTML through
     * {@link HTMLRenderer} anyway, so there is nothing an HTML-aware document would add.
     */
    public Document createDefaultDocument() {
        return new javax.swing.text.PlainDocument();
    }

    /** {@code null}: the port has no {@code View} hierarchy (see the class note). */
    public ViewFactory getViewFactory() {
        return null;
    }

    /** Applies the kit's default cursor to {@code c}. */
    public void install(JEditorPane c) {
        if (c != null) {
            c.setCursor(defaultCursor);
        }
    }

    /** Clears the cursor {@link #install(JEditorPane)} applied. */
    public void deinstall(JEditorPane c) {
        if (c != null) {
            c.setCursor(null);
        }
    }

    public void setDefaultCursor(Cursor cursor) {
        if (cursor != null) {
            this.defaultCursor = cursor;
        }
    }

    public Cursor getDefaultCursor() {
        return defaultCursor;
    }
}
