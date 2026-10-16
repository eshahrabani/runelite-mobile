package java.awt;

import java.util.Vector;

/**
 * AWT {@code CardLayout}: the JDK's "one visible card at a time" layout.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the port
 * really runs it: RuneLite's {@code MultiplexingPluginPanel} swaps plugin panels with
 * {@link #show(Container, String)} and the grand exchange search/offers panels use it
 * for their result/empty/loading states.
 *
 * <p>Implemented JDK rules:
 * <ul>
 * <li>Cards are keyed by the {@code String} constraint passed to
 *     {@code addLayoutComponent}; adding a second card under an existing name replaces
 *     the first card's component in place, and every card but the first starts hidden.</li>
 * <li>{@link #first}/{@link #next}/{@link #previous}/{@link #last} hide the currently
 *     visible card and show another, wrapping around; {@code next}/{@code previous} fall
 *     back to the first card when no card is visible.</li>
 * <li>{@link #show(Container, String)} with an unknown name does nothing, and with the
 *     already visible card also does nothing.</li>
 * <li>{@code layoutContainer} sizes <em>every</em> card to the container minus the
 *     insets and twice the gaps (the hidden ones too, so switching cards never needs a
 *     resize), and forces the first card visible if none is.</li>
 * <li>{@code preferredLayoutSize}/{@code minimumLayoutSize} are the largest card's
 *     preferred/minimum size plus insets and {@code 2 * hgap}/{@code 2 * vgap}.</li>
 * </ul>
 *
 * <p>Port deviation: none in behaviour. {@code checkLayout} verifies the layout manager
 * is still installed on the container before every navigation call, which is the JDK's
 * own guard against a {@code CardLayout} being shared between containers.
 */
public class CardLayout implements LayoutManager2, java.io.Serializable {

    int hgap;
    int vgap;

    /** The cards in insertion order. */
    Vector<Card> vector = new Vector<>();

    /** Index of the card considered current; also the visible one. */
    int currentCard = 0;

    /** A named card. */
    class Card implements java.io.Serializable {
        public String name;
        public Component comp;

        public Card(String cardName, Component cardComponent) {
            name = cardName;
            comp = cardComponent;
        }
    }

    /** Creates a card layout with zero gaps. */
    public CardLayout() {
        this(0, 0);
    }

    /** Creates a card layout with the given horizontal and vertical gaps. */
    public CardLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    /** Returns the horizontal gap. */
    public int getHgap() {
        return hgap;
    }

    /** Sets the horizontal gap. */
    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    /** Returns the vertical gap. */
    public int getVgap() {
        return vgap;
    }

    /** Sets the vertical gap. */
    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        synchronized (comp.getTreeLock()) {
            if (constraints == null) {
                constraints = "";
            }
            if (constraints instanceof String) {
                addLayoutComponent((String) constraints, comp);
            } else {
                throw new IllegalArgumentException("cannot add to layout: constraint must be a string");
            }
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        synchronized (comp.getTreeLock()) {
            // Only the first card is visible; the rest are hidden until shown.
            if (!vector.isEmpty()) {
                comp.setVisible(false);
            }
            for (int i = 0; i < vector.size(); i++) {
                if (vector.get(i).name.equals(name)) {
                    vector.get(i).comp = comp;
                    return;
                }
            }
            vector.add(new Card(name, comp));
        }
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        synchronized (comp.getTreeLock()) {
            for (int i = 0; i < vector.size(); i++) {
                if (vector.get(i).comp == comp) {
                    // If the visible card is removed, show the next one instead.
                    if (comp.isVisible() && (comp.getParent() != null)) {
                        next(comp.getParent());
                    }
                    vector.remove(i);
                    if (currentCard > i) {
                        currentCard--;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int ncomponents = parent.getComponentCount();
            int w = 0;
            int h = 0;

            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                Dimension d = comp.getPreferredSize();
                if (d.width > w) {
                    w = d.width;
                }
                if (d.height > h) {
                    h = d.height;
                }
            }
            return new Dimension(insets.left + insets.right + w + hgap * 2,
                    insets.top + insets.bottom + h + vgap * 2);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int ncomponents = parent.getComponentCount();
            int w = 0;
            int h = 0;

            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                Dimension d = comp.getMinimumSize();
                if (d.width > w) {
                    w = d.width;
                }
                if (d.height > h) {
                    h = d.height;
                }
            }
            return new Dimension(insets.left + insets.right + w + hgap * 2,
                    insets.top + insets.bottom + h + vgap * 2);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int ncomponents = parent.getComponentCount();
            Component comp;
            boolean currentFound = false;

            for (int i = 0; i < ncomponents; i++) {
                comp = parent.getComponent(i);
                comp.setBounds(hgap + insets.left, vgap + insets.top,
                        parent.width - (hgap * 2 + insets.left + insets.right),
                        parent.height - (vgap * 2 + insets.top + insets.bottom));
                if (comp.isVisible()) {
                    currentFound = true;
                }
            }

            // At least one card has to be visible, otherwise nothing would show.
            if (!currentFound && ncomponents > 0) {
                parent.getComponent(0).setVisible(true);
            }
        }
    }

    /**
     * Flips to the first card.
     *
     * @throws IllegalArgumentException if this layout is not installed on {@code parent}
     */
    public void first(Container parent) {
        synchronized (parent.getTreeLock()) {
            checkLayout(parent);
            int ncomponents = parent.getComponentCount();
            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                if (comp.isVisible()) {
                    comp.setVisible(false);
                    break;
                }
            }
            if (ncomponents > 0) {
                currentCard = 0;
                parent.getComponent(0).setVisible(true);
                parent.validate();
            }
        }
    }

