package javax.swing.event;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/** Swing {@code TreeModelEvent}: a tree model changed. */
public class TreeModelEvent extends java.util.EventObject {
    private static final long serialVersionUID = -3108806576992106004L;

    protected TreePath path;
    protected int[] childIndices;
    protected Object[] children;

    public TreeModelEvent(Object source, Object[] path, int[] childIndices, Object[] children) {
        this(source, path == null ? null : new TreePath(path), childIndices, children);
    }

    public TreeModelEvent(Object source, TreePath path, int[] childIndices, Object[] children) {
        super(source == null ? TreeModelEvent.class : source);
        this.path = path;
        this.childIndices = childIndices;
        this.children = children;
    }

    public TreeModelEvent(Object source, Object[] path) {
        this(source, path == null ? null : new TreePath(path), null, null);
    }

    public TreeModelEvent(Object source, TreePath path) {
        this(source, path, null, null);
    }

    public TreePath getTreePath() {
        return path;
    }

    public Object[] getPath() {
        return path == null ? null : path.getPath();
    }

    public Object[] getChildren() {
        return children;
    }

    public int[] getChildIndices() {
        return childIndices;
    }

    public void setPath(TreePath path) {
        this.path = path;
    }

    public void setChildIndices(int[] childIndices) {
        this.childIndices = childIndices;
    }

    public void setChildren(Object[] children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[path=" + path + ",childIndices="
                + (childIndices == null ? "null" : java.util.Arrays.toString(childIndices)) + "]";
    }
}
