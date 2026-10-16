package javax.swing.text;

import java.awt.Container;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

import javax.swing.event.DocumentEvent;

/**
 * Hand-written {@code javax.swing.text.View}: one piece of a document's rendering tree.
 *
 * <p>Why hand-written: the port's HTML support and the client's {@code JEditorPane} subclasses
 * build views through {@link ViewFactory}, and the plain text components answer
 * {@code getViewFactory()} with one. The generated stub declared no members.
 *
 * <p>Deviations from the JDK, both documented in place: the port's plain text components lay
 * their text out themselves (they do not build a {@link View} tree), so the child-management
 * and update methods here are a working but simple implementation rather than the JDK's
 * update-index machinery; {@code getGraphics()} is answered from the component's stage buffer
 * only while painting.
 */
public abstract class View implements javax.swing.SwingConstants {

    /** A view that cannot be broken at this point. */
    public static final int BadBreakWeight = 0;

    /** A view that can be broken here when nothing better exists. */
    public static final int GoodBreakWeight = 1000;

    /** A view that should be broken here in preference to a {@code GoodBreakWeight} point. */
    public static final int ExcellentBreakWeight = 2000;

    /** A view that must be broken here (a line break). */
    public static final int ForcedBreakWeight = 3000;

    private View parent;
    private final Element elem;
    private View[] children;
    private int width;
    private int height;

    protected View(Element elem) {
        this.elem = elem;
    }

    /** The view's parent, or null for a root view. */
    public View getParent() {
        return parent;
    }

    /** True when the view is part of the display (children are always visible here). */
    public boolean isVisible() {
        return true;
    }

    /** The element this view represents. */
    public Element getElement() {
        return elem;
    }

    /** The document of the view's element. */
    public Document getDocument() {
        return elem == null ? null : elem.getDocument();
    }

    /** The offset the view starts at. */
    public int getStartOffset() {
        return elem == null ? 0 : elem.getStartOffset();
    }

    /** The offset the view ends at. */
    public int getEndOffset() {
        return elem == null ? 0 : elem.getEndOffset();
    }

    /** The attributes of the view's element, or null. */
    public AttributeSet getAttributes() {
        return elem == null ? null : elem.getAttributes();
    }

    // ------------------------------------------------------------ spans

    /** The preferred span along {@code axis} ({@link #X_AXIS} or {@link #Y_AXIS}). */
    public abstract float getPreferredSpan(int axis);

    /** The smallest span the view can occupy along {@code axis}. */
    public float getMinimumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    /** The largest span the view can occupy along {@code axis}. */
    public float getMaximumSpan(int axis) {
        return getPreferredSpan(axis);
    }

    /** The view's alignment along {@code axis}, 0..1. */
    public float getAlignment(int axis) {
        return 0.5f;
    }

    /** Tells the parent that a child's size preference changed. */
    public void preferenceChanged(View child, boolean width, boolean height) {
        if (parent != null) {
            parent.preferenceChanged(this, width, height);
        }
    }

    // ----------------------------------------------------------- paint

    /** Renders the view into {@code g} inside {@code allocation}. */
    public abstract void paint(Graphics g, Shape allocation);

    /** Called by the parent after it repositions this view. */
    public void setSize(float width, float height) {
        this.width = (int) width;
        this.height = (int) height;
    }

    /** The size the parent last gave this view. */
    public int getWidth() {
        return width;
    }

    /** The size the parent last gave this view. */
    public int getHeight() {
        return height;
    }

    /**
     * The component hosting this view; the query is forwarded to the parent, so a root view
     * (which has none) overrides it.
     */
    public Container getContainer() {
        View v = parent;
        return v != null ? v.getContainer() : null;
    }

    /** The factory feeding this view hierarchy; forwarded to the parent. */
    public ViewFactory getViewFactory() {
        View v = parent;
        return v != null ? v.getViewFactory() : null;
    }

    /** The graphics of the hosting container, or null while the view is not being painted. */
    public Graphics getGraphics() {
        Container c = getContainer();
        return c == null ? null : c.getGraphics();
    }

    // -------------------------------------------------------- children

    /** The number of child views. */
    public int getViewCount() {
        return children == null ? 0 : children.length;
    }

    /** The child at {@code n}, or null. */
    public View getView(int n) {
        if (children == null || n < 0 || n >= children.length) {
            return null;
        }
        return children[n];
    }

    /** True when the view has no children (a leaf of the rendering tree). */
    public boolean isLeaf() {
        return getViewCount() == 0;
    }

    /** Assigns the parent view. */
    public void setParent(View parent) {
        this.parent = parent;
    }

    /** Inserts {@code v} at {@code offs}. */
    public void insert(int offs, View v) {
        if (v == null) {
            return;
        }
        View[] current = children == null ? new View[0] : children;
        View[] updated = new View[current.length + 1];
        System.arraycopy(current, 0, updated, 0, offs);
        updated[offs] = v;
        System.arraycopy(current, offs, updated, offs + 1, current.length - offs);
        children = updated;
        v.setParent(this);
    }

    /** Appends {@code v}. */
    public void append(View v) {
        insert(getViewCount(), v);
    }

