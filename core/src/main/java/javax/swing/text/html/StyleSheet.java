package javax.swing.text.html;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written {@code javax.swing.text.html.StyleSheet}: the rule holder RuneLite's
 * {@code JRichTextPane} fills in from its constructor
 * ({@code getEditorKitForContentType("text/html").getStyleSheet().addRule("a {color: #DDDDDD}")}).
 *
 * <p>It is hand-written because the generated data-only stub returned {@code null} from
 * {@link HTMLEditorKit#getStyleSheet()}, so that {@code addRule} call threw a
 * {@code NullPointerException} while {@code InfoPlugin} was building its panel — the
 * documented {@code InfoPlugin} blocker.
 *
 * <p>Deliberate deviation from the JDK: the rules are stored verbatim and are
 * <em>never</em> parsed, matched or cascaded. This port has no {@code HTMLDocument}
 * element tree to apply them to; HTML strings are drawn by {@link HTMLRenderer}, which
 * understands the presentational subset listed there and ignores stylesheet rules. The
 * rules are kept because consumers legitimately add them and because a later
 * implementation could pick them up.
 */
public class StyleSheet extends javax.swing.text.StyleContext {

    private final List<String> rules = new ArrayList<>();

    public StyleSheet() {
        super();
    }

    /**
     * Stores a CSS rule verbatim, in insertion order. {@code null} and blank rules are
     * ignored (the JDK's parser drops them too).
     */
    public void addRule(String rule) {
        if (rule == null) {
            return;
        }
        String trimmed = rule.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        rules.add(trimmed);
    }

    /** The rules added to this sheet so far, in insertion order (a defensive copy). */
    public String[] getRules() {
        return rules.toArray(new String[0]);
    }
}
