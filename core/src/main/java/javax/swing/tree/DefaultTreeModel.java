package javax.swing.tree;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

/**
 * Swing {@code DefaultTreeModel}: a {@link TreeModel} over {@link TreeNode}s with the
 * JDK's mutation helpers and event firing.
 *
 * <p>Hand-written because the port's {@link javax.swing.JTree} renders real models and the
 * shipped plugin classes build and mutate {@code DefaultTreeModel} trees. The listener list
 * is a plain list (the JDK's {@code EventListenerList} is not an Android class).
 */
public class DefaultTreeModel implements TreeModel, java.io.Serializable {
    private static final long serialVersionUID = 1165394852090683887L;

    protected TreeNode root;
    protected boolean asksAllowsChildren;

    private final List<TreeModelListener> listenerList = new ArrayList<>();

    public DefaultTreeModel(TreeNode root) {
        this(root, false);
    }

    public DefaultTreeModel(TreeNode root, boolean asksAllowsChildren) {
        this.root = root;
        this.asksAllowsChildren = asksAllowsChildren;
    }

    // ------------------------------------------------------------- TreeModel

    @Override
    public Object getRoot() {
        return root;
    }

    /** Replaces the root and fires a structure change. */
    public void setRoot(TreeNode root) {
        Object oldRoot = this.root;
        this.root = root;
        if (root == null && oldRoot != null) {
            fireTreeStructureChanged(this, null);
        } else if (root == null || root != oldRoot) {
            nodeStructureChanged(root);
        }
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent instanceof TreeNode) {
            return ((TreeNode) parent).getChildCount();
        }
        return 0;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return parent instanceof TreeNode ? ((TreeNode) parent).getChildAt(index) : null;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent instanceof TreeNode && child instanceof TreeNode) {
            return ((TreeNode) parent).getIndex((TreeNode) child);
        }
        return -1;
    }

    @Override
    public boolean isLeaf(Object node) {
        if (asksAllowsChildren && node instanceof TreeNode) {
            return !((TreeNode) node).getAllowsChildren();
        }
        return getChildCount(node) == 0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        Object node = path.getLastPathComponent();
        if (node instanceof MutableTreeNode) {
            ((MutableTreeNode) node).setUserObject(newValue);
        }
        if (node instanceof TreeNode) {
            nodeChanged((TreeNode) node);
        }
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        if (l != null) {
            listenerList.add(l);
        }
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listenerList.remove(l);
    }

    /** The currently registered listeners. */
    public TreeModelListener[] getTreeModelListeners() {
        return listenerList.toArray(new TreeModelListener[0]);
    }

    // ------------------------------------------------------------ structure

    /** Whether leafness is decided by {@code getAllowsChildren} rather than child count. */
    public boolean asksAllowsChildren() {
        return asksAllowsChildren;
    }

    /** The root-first path ending at {@code aNode}. */
    public TreeNode[] getPathToRoot(TreeNode aNode) {
        return getPathToRoot(aNode, 0);
    }

    /** Recursive worker for {@link #getPathToRoot(TreeNode)}. */
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

    /** Reloads the whole tree, firing a structure change. */
    public void reload() {
        reload(root);
    }

    /** Reloads the subtree at {@code node}. */
    public void reload(TreeNode node) {
        if (node != null) {
            fireTreeStructureChanged(this, getPathToRoot(node), null, null);
        }
    }

    /** Inserts {@code newChild} under {@code parent} at {@code index} and fires one event. */
    public void insertNodeInto(MutableTreeNode newChild, MutableTreeNode parent, int index) {
        parent.insert(newChild, index);
        int[] childIndices = {index};
        Object[] children = {newChild};
        fireTreeNodesInserted(this, getPathToRoot(parent), childIndices, children);
    }

    /** Removes {@code node} from its parent and fires one event. */
    public void removeNodeFromParent(MutableTreeNode node) {
        MutableTreeNode parent = (MutableTreeNode) node.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("node does not have a parent");
        }
        int index = parent.getIndex(node);
        node.removeFromParent();
        int[] childIndices = {index};
        Object[] children = {node};
        fireTreeNodesRemoved(this, getPathToRoot(parent), childIndices, children);
    }

    /** Announces that {@code node}'s user object changed. */
    public void nodeChanged(TreeNode node) {
        if (node == null) {
            return;
        }
        TreeNode parent = node.getParent();
        if (parent != null) {
            int index = parent.getIndex(node);
            if (index != -1) {
                int[] childIndices = {index};
                Object[] children = {node};
                fireTreeNodesChanged(this, getPathToRoot(parent), childIndices, children);
                return;
            }
        } else if (node == root) {
            fireTreeNodesChanged(this, getPathToRoot(node), null, null);
        }
    }

    /** Announces that the user objects of {@code changedNodes} changed. */
    public void nodesChanged(TreeNode node, int[] childIndices, Object[] children) {
        if (node != null && childIndices != null && children != null && childIndices.length > 0) {
            fireTreeNodesChanged(this, getPathToRoot(node), childIndices, children);
        }
    }

    /** Announces that {@code node}'s children (or the whole subtree) changed. */
    public void nodeStructureChanged(TreeNode node) {
        if (node != null) {
            fireTreeStructureChanged(this, getPathToRoot(node), null, null);
        }
    }

    /** Announces that {@code node}'s subtree, or the whole tree when {@code node} is null, changed. */
    public void nodeWasCollapsed(TreeNode node) {
        // The port does not distinguish "collapsed" from a plain structure change.
        if (node != null) {
            fireTreeStructureChanged(this, getPathToRoot(node), null, null);
        }
    }

    // ----------------------------------------------------------------- events

    protected void fireTreeNodesChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
        TreeModelListener[] listeners = listenerList.toArray(new TreeModelListener[0]);
        if (listeners.length == 0) {
            return;
        }
        TreeModelEvent event = new TreeModelEvent(source, path, childIndices, children);
        for (TreeModelListener l : listeners) {
            l.treeNodesChanged(event);
        }
    }

    protected void fireTreeNodesInserted(Object source, Object[] path, int[] childIndices, Object[] children) {
        TreeModelListener[] listeners = listenerList.toArray(new TreeModelListener[0]);
        if (listeners.length == 0) {
            return;
        }
        TreeModelEvent event = new TreeModelEvent(source, path, childIndices, children);
        for (TreeModelListener l : listeners) {
            l.treeNodesInserted(event);
        }
    }

    protected void fireTreeNodesRemoved(Object source, Object[] path, int[] childIndices, Object[] children) {
        TreeModelListener[] listeners = listenerList.toArray(new TreeModelListener[0]);
        if (listeners.length == 0) {
            return;
        }
        TreeModelEvent event = new TreeModelEvent(source, path, childIndices, children);
        for (TreeModelListener l : listeners) {
            l.treeNodesRemoved(event);
        }
    }

    protected void fireTreeStructureChanged(Object source, Object[] path) {
        fireTreeStructureChanged(source, path, null, null);
    }

    protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
        TreeModelListener[] listeners = listenerList.toArray(new TreeModelListener[0]);
        if (listeners.length == 0) {
            return;
        }
        TreeModelEvent event = new TreeModelEvent(source, path, childIndices, children);
        for (TreeModelListener l : listeners) {
            l.treeStructureChanged(event);
        }
    }
}
