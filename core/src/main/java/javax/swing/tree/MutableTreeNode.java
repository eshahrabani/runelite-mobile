package javax.swing.tree;

/**
 * Swing {@code MutableTreeNode}: a {@link TreeNode} whose children and user object can be
 * changed.
 *
 * <p>Hand-written because the port's tree models are mutated by the shipped plugin
 * classes. The member set is the JDK's.
 */
public interface MutableTreeNode extends TreeNode {
    /** Inserts {@code child} at {@code index}. */
    void insert(MutableTreeNode child, int index);

    /** Removes the child at {@code index}. */
    void remove(int index);

    /** Removes {@code node} from this node's children. */
    void remove(MutableTreeNode node);

    /** Sets this node's user object. */
    void setUserObject(Object object);

    /** Removes this node from its parent's child list. */
    void removeFromParent();

    /** Sets this node's parent. */
    void setParent(MutableTreeNode newParent);
}
