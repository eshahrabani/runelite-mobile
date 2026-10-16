package javax.swing.text;

import java.awt.Graphics;
import java.awt.Point;

import javax.swing.event.ChangeListener;

/**
 * Hand-written {@code javax.swing.text.Caret}: the text insertion point and selection anchor
 * of a {@link JTextComponent}.
 *
 * <p>Why hand-written: the port's text fields really show a caret and really select, and the
 * plan's key handling is defined in terms of the caret's dot/mark. The generated stub declared
 * no members, so {@code getCaret()} could only ever answer null.
 *
 * <p>Deviation from the JDK: the position-with-bias overloads ({@code setDot(int,
 * Position.Bias)}, {@code moveDot(int, Position.Bias)}) and the {@code getDotBias()}/
 * {@code getMarkBias()} accessors are absent — nothing on this port uses bidi text. Caret
 * listeners live on the component (as in the JDK), not on the caret.
 */
public interface Caret {

    /** Binds this caret to a component. */
    void install(JTextComponent c);

    /** Detaches this caret from its component. */
    void deinstall(JTextComponent c);

    /** Paints the caret. */
    void paint(Graphics g);

    /** Registers a listener for caret movement. */
    void addChangeListener(ChangeListener l);

    /** Removes a listener registered with {@link #addChangeListener}. */
    void removeChangeListener(ChangeListener l);

    /** True when the caret is drawn. */
    boolean isVisible();

    /** Shows or hides the caret. */
    void setVisible(boolean v);

    /** True when the selection is drawn. */
    boolean isSelectionVisible();

    /** Shows or hides the selection. */
    void setSelectionVisible(boolean v);

    /** Remembers a preferred caret position (used to keep the column while moving by line). */
    void setMagicCaretPosition(Point p);

    /** The preferred caret position remembered by {@link #setMagicCaretPosition}, or null. */
    Point getMagicCaretPosition();

    /** Sets the blink rate in milliseconds (0 means no blinking). */
    void setBlinkRate(int rate);

    /** The blink rate in milliseconds. */
    int getBlinkRate();

    /** The insertion point ("dot"). */
    int getDot();

    /** The other end of the selection ("mark"). */
    int getMark();

    /** Moves the caret to {@code dot}, dropping any selection. */
    void setDot(int dot);

    /** Extends the selection to {@code dot}, keeping the mark. */
    void moveDot(int dot);
}
