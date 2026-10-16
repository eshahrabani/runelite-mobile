package javax.swing.tree;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Swing {@code DefaultMutableTreeNode}: a general-purpose tree node (a user object plus
 * child list) for {@link DefaultTreeModel}.
 *
 * <p>Hand-written because the port's {@link javax.swing.JTree} renders real models and the
 * shipped plugin classes build {@code DefaultMutableTreeNode} trees. The child list is a
 * {@code Vector} like the JDK's; the deprecated enumeration-based constructors are omitted
 * (nothing the shipped jars reference them).
 */
public class DefaultMutableTreeNode implements MutableTreeNode, java.io.Serializable {
    private static final long serialVersionUID = -4298474751201349152L;

    /** Sentinel: this node may have children. */
    public static final Enumeration<TreeNode> EMPTY_ENUMERATION =
            Collections.enumeration(Collections.<TreeNode>emptyList());

    protected MutableTreeNode parent;
    protected Vector<MutableTreeNode> children;
    protected transient Object userObject;
    protected boolean allowsChildren;

    public DefaultMutableTreeNode() {
        this(null);
    }

    public DefaultMutableTreeNode(Object userObject) {
        this(userObject, true);
    }

    public DefaultMutableTreeNode(Object userObject, boolean allowsChildren) {
        this.userObject = userObject;
        this.allowsChildren = allowsChildren;
    }

    // ------------------------------------------------------------ children

    @Override
    public void insert(MutableTreeNode newChild, int childIndex) {
        if (newChild == null) {
            throw new IllegalArgumentException("new child is null");
        }
        if (children == null) {
            children = new Vector<>();
        }
        children.insertElementAt(newChild, childIndex);
        newChild.setParent(this);
    }

    /** Appends {@code newChild} (or moves it within this node). Not part of the interface. */
    public void add(MutableTreeNode newChild) {
        if (newChild != null && newChild.getParent() == this) {
            insert(newChild, getChildCount() - 1);
        } else {
            insert(newChild, getChildCount());
        }
    }

    @Override
    public void remove(int childIndex) {
        MutableTreeNode child = (MutableTreeNode) getChildAt(childIndex);
        children.removeElementAt(childIndex);
        child.setParent(null);
    }

    @Override
    public void remove(MutableTreeNode aChild) {
        if (aChild == null) {
            throw new IllegalArgumentException("argument is null");
        }
        if (!isNodeChild(aChild)) {
            throw new IllegalArgumentException("argument is not a child");
        }
        remove(getIndex(aChild));
    }

    @Override
    public void removeFromParent() {
        if (parent != null) {
            parent.remove(this);
        }
    }

    /** Removes every child. Not part of the interface. */
    public void removeAllChildren() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            remove(i);
        }
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        parent = newParent;
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public TreeNode getChildAt(int index) {
        if (children == null) {
            throw new ArrayIndexOutOfBoundsException("node has no children");
        }
        return children.elementAt(index);
    }

    @Override
    public int getChildCount() {
        return children == null ? 0 : children.size();
    }

    /** Index of {@code aChild} among this node's children, or {@code -1}. */
    @Override
    public int getIndex(TreeNode aChild) {
        if (aChild == null) {
            throw new IllegalArgumentException("argument is null");
        }
        if (!isNodeChild(aChild)) {
            return -1;
        }
        return children.indexOf(aChild);
    }

    /** The child immediately before {@code aChild}, or {@code null}. */
    public TreeNode getChildBefore(TreeNode aChild) {
        int index = getIndex(aChild);
        return index <= 0 ? null : getChildAt(index - 1);
    }

    /** The child immediately after {@code aChild}, or {@code null}. */
    public TreeNode getChildAfter(TreeNode aChild) {
        int index = getIndex(aChild);
        return index == -1 || index >= getChildCount() - 1 ? null : getChildAt(index + 1);
    }

    /** The first child, or {@code null}. */
    public TreeNode getFirstChild() {
        return getChildCount() == 0 ? null : getChildAt(0);
    }

    /** The last child, or {@code null}. */
    public TreeNode getLastChild() {
        return getChildCount() == 0 ? null : getChildAt(getChildCount() - 1);
    }

    /** True when {@code aNode} is one of this node's children. */
    public boolean isNodeChild(TreeNode aNode) {
        if (aNode == null) {
            return false;
        }
        return getChildCount() != 0 && aNode.getParent() == this;
    }

    @Override
    public Enumeration<TreeNode> children() {
        if (children == null) {
            return EMPTY_ENUMERATION;
        }
        return Collections.enumeration(new java.util.ArrayList<TreeNode>(children));
    }

    @Override
    public boolean getAllowsChildren() {
        return allowsChildren;
    }

    /** Sets whether this node may carry children. */
    public void setAllowsChildren(boolean allows) {
        allowsChildren = allows;
    }

    @Override
    public boolean isLeaf() {
        return getChildCount() == 0;
    }

    /** This node's user object (not part of {@link TreeNode}). */
    public Object getUserObject() {
        return userObject;
    }

    @Override
    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }

    /** True when this node is the root (has no parent). */
    public boolean isRoot() {
        return getParent() == null;
    }

    /** True when this node's parent is {@code aNode}. */
    public boolean isNodeAncestor(TreeNode anotherNode) {
        if (anotherNode == null) {
            return false;
        }
        TreeNode ancestor = this;
        do {
            if (ancestor == anotherNode) {
                return true;
            }
            ancestor = ancestor.getParent();
        } while (ancestor != null);
        return false;
    }

    @Override
    public TreeNode[] getPath() {
        return getPathToRoot(this, 0);
    }

    /** Builds the root-first path ending at {@code aNode}. */
    protected TreeNode[] getPathToRoot(TreeNode aNode, int depth) {
        TreeNode[] ret;
        if (aNode == null) {
            ret = depth == 0 ? null : new TreeNode[depth];
        } else {
            depth++;
            ret = getPathToRoot(aNode.getParent(), depth);
            ret[ret.length - depth] = aNode;
        }
        return ret;
    }

    @Override
    public String toString() {
        return userObject == null ? "" : userObject.toString();
    }
}
