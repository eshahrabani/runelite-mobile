package javax.swing.text;

import java.awt.Graphics;
import java.awt.Shape;

/**
 * Hand-written {@code javax.swing.text.Highlighter}: the marks drawn behind a
 * {@link JTextComponent}'s text (searches, matched links, …).
 *
 * <p>Why hand-written: {@code JRichTextPane} — the info panel's rich-text view, shipped in
 * the client jar — calls {@code setHighlighter(null)} on itself to switch the desktop
 * editor's selection highlight off, and {@link JTextComponent} hands a non-null highlighter
 * to anything that asks for one. The generated stub was an empty interface, so
 * {@code getHighlighter()} could only ever return null and that call could not be served.
 *
 * <p>Deviation from the JDK: the port has no {@code View} hierarchy to paint layers through,
 * so the layered entry points are accepted and ignored (see {@link DefaultHighlighter}).
 */
public interface Highlighter {

    /** Binds this highlighter to a component. */
    void install(JTextComponent c);

    /** Detaches this highlighter from its component. */
    void deinstall(JTextComponent c);

    /** Marks the range {@code p0}..{@code p1} and returns the tag that identifies it. */
    Object addHighlight(int p0, int p1, HighlightPainter p) throws BadLocationException;

    /** Removes the highlight created under {@code tag}. */
    void removeHighlight(Object tag);

    /** Removes every highlight. */
    void removeAllHighlights();

    /** Moves the highlight created under {@code tag} to a new range. */
    void changeHighlight(Object tag, int p0, int p1) throws BadLocationException;

    /** Every current highlight. */
    Highlight[] getHighlights();

    /** Paints the highlights. */
    void paint(Graphics g);

    /** One mark of a highlighter. */
    interface Highlight {
        /** The offset the mark starts at. */
        int getStartOffset();

        /** The offset the mark ends at. */
        int getEndOffset();

        /** The painter that draws the mark. */
        HighlightPainter getPainter();
    }

    /** Draws one highlight range. */
    interface HighlightPainter {
        /**
         * Paints the range {@code p0}..{@code p1} of {@code c}, whose whole visible area is
         * {@code bounds}.
         */
        void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c);
    }

    /** A highlight painter that draws on the text's own layer. */
    interface LayerPainter extends HighlightPainter {
        /**
         * Paints the range inside {@code viewBounds} of {@code view}.
         *
         * @return the shape that was painted, or null when nothing was
         */
        Shape paintLayer(Graphics g, int p0, int p1, Shape viewBounds, JTextComponent editor, View view);
    }
}
