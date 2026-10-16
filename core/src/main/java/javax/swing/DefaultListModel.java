package javax.swing;

import java.util.Enumeration;
import java.util.Vector;
import javax.swing.event.ListDataListener;

/**
 * Swing {@code DefaultListModel}: a growable {@link ListModel} backed by a
 * {@link Vector}.
 *
 * <p>Hand-written because the port's sidebar list widgets need a working model; every
 * mutator that changes the element count or an element's value fires the matching
 * {@code ListDataEvent} through {@link AbstractListModel}. The JDK's {@code delegate}
 * field is kept protected for binary/source compatibility with the shipped RuneLite
 * classes.
 */
public class DefaultListModel<E> extends AbstractListModel<E> {
    private static final long serialVersionUID = 2319121179987332515L;

    /** The element store; protected exactly like the JDK's. */
    protected Vector<E> delegate = new Vector<>();

    public DefaultListModel() {
    }

    // ------------------------------------------------------------- ListModel

    @Override
    public int getSize() {
        return delegate.size();
    }

    @Override
    public E getElementAt(int index) {
        return delegate.elementAt(index);
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        super.addListDataListener(l);
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        super.removeListDataListener(l);
    }

    /** The element at {@code index} (JDK alias for {@link #getElementAt(int)}). */
    public E get(int index) {
        return delegate.elementAt(index);
    }

    /** Sets the element at {@code index}, firing one {@code CONTENTS_CHANGED}. */
    public E set(int index, E element) {
        E old = delegate.set(index, element);
        fireContentsChanged(this, index, index);
        return old;
    }

    // ------------------------------------------------------------ structure

    /** Appends an element, firing {@code INTERVAL_ADDED}. */
    public void addElement(E element) {
        int index = delegate.size();
        delegate.addElement(element);
        fireIntervalAdded(this, index, index);
    }

    /** Inserts an element at {@code index}, firing {@code INTERVAL_ADDED}. */
    public void insertElementAt(E element, int index) {
        delegate.insertElementAt(element, index);
        fireIntervalAdded(this, index, index);
    }

    /** Replaces the element at {@code index}, firing {@code CONTENTS_CHANGED}. */
    public void setElementAt(E element, int index) {
        delegate.setElementAt(element, index);
        fireContentsChanged(this, index, index);
    }

    /** Removes the element at {@code index}, firing {@code INTERVAL_REMOVED}. */
    public void removeElementAt(int index) {
        delegate.removeElementAt(index);
        fireIntervalRemoved(this, index, index);
    }

    /** Removes the first occurrence of {@code obj}, firing {@code INTERVAL_REMOVED}. */
    public void removeElement(Object obj) {
        int index = delegate.indexOf(obj);
        if (index >= 0) {
            removeElementAt(index);
        }
    }

    /** Removes the inclusive range, firing one {@code INTERVAL_REMOVED}. */
    public void removeRange(int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex " + fromIndex + " > toIndex " + toIndex);
        }
        if (fromIndex < 0 || toIndex >= delegate.size()) {
            throw new ArrayIndexOutOfBoundsException("fromIndex " + fromIndex + ", toIndex " + toIndex);
        }
        for (int i = toIndex; i >= fromIndex; i--) {
            delegate.removeElementAt(i);
        }
        fireIntervalRemoved(this, fromIndex, toIndex);
    }

    /** Empties the model, firing one {@code INTERVAL_REMOVED} for the old range. */
    public void clear() {
        int oldSize = delegate.size();
        if (oldSize == 0) {
            return;
        }
        delegate.clear();
        fireIntervalRemoved(this, 0, oldSize - 1);
    }

    /** Removes every element (JDK alias for {@link #clear()}). */
    public void removeAllElements() {
        clear();
    }

    // ------------------------------------------------------------- queries

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public boolean contains(Object elem) {
        return delegate.contains(elem);
    }

    public int indexOf(Object elem) {
        return delegate.indexOf(elem);
    }

    public int indexOf(Object elem, int index) {
        return delegate.indexOf(elem, index);
    }

    public int lastIndexOf(Object elem) {
        return delegate.lastIndexOf(elem);
    }

    public int lastIndexOf(Object elem, int index) {
        return delegate.lastIndexOf(elem, index);
    }

    public int size() {
        return delegate.size();
    }

    public int capacity() {
        return delegate.capacity();
    }

    public void ensureCapacity(int minCapacity) {
        delegate.ensureCapacity(minCapacity);
    }

    public E elementAt(int index) {
        return delegate.elementAt(index);
    }

    public E firstElement() {
        return delegate.firstElement();
    }

    public E lastElement() {
        return delegate.lastElement();
    }

    public Enumeration<E> elements() {
        return delegate.elements();
    }

    public Object[] toArray() {
        return delegate.toArray();
    }

    public void copyInto(Object[] anArray) {
        delegate.copyInto(anArray);
    }

    /** Removes the element at {@code index} (List-style alias). */
    public E remove(int index) {
        E old = delegate.elementAt(index);
        removeElementAt(index);
        return old;
    }
}
