package javax.swing;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;

/**
 * Hand-written {@code javax.swing.JPasswordField}: a single-line field that echoes a mask
 * character instead of the text.
 *
 * <p>Why hand-written: the plugin-hub and account panels use it for tokens and secrets; the
 * generated stub painted nothing at all and had no echo character, so the field showed neither
 * the typed text nor its mask.
 *
 * <p>Semantics follow the JDK: {@link #getText()} still answers the real text (deprecated
 * there for the same reason as here — it defeats the mask), {@link #getPassword()} answers it
 * as a {@code char[]}, and {@code copy()}/{@code cut()} deliberately do nothing so a masked
 * value never lands on the clipboard.
 */
public class JPasswordField extends JTextField {

    private char echoChar;

    public JPasswordField() {
        this(null, null, 0);
    }

    public JPasswordField(String text) {
        this(null, text, 0);
    }

    public JPasswordField(int columns) {
        this(null, null, columns);
    }

    public JPasswordField(String text, int columns) {
        this(null, text, columns);
    }

    public JPasswordField(Document doc, String txt, int columns) {
        super(doc, txt, columns);
        setEchoChar('*');
    }

    /** The UI delegate's class id (kept for API shape). */
    @Override
    public String getUIClassID() {
        return "PasswordFieldUI";
    }

    @Override
    protected Document createDefaultModel() {
        return new PlainDocument();
    }

    /** The character drawn in place of each typed character. */
    public char getEchoChar() {
        return echoChar;
    }

    public void setEchoChar(char c) {
        char old = echoChar;
        echoChar = c;
        firePropertyChange("echoChar", old, c);
        invalidateLines();
        repaint();
    }

    /** True when a mask character is set. */
    public boolean echoCharIsSet() {
        return echoChar != 0;
    }

    /** The text as characters (the JDK's accessor; the array is a copy). */
    public char[] getPassword() {
        String text = super.getText();
        return text == null ? new char[0] : text.toCharArray();
    }

    /** The characters as displayed: one mask character per character of the text. */
    @Override
    protected String getDisplayText(int start, int end) {
        String raw = super.getDisplayText(start, end);
        if (!echoCharIsSet() || raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            sb.append(echoChar);
        }
        return sb.toString();
    }

    /** A masked value is never copied to the clipboard. */
    @Override
    public void copy() {
    }

    /** A masked value is never cut to the clipboard. */
    @Override
    public void cut() {
    }

    /** The text of the field; the mask is only about painting. */
    @Override
    public String getText() {
        return super.getText();
    }

}
