package javax.swing.tree;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

/**
 * Swing {@code DefaultTreeSelectionModel}: a plain-list {@link TreeSelectionModel}.
 *
 * <p>Hand-written because the port's {@link javax.swing.JTree} needs a working selection.
 * Every change fires exactly one {@link TreeSelectionEvent}; row-based queries are answered
 * through the {@link RowMapper} the owning tree installs (without one they return -1/empty).
 * The JDK's vetoable-property and property-change plumbing is not implemented — nothing the
 * shipped panels use listens for it.
 */
public class DefaultTreeSelectionModel implements TreeSelectionModel, java.io.Serializable {
    private static final long serialVersionUID = -5603727335682911509L;

    private final List<TreeSelectionListener> listeners = new ArrayList<>();
    private final List<TreePath> selection = new ArrayList<>();
    private TreePath leadPath;
    private int selectionMode = DISCONTIGUOUS_TREE_SELECTION;
    private RowMapper rowMapper;

    // ------------------------------------------------------------- selection

    @Override
    public void setSelectionPath(TreePath path) {
        if (path == null) {
            clearSelection();
            return;
        }
        TreePath oldLead = leadPath;
        List<TreePath> old = new ArrayList<>(selection);
        selection.clear();
        selection.add(path);
        leadPath = path;
        fireChanged(new TreePath[] {path}, new boolean[] {true}, oldLead);
    }

    @Override
    public void addSelectionPath(TreePath path) {
        if (path == null || isPathSelected(path)) {
            return;
        }
        if (selectionMode == SINGLE_TREE_SELECTION) {
            setSelectionPath(path);
            return;
        }
        TreePath oldLead = leadPath;
        selection.add(path);
        leadPath = path;
        fireChanged(new TreePath[] {path}, new boolean[] {true}, oldLead);
    }

    @Override
    public void setSelectionPaths(TreePath[] paths) {
        if (paths == null || paths.length == 0) {
            clearSelection();
            return;
        }
        TreePath oldLead = leadPath;
        selection.clear();
        for (TreePath p : paths) {
            if (p != null) {
                selection.add(p);
            }
        }
        leadPath = selection.isEmpty() ? null : selection.get(selection.size() - 1);
        fireChanged(paths, new boolean[paths.length], oldLead);
    }

    @Override
    public void addSelectionPaths(TreePath[] paths) {
        if (paths == null) {
            return;
        }
        List<TreePath> added = new ArrayList<>();
        TreePath oldLead = leadPath;
        for (TreePath p : paths) {
            if (p != null && !isPathSelected(p)) {
                if (selectionMode == SINGLE_TREE_SELECTION) {
                    setSelectionPath(p);
                    return;
                }
                added.add(p);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        selection.addAll(added);
        leadPath = added.get(added.size() - 1);
        fireChanged(added.toArray(new TreePath[0]), new boolean[added.size()], oldLead);
    }

    @Override
    public void removeSelectionPath(TreePath path) {
        if (path == null || !selection.remove(path)) {
            return;
        }
        if (path.equals(leadPath)) {
            leadPath = selection.isEmpty() ? null : selection.get(selection.size() - 1);
        }
        fireChanged(new TreePath[] {path}, new boolean[] {false}, leadPath);
    }

    @Override
    public void removeSelectionPaths(TreePath[] paths) {
        if (paths == null) {
            return;
        }
        List<TreePath> removed = new ArrayList<>();
        for (TreePath p : paths) {
            if (p != null && selection.remove(p)) {
                removed.add(p);
            }
        }
        if (removed.isEmpty()) {
            return;
        }
        if (removed.contains(leadPath)) {
            leadPath = selection.isEmpty() ? null : selection.get(selection.size() - 1);
        }
        fireChanged(removed.toArray(new TreePath[0]), new boolean[removed.size()], leadPath);
    }

    @Override
    public void clearSelection() {
        if (selection.isEmpty()) {
            return;
        }
        TreePath oldLead = leadPath;
        TreePath[] old = selection.toArray(new TreePath[0]);
        selection.clear();
        leadPath = null;
        fireChanged(old, new boolean[old.length], oldLead);
    }

    @Override
    public TreePath getSelectionPath() {
        return selection.isEmpty() ? null : selection.get(0);
    }

    @Override
    public TreePath[] getSelectionPaths() {
        return selection.toArray(new TreePath[0]);
    }

    @Override
    public int getSelectionCount() {
        return selection.size();
    }

    @Override
    public boolean isPathSelected(TreePath path) {
        return path != null && selection.contains(path);
    }

    @Override
    public boolean isSelectionEmpty() {
        return selection.isEmpty();
    }

    @Override
    public TreePath getLeadSelectionPath() {
        return leadPath;
    }

    @Override
    public void setLeadSelectionPath(TreePath newLead) {
        leadPath = newLead;
    }

    // ------------------------------------------------------------- settings

    @Override
    public void setSelectionMode(int mode) {
        if (mode != SINGLE_TREE_SELECTION && mode != CONTIGUOUS_TREE_SELECTION
                && mode != DISCONTIGUOUS_TREE_SELECTION) {
            throw new IllegalArgumentException("invalid selectionMode");
        }
        selectionMode = mode;
    }

    @Override
    public int getSelectionMode() {
        return selectionMode;
    }

    @Override
    public void addTreeSelectionListener(TreeSelectionListener x) {
        if (x != null) {
            listeners.add(x);
        }
    }

    @Override
    public void removeTreeSelectionListener(TreeSelectionListener x) {
        listeners.remove(x);
    }

    @Override
    public void setRowMapper(RowMapper newMapper) {
        rowMapper = newMapper;
    }

    @Override
    public RowMapper getRowMapper() {
        return rowMapper;
    }

    // ------------------------------------------------------------------ rows

    @Override
    public int getMinSelectionRow() {
        int[] rows = getSelectionRows();
        return rows.length == 0 ? -1 : rows[0];
    }

    @Override
    public int getMaxSelectionRow() {
        int[] rows = getSelectionRows();
        return rows.length == 0 ? -1 : rows[rows.length - 1];
    }

    @Override
    public int getLeadSelectionRow() {
        if (leadPath == null || rowMapper == null) {
            return -1;
        }
        int[] rows = rowMapper.getRowsForPaths(new TreePath[] {leadPath});
        return rows == null || rows.length == 0 ? -1 : rows[0];
    }

    @Override
    public boolean isRowSelected(int row) {
        if (rowMapper == null) {
            return false;
        }
        for (TreePath path : selection) {
            int[] rows = rowMapper.getRowsForPaths(new TreePath[] {path});
            if (rows != null) {
                for (int r : rows) {
                    if (r == row) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int[] getSelectionRows() {
        if (rowMapper == null) {
            return new int[0];
        }
        List<Integer> rows = new ArrayList<>();
        for (TreePath path : selection) {
            int[] r = rowMapper.getRowsForPaths(new TreePath[] {path});
            if (r != null) {
                for (int value : r) {
                    if (value >= 0 && !rows.contains(value)) {
                        rows.add(value);
                    }
                }
            }
        }
        java.util.Collections.sort(rows);
        int[] out = new int[rows.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = rows.get(i);
        }
        return out;
    }

    // ----------------------------------------------------------------- events

    private void fireChanged(TreePath[] paths, boolean[] areNew, TreePath oldLead) {
        if (listeners.isEmpty()) {
            return;
        }
        TreeSelectionEvent event = new TreeSelectionEvent(this, paths, areNew, oldLead, leadPath);
        for (TreeSelectionListener l : new ArrayList<>(listeners)) {
            l.valueChanged(event);
        }
    }
}
