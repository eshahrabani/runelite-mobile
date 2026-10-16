package javax.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.border.Border;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.RowMapper;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * Swing {@code JTree}: a painting tree with expand/collapse and selection.
 *
 * <p>Hand-written to the depth the port needs (see the Swing sidebar plan, "B5"): model
 * wiring, row painting, expansion state and selection. Rows are the visible nodes, walked
 * from the model's root with collapsed subtrees skipped; a tap on a node's twisty toggles
 * expansion, a tap on the label selects the node's path (firing one
 * {@code TreeSelectionEvent}).
 *
 * <p>Deliberately not implemented: inline node editing, drag-and-drop reordering, the
 * {@code TreeCellRenderer} indirection (rows are painted directly with the component font),
 * the {@code TreeWillExpandListener} veto, and the accessibility tree.
 */
public class JTree extends JComponent implements Scrollable, RowMapper, javax.accessibility.Accessible {

    private static final long serialVersionUID = -3243493141123251866L;

    /** Indentation added per tree level (logical pixels). */
    private static final int INDENT = 14;
    /** Size of the expand/collapse twisty. */
    private static final int HANDLE = 12;

    private TreeModel model;
    private TreeSelectionModel selectionModel = new DefaultTreeSelectionModel();
    private final Set<TreePath> expandedPaths = new HashSet<>();
    private boolean rootVisible = true;
    private boolean showsRootHandles;
    private boolean expandsSelectedPaths = true;
    private int rowHeight = 18;
    private Color selectionBackground;
    private Color selectionForeground;

    private final TreeModelListener modelListener = new TreeModelListener() {
        @Override
        public void treeNodesChanged(TreeModelEvent e) {
            repaint();
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
            revalidate();
            repaint();
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
            revalidate();
            repaint();
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            revalidate();
            repaint();
        }
    };

