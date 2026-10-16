package java.awt;

import java.awt.event.AdjustmentListener;

/**
 * AWT {@code Adjustable}: the "numeric value inside a bounded range" contract that
 * {@link javax.swing.JScrollBar} and the game client's own scrolling widgets are typed
 * against.
 *
 * <p>Hand-written rather than generated: the shipped RuneLite jars are compiled against
 * {@code java.desktop}, so every member they resolve through this type has to exist here
 * for real. The generated file was an empty interface (the generator found no member
 * reference), which would have left {@code javax.swing.JScrollBar} without the
 * {@code Adjustable} surface its callers use; the marker sentence is removed so the
 * generator leaves this file alone.
 *
 * <p>Port deviation: the JDK marks the orientation constants with
 * {@code java.lang.annotation.Native} for JNI. There is no native AWT layer here, so the
 * annotation is dropped and the constants are plain {@code public static final int}s.
 */
public interface Adjustable {

    /** Indicates that the adjustable object is horizontal. */
    public static final int HORIZONTAL = 0;

    /** Indicates that the adjustable object is vertical. */
    public static final int VERTICAL = 1;

    /** Indicates that the adjustable object has no orientation. */
    public static final int NO_ORIENTATION = 2;

    /** Vertical or horizontal orientation of the adjustable. */
    int getOrientation();

    /** Sets the minimum value. Hidden values below it are clamped by implementors. */
    void setMinimum(int min);

    int getMinimum();

    void setMaximum(int max);

    int getMaximum();

    /** Sets the amount a unit (line/arrow) scroll moves the value by. */
    void setUnitIncrement(int u);

    int getUnitIncrement();

    /** Sets the amount a block (page) scroll moves the value by. */
    void setBlockIncrement(int b);

    int getBlockIncrement();

    /** Sets the length of the proportional indicator (the scroll bar's thumb). */
    void setVisibleAmount(int v);

    int getVisibleAmount();

    void setValue(int v);

    int getValue();

    void addAdjustmentListener(AdjustmentListener l);

    void removeAdjustmentListener(AdjustmentListener l);
}