    /** Removes the child at {@code i}. */
    public void remove(int i) {
        if (children == null || i < 0 || i >= children.length) {
            return;
        }
        View[] updated = new View[children.length - 1];
        System.arraycopy(children, 0, updated, 0, i);
        System.arraycopy(children, i + 1, updated, i, children.length - i - 1);
        children[i].setParent(null);
        children = updated;
    }

    /** Removes every child. */
    public void removeAll() {
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                children[i].setParent(null);
            }
            children = null;
        }
    }

    /** Replaces the children in {@code [offset, offset + length)} with {@code views}. */
    public void replace(int offset, int length, View[] views) {
        if (children == null) {
            children = views == null ? null : views.clone();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    children[i].setParent(this);
                }
            }
            return;
        }
        int added = views == null ? 0 : views.length;
        View[] updated = new View[children.length - length + added];
        System.arraycopy(children, 0, updated, 0, offset);
        if (added > 0) {
            System.arraycopy(views, 0, updated, offset, added);
            for (int i = 0; i < added; i++) {
                views[i].setParent(this);
            }
        }
        System.arraycopy(children, offset + length, updated, offset + added,
                children.length - offset - length);
        children = updated;
    }

    /** The allocation of child {@code index} inside {@code a} (the identity here). */
    public Shape getChildAllocation(int index, Shape a) {
        return a;
    }

    /** The index of the child holding {@code pos}, or -1. */
    public int getViewIndex(int pos, Position.Bias b) {
        if (children == null) {
            return -1;
        }
        for (int i = 0; i < children.length; i++) {
            if (pos < children[i].getEndOffset()) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the child containing the point ({@code -1} when there is none). */
    public int getViewIndex(float x, float y, Shape allocation) {
        if (children == null) {
            return -1;
        }
        for (int i = 0; i < children.length; i++) {
            Rectangle r = allocation == null ? null : allocation.getBounds();
            if (r != null && r.contains((int) x, (int) y)) {
                return i;
            }
        }
        return -1;
    }

    // --------------------------------------------------- model ⇄ view

    /** The area the text at {@code pos} occupies inside {@code a}. */
    public abstract Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException;

    /** The area the text at {@code pos} occupies (forward bias). */
    public Shape modelToView(int pos, Shape a) throws BadLocationException {
        return modelToView(pos, a, Position.Bias.Forward);
    }

    /** The offset under the point {@code (x, y)}; fills {@code biasRet[0]} with its bias. */
    public abstract int viewToModel(float x, float y, Shape a, Position.Bias[] biasRet);

    /** The offset under the point (bias discarded). */
    public int viewToModel(float x, float y, Shape a) {
        return viewToModel(x, y, a, new Position.Bias[1]);
    }

    /** The offset {@code dir} visual positions from {@code pos} (returns {@code pos} here). */
    public int getNextVisualPositionFrom(int pos, Position.Bias b, Shape a, int direction,
                                         Position.Bias[] biasRet) throws BadLocationException {
        return pos;
    }

    /** The tooltip for a point, or null. */
    public String getToolTipText(float x, float y, Shape allocation) {
        return null;
    }

    // ---------------  update

    /** Handles an insertion in the model (no-op: the port's views are rebuilt by the kit). */
    public void insertUpdate(DocumentEvent e, Shape a, ViewFactory f) {
    }

    /** Handles a removal in the model. */
    public void removeUpdate(DocumentEvent e, Shape a, ViewFactory f) {
    }

    /** Handles an attribute-only change. */
    public void changedUpdate(DocumentEvent e, Shape a, ViewFactory f) {
    }

    /** Forwards an update to the children named by {@code ec}. */
    protected void forwardUpdate(DocumentEvent.ElementChange ec, DocumentEvent e, Shape a, ViewFactory f) {
    }

    /** Forwards an update to one child. */
    protected void forwardUpdateToView(View v, DocumentEvent e, Shape a, ViewFactory f) {
        if (e.getType() == DocumentEvent.EventType.INSERT) {
            v.insertUpdate(e, a, f);
        } else if (e.getType() == DocumentEvent.EventType.REMOVE) {
            v.removeUpdate(e, a, f);
        } else {
            v.changedUpdate(e, a, f);
        }
    }

    /** Applies the element change to this view's children. */
    protected boolean updateChildren(DocumentEvent.ElementChange ec, DocumentEvent e, ViewFactory f) {
        return false;
    }

    /** Recomputes the layout after a change (no-op here). */
    protected void updateLayout(DocumentEvent.ElementChange ec, DocumentEvent e, Shape a) {
    }

    // ------------------------------------------------- break/fragment

    /** A view covering {@code [p0, p1)} of this view's range (the view itself by default). */
    public View createFragment(int p0, int p1) {
        return this;
    }

    /** A view covering {@code [p0, p1)} created when the view must be split to fit. */
    public View breakView(int axis, int offset, float pos, float len) {
        return this;
    }

    /** How desirable a break at {@code (axis, pos, len)} is (see the {@code *BreakWeight}s). */
    public int getBreakWeight(int axis, float pos, float len) {
        return BadBreakWeight;
    }

    /** How much this view wants to absorb extra space along {@code axis}. */
    public int getResizeWeight(int axis) {
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + getStartOffset() + "," + getEndOffset() + "]";
    }

}
