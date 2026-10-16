package javax.swing;

import java.util.Vector;

/**
 * Swing {@code DefaultComboBoxModel}: a mutable {@link ComboBoxModel} over a
 * {@code Vector}.
 *
 * <p>Hand-written because {@link JComboBox} needs a working default model. Setting the
 * selected item fires one {@code CONTENTS_CHANGED} with a {@code -1..-1} range (the JDK's
 * "the selection, not an element, changed" convention); the default selection after
 * construction is the first element.
 *
 * <p>Deviation: the JDK's {@code MutableComboBoxModel} interface is not present in this
 * tree (nothing the shipped jars link against references it), so the mutable methods are
 * declared here directly.
 */
public class DefaultComboBoxModel<E> extends AbstractListModel<E> implements ComboBoxModel<E> {
    private static final long serialVersionUID = 669865770367692808L;

    protected Vector<E> objects = new Vector<>();
    private Object selectedObject;

    public DefaultComboBoxModel() {
    }

    public DefaultComboBoxModel(E[] items) {
        objects = new Vector<>(items.length);
        for (E item : items) {
            objects.addElement(item);
        }
        if (!objects.isEmpty()) {
            selectedObject = objects.elementAt(0);
        }
    }

    public DefaultComboBoxModel(Vector<E> v) {
        objects = v;
        if (!objects.isEmpty()) {
            selectedObject = objects.elementAt(0);
        }
    }

    // ------------------------------------------------------------- ListModel

    @Override
    public int getSize() {
        return objects.size();
    }

    @Override
    public E getElementAt(int index) {
        if (index < 0 || index >= objects.size()) {
            return null;
        }
        return objects.elementAt(index);
    }

    // ---------------------------------------------------------- ComboBoxModel

    @Override
    public void setSelectedItem(Object anObject) {
        if (selectedObject != null ? !selectedObject.equals(anObject) : anObject != null) {
            selectedObject = anObject;
            fireContentsChanged(this, -1, -1);
        }
    }

    @Override
    public Object getSelectedItem() {
        return selectedObject;
    }

    // ------------------------------------------------------------- mutation

    /** Index of {@code anObject} in the model, or {@code -1}. */
    public int getIndexOf(Object anObject) {
        return objects.indexOf(anObject);
    }

    public void addElement(E anObject) {
        objects.addElement(anObject);
        fireIntervalAdded(this, objects.size() - 1, objects.size() - 1);
        if (objects.size() == 1 && selectedObject == null && anObject != null) {
            setSelectedItem(anObject);
        }
    }

    public void insertElementAt(E anObject, int index) {
        objects.insertElementAt(anObject, index);
        fireIntervalAdded(this, index, index);
    }

    public void removeElementAt(int index) {
        if (getElementAt(index) == selectedObject) {
            if (index == 0) {
                setSelectedItem(getSize() == 1 ? null : getElementAt(index + 1));
            } else {
                setSelectedItem(getElementAt(index - 1));
            }
        }
        objects.removeElementAt(index);
        fireIntervalRemoved(this, index, index);
    }

    public void removeElement(Object anObject) {
        int index = objects.indexOf(anObject);
        if (index != -1) {
            removeElementAt(index);
        }
    }

    public void removeAllElements() {
        if (objects.isEmpty()) {
            return;
        }
        int size = objects.size();
        objects.removeAllElements();
        selectedObject = null;
        fireIntervalRemoved(this, 0, size - 1);
    }
}
