package javax.swing.tree;

import java.util.Enumeration;

/**
 * Swing {@code TreeNode}: one node of a tree model.
 *
 * <p>Hand-written because {@link javax.swing.JTree} renders real models on this port. The
 * member set is the JDK's.
 */
public interface TreeNode {
    /** This node's child at {@code index}. */
    TreeNode getChildAt(int childIndex);

    /** Number of children. */
    int getChildCount();

    /** This node's parent, or {@code null} for the root. */
    TreeNode getParent();

    /** Index of {@code node} among this node's children, or {@code -1}. */
    int getIndex(TreeNode node);

    /** Whether this node may have children. */
    boolean getAllowsChildren();

    /** Whether this node has no children. */
    boolean isLeaf();

    /** This node's children as an enumeration. */
    Enumeration<? extends TreeNode> children();

    /** The path from the root down to and including this node. */
    TreeNode[] getPath();
}