    public JTree(TreeModel newModel) {
        setOpaque(true);
        Color bg = UIManager.getColor("Tree.background");
        Color fg = UIManager.getColor("Tree.foreground");
        if (bg != null) {
            setBackground(bg);
        }
        if (fg != null) {
            setForeground(fg);
        }
        java.awt.Font f = UIManager.getFont("Tree.font");
        if (f != null) {
            setFont(f);
        }
        selectionBackground = UIManager.getColor("List.selectionBackground");
        selectionForeground = UIManager.getColor("List.selectionForeground");
        selectionModel.setRowMapper(this);
        selectionModel.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                repaint();
            }
        });
        setModel(newModel);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleTap(e);
            }
        });
    }

    public JTree(TreeNode root) {
        this(new DefaultTreeModel(root));
    }

    // ------------------------------------------------------------------ model

    public void setModel(TreeModel newModel) {
        if (this.model != null) {
            this.model.removeTreeModelListener(modelListener);
        }
        this.model = newModel;
        if (newModel != null) {
            newModel.addTreeModelListener(modelListener);
        }
        expandedPaths.clear();
        revalidate();
        repaint();
    }

    public TreeModel getModel() {
        return model;
    }

    public void setRootVisible(boolean rootVisible) {
        this.rootVisible = rootVisible;
        revalidate();
        repaint();
    }

    public boolean isRootVisible() {
        return rootVisible;
    }

    public void setShowsRootHandles(boolean newValue) {
        showsRootHandles = newValue;
        repaint();
    }

    public boolean getShowsRootHandles() {
        return showsRootHandles;
    }

    public void setExpandsSelectedPaths(boolean newValue) {
        expandsSelectedPaths = newValue;
    }

    public boolean getExpandsSelectedPaths() {
        return expandsSelectedPaths;
    }

    @Override
    public void setBorder(Border border) {
        super.setBorder(border);
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------- selection

    public TreeSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void setSelectionModel(TreeSelectionModel newModel) {
        if (newModel == null) {
            return;
        }
        selectionModel = newModel;
        selectionModel.setRowMapper(this);
        repaint();
    }

    public void addTreeSelectionListener(TreeSelectionListener listener) {
        selectionModel.addTreeSelectionListener(listener);
    }

    public void removeTreeSelectionListener(TreeSelectionListener listener) {
        selectionModel.removeTreeSelectionListener(listener);
    }

    public void setSelectionPath(TreePath path) {
        selectionModel.setSelectionPath(path);
    }

    public TreePath getSelectionPath() {
        return selectionModel.getSelectionPath();
    }

    public TreePath[] getSelectionPaths() {
        return selectionModel.getSelectionPaths();
    }

    /** The user object of the lead selected path, or {@code null}. */
    public Object getLastSelectedPathComponent() {
        TreePath path = selectionModel.getLeadSelectionPath();
        return path == null ? null : path.getLastPathComponent();
    }

    // -------------------------------------------------------------- expansion

    /** Expands the node at {@code path} so its children become visible. */
    public void expandPath(TreePath path) {
        if (path != null && expandedPaths.add(path)) {
            revalidate();
            repaint();
        }
    }

    /** Collapses the node at {@code path}; a selected descendant is cleared. */
    public void collapsePath(TreePath path) {
        if (path == null || !expandedPaths.remove(path)) {
            return;
        }
        TreePath lead = selectionModel.getLeadSelectionPath();
        if (lead != null && path.isDescendant(lead)) {
            selectionModel.clearSelection();
        }
        revalidate();
        repaint();
    }

    public boolean isExpanded(TreePath path) {
        return path != null && expandedPaths.contains(path);
    }

    public boolean isCollapsed(TreePath path) {
        return path != null && !expandedPaths.contains(path);
    }

    /** Expands the node at {@code row}. */
    public void expandRow(int row) {
        expandPath(getPathForRow(row));
    }

    /** Collapses the node at {@code row}. */
    public void collapseRow(int row) {
        collapsePath(getPathForRow(row));
    }

    // ------------------------------------------------------------------- rows

    /** Number of display rows (the visible nodes). */
    public int getRowCount() {
        return visibleRows().size();
    }

    /** The path displayed at {@code row}, or {@code null}. */
    public TreePath getPathForRow(int row) {
        List<TreePath> rows = visibleRows();
        return row < 0 || row >= rows.size() ? null : rows.get(row);
    }

    /** The display row of {@code path}, or {@code -1}. */
    public int getRowForPath(TreePath path) {
        if (path == null) {
            return -1;
        }
        return visibleRows().indexOf(path);
    }

    /** The bounds of {@code row} in this component's coordinates. */
    public Rectangle getRowBounds(int row) {
        if (row < 0 || row >= getRowCount()) {
            return null;
        }
        Insets insets = getInsets();
        return new Rectangle(insets.left, insets.top + row * rowHeight,
                Math.max(0, getWidth() - insets.left - insets.right), rowHeight);
    }

    @Override
    public int[] getRowsForPaths(TreePath[] paths) {
        if (paths == null || paths.length == 0) {
            return new int[0];
        }
        List<TreePath> rows = visibleRows();
        int[] out = new int[paths.length];
        for (int i = 0; i < paths.length; i++) {
            out[i] = paths[i] == null ? -1 : rows.indexOf(paths[i]);
        }
        return out;
    }

    /** The rows, top to bottom: the root (when visible) then each expanded node's children. */
    private List<TreePath> visibleRows() {
        List<TreePath> out = new ArrayList<>();
        if (model == null || model.getRoot() == null) {
            return out;
        }
        TreePath rootPath = new TreePath(model.getRoot());
        if (rootVisible) {
            out.add(rootPath);
            addChildren(rootPath, out);
        } else {
            int count = model.getChildCount(model.getRoot());
            for (int i = 0; i < count; i++) {
                TreePath childPath = rootPath.pathByAddingChild(
                        model.getChild(model.getRoot(), i));
                out.add(childPath);
                addChildren(childPath, out);
            }
        }
        return out;
    }

    private void addChildren(TreePath parent, List<TreePath> out) {
        if (!isExpanded(parent) || model.isLeaf(parent.getLastPathComponent())) {
            return;
        }
        Object parentNode = parent.getLastPathComponent();
        int count = model.getChildCount(parentNode);
        for (int i = 0; i < count; i++) {
            TreePath child = parent.pathByAddingChild(model.getChild(parentNode, i));
            out.add(child);
            addChildren(child, out);
        }
    }

    /** The x of the twisty for {@code path}. */
    private int handleX(TreePath path, Insets insets) {
        int depth = path.getPathCount() - (rootVisible ? 1 : 0);
        int x = insets.left + 1 + depth * INDENT;
        if (showsRootHandles || depth > 0) {
            return x;
        }
        return -1;
    }

    // --------------------------------------------------------------- geometry

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        return getPreferredScrollableViewportSize();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        int rows = Math.max(1, getRowCount());
        int width = 0;
        FontMetrics fm = getFontMetrics(getFont());
        for (TreePath path : visibleRows()) {
            int depth = path.getPathCount() - (rootVisible ? 1 : 0);
            width = Math.max(width, depth * INDENT + 24
                    + (fm == null ? 0 : fm.stringWidth(String.valueOf(path.getLastPathComponent()))));
        }
        return new Dimension(Math.max(width + 8, 60), rows * rowHeight + 2);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return rowHeight;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int extent = visibleRect == null ? 0 : visibleRect.height;
        return Math.max(rowHeight, extent - rowHeight);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // ------------------------------------------------------------------ paint

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (model == null) {
            return;
        }
        Insets insets = getInsets();
        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        List<TreePath> rows = visibleRows();
        Rectangle clip = g.getClipBounds();
        Color textColor = getForeground() != null ? getForeground() : Color.BLACK;
        for (int row = 0; row < rows.size(); row++) {
            TreePath path = rows.get(row);
            Rectangle bounds = new Rectangle(insets.left, insets.top + row * rowHeight,
                    Math.max(0, getWidth() - insets.left - insets.right), rowHeight);
            if (clip != null && !bounds.intersects(clip)) {
                continue;
            }
            boolean selected = selectionModel.isPathSelected(path);
            if (selected && selectionBackground != null) {
                g.setColor(selectionBackground);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            boolean hasChildren = model.getChildCount(path.getLastPathComponent()) > 0;
            int hx = handleX(path, insets);
            int textX = insets.left + 3 + (path.getPathCount() - (rootVisible ? 1 : 0)) * INDENT;
            if (hasChildren && hx >= 0) {
                paintHandle(g, hx, bounds.y + (rowHeight - HANDLE) / 2,
                        isExpanded(path), textColor);
                textX = hx + HANDLE + 3;
            }
            g.setColor(selected && selectionForeground != null ? selectionForeground : textColor);
            String text = String.valueOf(path.getLastPathComponent());
            int baseline = bounds.y + (rowHeight + fm.getAscent() - fm.getDescent()) / 2;
            Graphics g2 = g.create();
            try {
                g2.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.setFont(getFont());
                g2.setColor(selected && selectionForeground != null ? selectionForeground : textColor);
                g2.drawString(text, textX, baseline);
            } finally {
                g2.dispose();
            }
        }
    }

    /** A filled triangle: pointing right when collapsed, down when expanded. */
    private void paintHandle(Graphics g, int x, int y, boolean expanded, Color color) {
        g.setColor(color);
        int s = HANDLE;
        if (expanded) {
            int[] xs = {x + 2, x + s - 2, x + s / 2};
            int[] ys = {y + 4, y + 4, y + s - 3};
            g.fillPolygon(xs, ys, 3);
        } else {
            int[] xs = {x + 4, x + 4, x + s - 3};
            int[] ys = {y + 2, y + s - 2, y + s / 2};
            g.fillPolygon(xs, ys, 3);
        }
    }

    // ------------------------------------------------------------------ input

    private void handleTap(MouseEvent e) {
        if (!isEnabled()) {
            return;
        }
        Insets insets = getInsets();
        int row = rowAt(e.getPoint(), insets);
        if (row < 0) {
            return;
        }
        TreePath path = getPathForRow(row);
        if (path == null) {
            return;
        }
        Object node = path.getLastPathComponent();
        boolean hasChildren = model.getChildCount(node) > 0;
        int hx = handleX(path, insets);
        if (hasChildren && hx >= 0 && e.getX() >= hx - 2 && e.getX() < hx + HANDLE + 2) {
            if (isExpanded(path)) {
                collapsePath(path);
            } else {
                expandPath(path);
            }
        } else {
            selectionModel.setSelectionPath(path);
        }
        e.consume();
    }

    private int rowAt(Point p, Insets insets) {
        int row = (p.y - insets.top) / rowHeight;
        return row < 0 || row >= getRowCount() ? -1 : row;
    }
}
