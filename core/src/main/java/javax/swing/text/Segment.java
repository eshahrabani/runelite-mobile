package javax.swing.text;

import java.text.CharacterIterator;

/**
 * Hand-written {@code javax.swing.text.Segment}: a window onto a character array, in the
 * {@link CharacterIterator} shape Swing uses to hand text around without copying.
 *
 * <p>Why hand-written: {@link Document#getText(int, int, Segment)} and the plain-document
 * implementation move text through it, and {@code javax.swing.text.Segment} is one of the
 * text classes the shipped client jar references. The generated stub returned null from
 * {@code clone()} and implemented no iteration at all.
 *
 * <p>Semantics are the JDK's, including the iterator position field: iteration runs between
 * {@code offset} and {@code offset + count}, and {@code next()}/{@code previous()} answer
 * {@link CharacterIterator#DONE} at the ends.
 */
public class Segment implements Cloneable, CharacterIterator, CharSequence {

    /** The backing array (a view; callers must not modify it). */
    public char[] array;

    /** The index in {@link #array} where the text of interest begins. */
    public int offset;

    /** The number of characters of interest. */
    public int count;

    private boolean partialReturn;
    private int pos;

    public Segment() {
        this(null, 0, 0);
    }

    public Segment(char[] array, int offset, int count) {
        this.array = array;
        this.offset = offset;
        this.count = count;
    }

    /** When true, iteration is treated as a partial return (JDK flag, stored as-is). */
    public void setPartialReturn(boolean p) {
        partialReturn = p;
    }

    /** True when this segment was marked as a partial return. */
    public boolean isPartialReturn() {
        return partialReturn;
    }

    // -------------------------------------------------- CharacterIterator

    @Override
    public char first() {
        pos = offset;
        if (count != 0) {
            return array[pos];
        }
        return DONE;
    }

    @Override
    public char last() {
        pos = offset + count;
        if (count != 0) {
            pos -= 1;
            return array[pos];
        }
        return DONE;
    }

    @Override
    public char current() {
        if (count != 0 && pos < offset + count) {
            return array[pos];
        }
        return DONE;
    }

    @Override
    public char next() {
        pos += 1;
        int end = offset + count;
        if (pos >= end) {
            pos = end;
            return DONE;
        }
        return current();
    }

    @Override
    public char previous() {
        if (pos == offset) {
            return DONE;
        }
        pos -= 1;
        return current();
    }

    @Override
    public char setIndex(int position) {
        int end = offset + count;
        if (position < offset || position > end) {
            throw new IllegalArgumentException("bad position: " + position);
        }
        pos = position;
        if (pos != end && count != 0) {
            return array[pos];
        }
        return DONE;
    }

    @Override
    public int getBeginIndex() {
        return offset;
    }

    @Override
    public int getEndIndex() {
        return offset + count;
    }

    @Override
    public int getIndex() {
        return pos;
    }

    // ------------------------------------------------------- CharSequence

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= count) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return array[offset + index];
    }

    @Override
    public int length() {
        return count;
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (end > count) {
            throw new StringIndexOutOfBoundsException(end);
        }
        if (start > end) {
            throw new StringIndexOutOfBoundsException(end - start);
        }
        Segment segment = new Segment();
        segment.array = array;
        segment.offset = offset + start;
        segment.count = end - start;
        return segment;
    }

    @Override
    public String toString() {
        return array == null ? "" : new String(array, offset, count);
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // Segment is Cloneable, so this cannot happen.
            return null;
        }
    }
}
