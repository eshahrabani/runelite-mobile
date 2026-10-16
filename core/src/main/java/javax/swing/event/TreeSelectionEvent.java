package javax.swing.event;

import javax.swing.tree.TreePath;

/** Swing {@code TreeSelectionEvent}: a tree selection changed. */
public class TreeSelectionEvent extends java.util.EventObject {
    private static final long serialVersionUID = 5191549586060607292L;

    protected TreePath[] paths;
    protected boolean[] areNew;
    protected TreePath oldLeadSelectionPath;
    protected TreePath newLeadSelectionPath;

    public TreeSelectionEvent(Object source, TreePath[] paths, boolean[] areNew,
                              TreePath oldLeadSelectionPath, TreePath newLeadSelectionPath) {
        super(source == null ? TreeSelectionEvent.class : source);
        this.paths = paths;
        this.areNew = areNew;
        this.oldLeadSelectionPath = oldLeadSelectionPath;
        this.newLeadSelectionPath = newLeadSelectionPath;
    }

    public TreeSelectionEvent(Object source, TreePath path, boolean isNew,
                              TreePath oldLeadSelectionPath, TreePath newLeadSelectionPath) {
        this(source, new TreePath[] {path}, new boolean[] {isNew}, oldLeadSelectionPath, newLeadSelectionPath);
    }

    public TreePath[] getPaths() {
        return paths;
    }

    public TreePath getPath() {
        return paths == null || paths.length == 0 ? null : paths[0];
    }

    public boolean isAddedPath() {
        return areNew != null && areNew.length > 0 && areNew[0];
    }

    public boolean isAddedPath(TreePath path) {
        if (paths == null || areNew == null) {
            return false;
        }
        for (int i = 0; i < paths.length; i++) {
            if (paths[i] != null && paths[i].equals(path)) {
                return areNew[i];
            }
        }
        return false;
    }

    public TreePath getOldLeadSelectionPath() {
        return oldLeadSelectionPath;
    }

    public TreePath getNewLeadSelectionPath() {
        return newLeadSelectionPath;
    }
}
