package javax.swing.tree;

/**
 * Swing {@code TreePath}: an immutable path of nodes from a tree's root to one node.
 *
 * <p>Hand-written because the tree model and selection machinery compare paths for
 * equality (structural, not identity), which the generated stub could not do.
 */
public class TreePath implements java.io.Serializable {
    private static final long serialVersionUID = 4384038560295087996L;

    private final Object[] path;
    private transient int hash;

    public TreePath(Object singlePath) {
        if (singlePath == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (singlePath instanceof Object[]) {
            this.path = ((Object[]) singlePath).clone();
        } else {
            this.path = new Object[] {singlePath};
        }
    }

    public TreePath(Object[] path) {
        if (path == null || path.length == 0) {
            throw new IllegalArgumentException("path must not be empty");
        }
        this.path = path.clone();
    }

    protected TreePath(TreePath parent, Object lastElement) {
        Object[] parentPath = parent == null ? new Object[0] : parent.path;
        this.path = new Object[parentPath.length + 1];
        System.arraycopy(parentPath, 0, this.path, 0, parentPath.length);
        this.path[parentPath.length] = lastElement;
    }

    public Object[] getPath() {
        return path.clone();
    }

    public Object getLastPathComponent() {
        return path[path.length - 1];
    }

    public int getPathCount() {
        return path.length;
    }

    public Object getPathComponent(int index) {
        if (index < 0 || index >= path.length) {
            throw new IllegalArgumentException("index out of bounds: " + index);
        }
        return path[index];
    }

    public TreePath getParentPath() {
        return path.length <= 1 ? null : new TreePath(java.util.Arrays.copyOf(path, path.length - 1));
    }

    /** The path from the root to {@code child}, or null. */
    public TreePath pathByAddingChild(Object child) {
        return child == null ? null : new TreePath(this, child);
    }

    public boolean isDescendant(TreePath aTreePath) {
        if (aTreePath == null) {
            return false;
        }
        if (aTreePath.path.length <= path.length) {
            return false;
        }
        for (int i = 0; i < path.length; i++) {
            if (!path[i].equals(aTreePath.path[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TreePath)) {
            return false;
        }
        return java.util.Arrays.equals(path, ((TreePath) o).path);
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = java.util.Arrays.hashCode(path);
        }
        return hash;
    }

    @Override
    public String toString() {
        return java.util.Arrays.toString(path);
    }
}
