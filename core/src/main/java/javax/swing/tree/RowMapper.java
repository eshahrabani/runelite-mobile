package javax.swing.tree;

/**
 * Swing {@code RowMapper}: maps paths in a {@link TreeModel} to display rows.
 *
 * <p>Hand-written because {@link javax.swing.JTree} implements it (the JDK's tree does as
 * well) and the selection model may be given one. The member set is the JDK's.
 */
public interface RowMapper {
    /**
     * The display rows of {@code path}, in ascending order, or {@code null} when no path
     * maps to a row. A one-element array with {@code -1} is the JDK's "nothing here".
     */
    int[] getRowsForPaths(TreePath[] path);
}
