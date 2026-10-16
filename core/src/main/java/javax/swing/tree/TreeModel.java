package javax.swing.tree;

import javax.swing.event.TreeModelListener;

/**
 * Swing {@code TreeModel}: the data behind a {@link javax.swing.JTree}.
 *
 * <p>Hand-written because the port's tree widgets render real models. A node is identified
 * by {@code Object} (usually a {@link TreeNode}) and a location is a {@link TreePath}; the
 * member set is the JDK's.
 */
public interface TreeModel {
    /** The root of the tree, or {@code null} for an empty model. */
    Object getRoot();

    /** The child of {@code parent} at {@code index}. */
    Object getChild(Object parent, int index);

    /** Number of children of {@code parent}. */
    int getChildCount(Object parent);

    /** Whether {@code node} is a leaf. */
    boolean isLeaf(Object node);

    /** Called when the user (or a cell editor) has changed a value at {@code path}. */
    void valueForPathChanged(TreePath path, Object newValue);

    /** Index of {@code child} within {@code parent}, or {@code -1}. */
    int getIndexOfChild(Object parent, Object child);

    void addTreeModelListener(TreeModelListener l);

    void removeTreeModelListener(TreeModelListener l);
}