    /** Flips to the next card, wrapping around. */
    public void next(Container parent) {
        synchronized (parent.getTreeLock()) {
            checkLayout(parent);
            int ncomponents = parent.getComponentCount();
            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                if (comp.isVisible()) {
                    comp.setVisible(false);
                    currentCard = (i + 1) % ncomponents;
                    comp = parent.getComponent(currentCard);
                    comp.setVisible(true);
                    parent.validate();
                    return;
                }
            }
            showDefaultComponent(parent);
        }
    }

    /** Flips to the previous card, wrapping around. */
    public void previous(Container parent) {
        synchronized (parent.getTreeLock()) {
            checkLayout(parent);
            int ncomponents = parent.getComponentCount();
            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                if (comp.isVisible()) {
                    comp.setVisible(false);
                    currentCard = ((i > 0) ? i - 1 : ncomponents - 1);
                    comp = parent.getComponent(currentCard);
                    comp.setVisible(true);
                    parent.validate();
                    return;
                }
            }
            showDefaultComponent(parent);
        }
    }

    /** Shows the first card when none is visible. */
    void showDefaultComponent(Container parent) {
        if (parent.getComponentCount() > 0) {
            currentCard = 0;
            parent.getComponent(0).setVisible(true);
            parent.validate();
        }
    }

    /** Flips to the last card. */
    public void last(Container parent) {
        synchronized (parent.getTreeLock()) {
            checkLayout(parent);
            int ncomponents = parent.getComponentCount();
            for (int i = 0; i < ncomponents; i++) {
                Component comp = parent.getComponent(i);
                if (comp.isVisible()) {
                    comp.setVisible(false);
                    break;
                }
            }
            if (ncomponents > 0) {
                currentCard = ncomponents - 1;
                parent.getComponent(currentCard).setVisible(true);
                parent.validate();
            }
        }
    }

    /**
     * Flips to the card added under {@code name}; unknown names are ignored, and the
     * call is a no-op when that card is already visible.
     */
    public void show(Container parent, String name) {
        synchronized (parent.getTreeLock()) {
            checkLayout(parent);
            Component next = null;
            int ncomponents = vector.size();
            for (int i = 0; i < ncomponents; i++) {
                Card card = vector.get(i);
                if (card.name.equals(name)) {
                    next = card.comp;
                    currentCard = i;
                    break;
                }
            }
            if ((next != null) && !next.isVisible()) {
                ncomponents = parent.getComponentCount();
                for (int i = 0; i < ncomponents; i++) {
                    Component comp = parent.getComponent(i);
                    if (comp.isVisible()) {
                        comp.setVisible(false);
                        break;
                    }
                }
                next.setVisible(true);
                parent.validate();
            }
        }
    }

    /** Guards against sharing one {@code CardLayout} between containers (as the JDK does). */
    void checkLayout(Container parent) {
        if (parent.getLayout() != this) {
            throw new IllegalArgumentException("wrong parent for CardLayout");
        }
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // No cached layout state.
    }

    @Override
    public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + "]";
    }
}
