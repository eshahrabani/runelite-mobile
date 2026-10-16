package javax.swing.tree;

import javax.swing.event.TreeSelectionListener;

/**
 * Swing {@code TreeSelectionModel}: the selection state of a {@link javax.swing.JTree}.
 *
 * <p>Hand-written because the port's tree widget really selects rows. Paths are the units
 * of selection; {@link DefaultTreeSelectionModel} is the implementation, and row-based
 * queries go through the owning tree's {@link RowMapper}.
 */
public interface TreeSelectionModel {
    /** Only one path may be selected at a time. */
    int SINGLE_TREE_SELECTION = 1;
    /** One contiguous run of rows may be selected. */
    int CONTIGUOUS_TREE_SELECTION = 2;
    /** Any set of rows may be selected. */
    int DISCONTIGUOUS_TREE_SELECTION = 4;

    void setSelectionMode(int mode);

    int getSelectionMode();

    void setSelectionPath(TreePath path);

    void addSelectionPath(TreePath path);

    void setSelectionPaths(TreePath[] paths);

    void addSelectionPaths(TreePath[] paths);

    void removeSelectionPath(TreePath path);

    void removeSelectionPaths(TreePath[] paths);

    TreePath getSelectionPath();

    TreePath[] getSelectionPaths();

    int getSelectionCount();

    boolean isPathSelected(TreePath path);

    boolean isSelectionEmpty();

    void clearSelection();

    void addTreeSelectionListener(TreeSelectionListener x);

    void removeTreeSelectionListener(TreeSelectionListener x);

    TreePath getLeadSelectionPath();

    void setLeadSelectionPath(TreePath newLead);

    int getMinSelectionRow();

    int getMaxSelectionRow();

    int getLeadSelectionRow();

    boolean isRowSelected(int row);

    /** The selected rows in ascending order. */
    int[] getSelectionRows();

    void setRowMapper(RowMapper newMapper);

    RowMapper getRowMapper();
}
