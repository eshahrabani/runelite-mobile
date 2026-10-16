package javax.swing;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Swing {@code DefaultListSelectionModel}: a {@link BitSet}-backed
 * {@link ListSelectionModel}.
 *
 * <p>Hand-written because the port has no java.desktop selection machinery. The model
 * stores the anchor and lead indices the way the JDK does, and every mutator delivers
 * exactly one {@link ListSelectionEvent} covering the index range that changed (no event
 * at all when the selection is already in the requested state — the JDK is laxer here,
 * and the stricter rule keeps a redraw from following a no-op).
 */
public class DefaultListSelectionModel implements ListSelectionModel, java.io.Serializable {
    private static final long serialVersionUID = -3301605591108950415L;

    private final BitSet selected = new BitSet();
    private final List<ListSelectionListener> listeners = new ArrayList<>();
    private int selectionMode = MULTIPLE_INTERVAL_SELECTION;
    private int anchorIndex = -1;
    private int leadIndex = -1;
    private boolean valueIsAdjusting;

    // ------------------------------------------------------------- selection

    @Override
    public void setSelectionInterval(int index0, int index1) {
        if (index0 == -1 || index1 == -1) {
            clearSelection();
            return;
        }
        if (index0 > index1) {
            int t = index0;
            index0 = index1;
            index1 = t;
        }
        index0 = Math.max(0, index0);
        anchorIndex = index0;
        leadIndex = index1;

        int oldMin = getMinSelectionIndex();
        int oldMax = getMaxSelectionIndex();
        BitSet next = new BitSet();
        next.set(index0, index1 + 1);
        if (next.equals(selected)) {
            return;
        }
        selected.clear();
        selected.or(next);
        fireValueChanged(unionMin(oldMin, index0), unionMax(oldMax, index1));
    }

    @Override
    public void addSelectionInterval(int index0, int index1) {
        if (index0 == -1 || index1 == -1) {
            return;
        }
        if (selectionMode == SINGLE_SELECTION) {
            setSelectionInterval(index0, index1);
            return;
        }
        if (index0 > index1) {
            int t = index0;
            index0 = index1;
            index1 = t;
        }
        index0 = Math.max(0, index0);
        leadIndex = index1;
        if (selected.get(index0, index1 + 1).cardinality() == index1 - index0 + 1) {
            return;
        }
        selected.set(index0, index1 + 1);
        fireValueChanged(index0, index1);
    }

    @Override
    public void removeSelectionInterval(int index0, int index1) {
        if (index0 == -1 || index1 == -1) {
            return;
        }
        if (index0 > index1) {
            int t = index0;
            index0 = index1;
            index1 = t;
        }
        index0 = Math.max(0, index0);
        if (selected.get(index0, index1 + 1).isEmpty()) {
            return;
        }
        selected.clear(index0, index1 + 1);
        fireValueChanged(index0, index1);
    }

    @Override
    public void clearSelection() {
        if (selected.isEmpty()) {
            return;
        }
        int oldMin = getMinSelectionIndex();
        int oldMax = getMaxSelectionIndex();
        selected.clear();
        anchorIndex = -1;
        leadIndex = -1;
        fireValueChanged(oldMin, oldMax);
    }

    @Override
    public int getMinSelectionIndex() {
        return selected.isEmpty() ? -1 : selected.nextSetBit(0);
    }

    @Override
    public int getMaxSelectionIndex() {
        return selected.isEmpty() ? -1 : selected.length() - 1;
    }

    @Override
    public boolean isSelectedIndex(int index) {
        return index >= 0 && selected.get(index);
    }

    @Override
    public boolean isSelectionEmpty() {
        return selected.isEmpty();
    }

    @Override
    public int getAnchorSelectionIndex() {
        return anchorIndex;
    }

    @Override
    public void setAnchorSelectionIndex(int index) {
        anchorIndex = index;
    }

    @Override
    public int getLeadSelectionIndex() {
        return leadIndex;
    }

    @Override
    public void setLeadSelectionIndex(int index) {
        leadIndex = index;
    }

    @Override
    public int[] getSelectedIndices() {
        int[] out = new int[selected.cardinality()];
        int n = 0;
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
            out[n++] = i;
        }
        return out;
    }

    // ------------------------------------------------------------- structure

    @Override
    public void insertIndexInterval(int index, int length, boolean before) {
        if (length <= 0) {
            return;
        }
        int at = before ? index : index + 1;
        BitSet next = new BitSet();
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
            next.set(i >= at ? i + length : i);
        }
        selected.clear();
        selected.or(next);
        if (anchorIndex >= at) {
            anchorIndex += length;
        }
        if (leadIndex >= at) {
            leadIndex += length;
        }
    }

    @Override
    public void removeIndexInterval(int index0, int index1) {
        if (index1 < index0) {
            int t = index0;
            index0 = index1;
            index1 = t;
        }
        int length = index1 - index0 + 1;
        BitSet next = new BitSet();
        for (int i = selected.nextSetBit(0); i >= 0; i = selected.nextSetBit(i + 1)) {
            if (i < index0) {
                next.set(i);
            } else if (i > index1) {
                next.set(i - length);
            }
        }
        selected.clear();
        selected.or(next);
        if (anchorIndex > index1) {
            anchorIndex -= length;
        }
        if (leadIndex > index1) {
            leadIndex -= length;
        }
    }

    // -------------------------------------------------------------- settings

    @Override
    public void setValueIsAdjusting(boolean valueIsAdjusting) {
        this.valueIsAdjusting = valueIsAdjusting;
    }

    @Override
    public boolean getValueIsAdjusting() {
        return valueIsAdjusting;
    }

    @Override
    public void setSelectionMode(int selectionMode) {
        if (selectionMode != SINGLE_SELECTION
                && selectionMode != SINGLE_INTERVAL_SELECTION
                && selectionMode != MULTIPLE_INTERVAL_SELECTION) {
            throw new IllegalArgumentException("invalid selectionMode");
        }
        if (this.selectionMode == selectionMode) {
            return;
        }
        this.selectionMode = selectionMode;
        if (selectionMode == SINGLE_SELECTION) {
            int min = getMinSelectionIndex();
            int max = getMaxSelectionIndex();
            if (min != -1 && max != min) {
                setSelectionInterval(anchorIndex < 0 ? min : anchorIndex, anchorIndex < 0 ? min : anchorIndex);
            }
        }
    }

    @Override
    public int getSelectionMode() {
        return selectionMode;
    }

    @Override
    public void addListSelectionListener(ListSelectionListener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    @Override
    public void removeListSelectionListener(ListSelectionListener l) {
        listeners.remove(l);
    }

    // ----------------------------------------------------------------- events

    /** Delivers one event covering the inclusive range {@code first..last}. */
    private void fireValueChanged(int first, int last) {
        if (listeners.isEmpty() || first < 0) {
            return;
        }
        ListSelectionEvent event = new ListSelectionEvent(this, first, last, valueIsAdjusting);
        for (ListSelectionListener l : new ArrayList<>(listeners)) {
            l.valueChanged(event);
        }
    }

    private static int unionMin(int a, int b) {
        if (a < 0) {
            return b;
        }
        return Math.min(a, b);
    }

    private static int unionMax(int a, int b) {
        return Math.max(a, b);
    }
}
