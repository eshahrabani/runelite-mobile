package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GroupLayout}: the JDK's spring based layout builder.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the port
 * really runs this layout: RuneLite's own panels (the plugin hub's plugin view, the config
 * panels, the Kourend library panel) build their whole content with
 * {@code createSequentialGroup()/createParallelGroup()/addGroup()/addPreferredGap(...)}.
 * The generated data-only stub returned {@code null} from every {@code create*} method, so
 * those panels threw an immediate NPE.
 *
 * <p>The implementation follows the JDK's spring model: each axis is described by a tree
 * of {@link Spring}s, where a spring has a minimum/preferred/maximum range; a
 * {@link SequentialGroup} sums its children and a {@link ParallelGroup} takes the maximum
 * of each of its children's ranges. Sizes are then solved in two passes: the tree is laid
 * out at the container's size to learn the actual origins, auto-created gaps are sized
 * from those origins, and the tree is laid out again with the final gap sizes.
 *
 * <p>Port deviations, all forced by what this runtime provides:
 * <ul>
 * <li>{@code Component.getBaseline(int,int)} does not exist here, so every spring reports
 *     "no baseline" ({@code -1}). That is not an approximation of a different result: the
 *     JDK's own {@code BaselineGroup} degenerates to a leading-aligned parallel group when
 *     no child has a baseline ({@code prefAscent} stays -1, {@code calculateMinSize}/
 *     {@code calculateMaxSize} fall back to the plain max of the children, and
 *     {@code setValidSize} calls {@code super}), so {@link Alignment#BASELINE} is treated
 *     as {@link Alignment#LEADING} here, as the JDK does for a component whose
 *     {@code getBaseline} returns -1.</li>
 * <li>{@code Container.getComponentOrientation()} does not exist here, so layout is always
 *     left-to-right (the JDK default).</li>
 * <li>The JDK's {@code Component.getMaximumSize()} returns {@code Short.MAX_VALUE} for a
 *     component that does not override it; this port's returns the preferred size. Spring
 *     maximums are therefore read through {@link #maximumSizeOf(Component)}, which
 *     reproduces the JDK's {@code JComponent.getMaximumSize()} exactly: explicit maximum,
 *     then the UI's, then {@code Short.MAX_VALUE} squared.</li>
 * <li>The JDK's {@code BaselineGroup} and {@code linkSize} support are omitted;
 *     {@code linkSize} is not used by any shipped call site and would only add spring
 *     arithmetic no consumer exercises.</li>
 * </ul>
 */
public class GroupLayout implements LayoutManager2 {

    /** Indicates that a range value should come from the component or gap itself. */
    public static final int DEFAULT_SIZE = -1;

    /** Indicates that the preferred size should be used for a range value. */
    public static final int PREFERRED_SIZE = -2;

    private static final int MIN_SIZE = 0;
    private static final int PREF_SIZE = 1;
    private static final int MAX_SIZE = 2;
    /** Layout pass: the size is known (the container's actual size). */
    private static final int SPECIFIC_SIZE = 3;

    /** Value of a spring field that has not been calculated yet. */
    private static final int UNSET = Integer.MIN_VALUE;

    /** Spring sizes are clamped to this to keep sums from overflowing. */
    private static final int INFINITE = Integer.MAX_VALUE >> 1;

    /** How a {@link ParallelGroup} aligns its children along the cross axis. */
    public enum Alignment {
        /** Align the children's leading edges. */
        LEADING,
        /** Align the children's trailing edges. */
        TRAILING,
        /** Centre the children. */
        CENTER,
        /**
         * Align the children on their baseline. This port has no component baselines, so
         * this behaves exactly like {@link #LEADING} (see the class note).
         */
        BASELINE
    }

    private final Container host;
    private Group horizontalGroup;
    private Group verticalGroup;
    private final Map<Component, ComponentInfo> componentInfos = new HashMap<>();
    private boolean springsChanged = true;
    private boolean autocreatePadding;
    private boolean autocreateContainerPadding;
    private boolean honorsVisibility = true;
    private boolean hasPreferredPaddingSprings;
    private LayoutStyle layoutStyle;
    /** Reused by {@link #areParallelSiblings} to avoid allocating per call. */
    private final List<Spring> ancestorScratch = new ArrayList<>();

    /**
     * Creates a layout manager for {@code host}. The host must be the only container this
     * layout manages.
     */
    public GroupLayout(Container host) {
        if (host == null) {
            throw new IllegalArgumentException("Container must be non-null");
        }
        this.host = host;
        setHorizontalGroup(createParallelGroup(Alignment.LEADING));
        setVerticalGroup(createParallelGroup(Alignment.LEADING));
    }

    // ----------------------------------------------------------- group factories

    /** Creates a group that lays its elements out one after another. */
    public SequentialGroup createSequentialGroup() {
        return new SequentialGroup();
    }

    /** Creates a group that aligns its elements (leading alignment, resizable). */
    public ParallelGroup createParallelGroup() {
        return createParallelGroup(Alignment.LEADING);
    }

    /** Creates a group that aligns its elements with the given alignment. */
    public ParallelGroup createParallelGroup(Alignment alignment) {
        return createParallelGroup(alignment, true);
    }

    /**
     * Creates an alignment group.
     *
     * @param alignment the alignment of the group's children
     * @param resizable {@code false} makes the group rigid: its minimum and maximum are
     *                  forced to its preferred size
     */
    public ParallelGroup createParallelGroup(Alignment alignment, boolean resizable) {
        if (alignment == null) {
            throw new IllegalArgumentException("Alignment must be non-null");
        }
        // BASELINE needs no special class here: with no baselines available in this port
        // the JDK's BaselineGroup behaves like a leading-aligned parallel group.
        return new ParallelGroup(alignment, resizable);
    }

    // ---------------------------------------------------------------- group setting

    /** Installs the group describing the horizontal axis. */
    public void setHorizontalGroup(Group group) {
        if (group == null) {
            throw new IllegalArgumentException("Group must be non-null");
        }
        checkGroup(group);
        horizontalGroup = createTopLevelGroup(group);
        springsChanged = true;
        invalidateHost();
    }

    /** Installs the group describing the vertical axis. */
    public void setVerticalGroup(Group group) {
        if (group == null) {
            throw new IllegalArgumentException("Group must be non-null");
        }
        checkGroup(group);
        verticalGroup = createTopLevelGroup(group);
        springsChanged = true;
        invalidateHost();
    }

    /** Returns the installed horizontal group (the caller's group, not the wrapper). */
    public Group getHorizontalGroup() {
        return unwrap(horizontalGroup);
    }

    /** Returns the installed vertical group (the caller's group, not the wrapper). */
    public Group getVerticalGroup() {
        return unwrap(verticalGroup);
    }

    /**
     * Wraps the caller's group in a sequential group; when container gaps are enabled the
     * wrapper also carries the leading and trailing container gap springs, which are what
     * turns {@code setAutoCreateContainerGaps(true)} into actual edge padding.
     */
    private Group createTopLevelGroup(Group specifiedGroup) {
        SequentialGroup group = createSequentialGroup();
        if (getAutoCreateContainerGaps()) {
            group.addSpring(new ContainerGapSpring());
            group.addGroup(specifiedGroup);
            group.addSpring(new ContainerGapSpring());
        } else {
            group.addGroup(specifiedGroup);
        }
        return group;
    }

    /** Returns the caller's group out of a top-level wrapper. */
    private static Group unwrap(Group topLevel) {
        int index = topLevel.springs.size() > 1 ? 1 : 0;
        Spring spring = topLevel.springs.get(index);
        return (spring instanceof Group) ? (Group) spring : topLevel;
    }

    /**
     * When enabled, a gap taken from {@link LayoutStyle} is inserted automatically between
     * consecutive components of every sequential group.
     */
    public void setAutoCreateGaps(boolean autoCreatePadding) {
        this.autocreatePadding = autoCreatePadding;
        invalidateHost();
    }

    /** Returns whether gaps are created automatically between components. */
    public boolean getAutoCreateGaps() {
        return autocreatePadding;
    }

    /**
     * When enabled, a container gap from {@link LayoutStyle} is inserted automatically
     * between the container's edge and the components touching it.
     */
    public void setAutoCreateContainerGaps(boolean autoCreateContainerPadding) {
        if (this.autocreateContainerPadding != autoCreateContainerPadding) {
            this.autocreateContainerPadding = autoCreateContainerPadding;
            // The container gaps live in the top-level wrapper, so both trees are rebuilt.
            Group horizontal = getHorizontalGroup();
            Group vertical = getVerticalGroup();
            horizontalGroup = createTopLevelGroup(horizontal);
            verticalGroup = createTopLevelGroup(vertical);
            springsChanged = true;
            invalidateHost();
        }
    }

    /** Returns whether container gaps are created automatically. */
    public boolean getAutoCreateContainerGaps() {
        return autocreateContainerPadding;
    }

    /** Global default for whether invisible components are ignored by the layout. */
    public void setHonorsVisibility(boolean honorsVisibility) {
        if (this.honorsVisibility != honorsVisibility) {
            this.honorsVisibility = honorsVisibility;
            springsChanged = true;
            invalidateHost();
        }
    }

    /** Returns the global visibility policy. */
    public boolean getHonorsVisibility() {
        return honorsVisibility;
    }

    /** Per-component override of the global visibility policy. */
    public void setHonorsVisibility(Component component, Boolean honorsVisibility) {
        if (component == null) {
            throw new IllegalArgumentException("Component must be non-null");
        }
        getComponentInfo(component).setHonorsVisibility(honorsVisibility);
        springsChanged = true;
        invalidateHost();
    }

    /** Returns the per-component visibility policy, or {@code null} when the default is used. */
    public Boolean getHonorsVisibility(Component component) {
        if (component == null) {
            throw new IllegalArgumentException("Component must be non-null");
        }
        return getComponentInfo(component).getHonorsVisibility();
    }

    /**
     * Sets the style used to calculate preferred gaps; {@code null} means "use
     * {@link LayoutStyle#getInstance()}".
     */
    public void setLayoutStyle(LayoutStyle layoutStyle) {
        this.layoutStyle = layoutStyle;
        invalidateHost();
    }

    /**
     * Port convenience overload: verifies {@code target} is this layout's container before
     * setting the style.
     */
    public void setLayoutStyle(Container target, LayoutStyle layoutStyle) {
        checkParent(target);
        setLayoutStyle(layoutStyle);
    }

    /** Returns the style set with {@link #setLayoutStyle(LayoutStyle)}, possibly null. */
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    // ------------------------------------------------------------- LayoutManager2

    @Override
    public void addLayoutComponent(String name, Component component) {
        // Components are registered through the groups, as the JDK does.
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
        // Components are registered through the groups, as the JDK does.
    }

    @Override
    public void removeLayoutComponent(Component component) {
        ComponentInfo info = componentInfos.remove(component);
        if (info != null) {
            info.dispose();
            springsChanged = true;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        checkParent(parent);
        prepare(PREF_SIZE);
        return adjustSize(horizontalGroup.getPreferredSize(SwingConstants.HORIZONTAL),
                verticalGroup.getPreferredSize(SwingConstants.VERTICAL));
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        checkParent(parent);
        prepare(MIN_SIZE);
        return adjustSize(horizontalGroup.getMinimumSize(SwingConstants.HORIZONTAL),
                verticalGroup.getMinimumSize(SwingConstants.VERTICAL));
    }

    @Override
    public Dimension maximumLayoutSize(Container parent) {
        checkParent(parent);
        prepare(MAX_SIZE);
        return adjustSize(horizontalGroup.getMaximumSize(SwingConstants.HORIZONTAL),
                verticalGroup.getMaximumSize(SwingConstants.VERTICAL));
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        checkParent(parent);
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        checkParent(parent);
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container parent) {
        checkParent(parent);
        // Nothing to clear: prepare() rebuilds the springs on every pass, because this
        // port's Container.invalidate() does not notify a LayoutManager2 (the JDK's does).
        // The method is still here because Container.invalidate() is documented to call it.
    }

    @Override
    public void layoutContainer(Container parent) {
        prepare(SPECIFIC_SIZE);
        Insets insets = parent.getInsets();
        int width = parent.getWidth() - insets.left - insets.right;
        int height = parent.getHeight() - insets.top - insets.bottom;
        boolean ltr = isLeftToRight();
        if (getAutoCreateGaps() || getAutoCreateContainerGaps() || hasPreferredPaddingSprings) {
            // Pass 1: size the tree at the container's size to learn the origins, then
            // turn the origins into automatic gap sizes.
            calculateAutopadding(horizontalGroup, SwingConstants.HORIZONTAL, SPECIFIC_SIZE, 0, width);
            calculateAutopadding(verticalGroup, SwingConstants.VERTICAL, SPECIFIC_SIZE, 0, height);
        }
        // Pass 2: lay the tree out again, now with the final gap sizes.
        horizontalGroup.setSize(SwingConstants.HORIZONTAL, 0, width);
        verticalGroup.setSize(SwingConstants.VERTICAL, 0, height);
        for (ComponentInfo info : componentInfos.values()) {
            info.setBounds(insets, width, ltr);
        }
    }

    @Override
    public String toString() {
        if (springsChanged) {
            registerComponents(horizontalGroup, SwingConstants.HORIZONTAL);
            registerComponents(verticalGroup, SwingConstants.VERTICAL);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("HORIZONTAL\n");
        createSpringDescription(sb, horizontalGroup, "  ", SwingConstants.HORIZONTAL);
        sb.append("\nVERTICAL\n");
        createSpringDescription(sb, verticalGroup, "  ", SwingConstants.VERTICAL);
        return sb.toString();
    }

    private void createSpringDescription(StringBuilder sb, Spring spring, String indent, int axis) {
        String origin = "";
        if (spring instanceof ComponentSpring) {
            ComponentSpring cSpring = (ComponentSpring) spring;
            origin = cSpring.getOrigin() + " ";
            String name = cSpring.getComponent().getName();
            if (name != null) {
                origin = "name=" + name + ", ";
            }
        }
        sb.append(indent).append(spring.getClass().getName()).append(' ')
                .append(Integer.toHexString(spring.hashCode())).append(' ')
                .append(origin).append(", size=").append(spring.getSize())
                .append(", alignment=").append(spring.getAlignment())
                .append(" prefs=[").append(spring.getMinimumSize(axis)).append(' ')
                .append(spring.getPreferredSize(axis)).append(' ')
                .append(spring.getMaximumSize(axis)).append("]\n");
        if (spring instanceof Group) {
            List<Spring> springs = ((Group) spring).springs;
            for (int counter = 0; counter < springs.size(); counter++) {
                createSpringDescription(sb, springs.get(counter), indent + "  ", axis);
            }
        }
    }

    // ------------------------------------------------------------------- internals

    private LayoutStyle getLayoutStyle0() {
        LayoutStyle style = getLayoutStyle();
        if (style == null) {
            style = LayoutStyle.getInstance();
        }
        return style;
    }

    private void invalidateHost() {
        if (host instanceof JComponent) {
            ((JComponent) host).revalidate();
        } else {
            host.invalidate();
        }
        host.repaint();
    }

    private void prepare(int sizeType) {
        // Step 1: drop the cached springs and refresh the cached visibility. The JDK can
        // skip this while its state is valid because Container.invalidate() calls
        // LayoutManager2.invalidateLayout(); this port's Container.invalidate() does not,
        // so a stale cache here would be permanent (a component that changed its
        // preferred size or was hidden would keep its old size forever). Rebuilding is
        // cheap for hand-written panels and always correct.
        horizontalGroup.setSize(SwingConstants.HORIZONTAL, UNSET, UNSET);
        verticalGroup.setSize(SwingConstants.VERTICAL, UNSET, UNSET);
        for (ComponentInfo ci : componentInfos.values()) {
            ci.refreshVisibility();
            ci.clearCachedSize();
        }
        // Step 2: bind every component of both groups to its ComponentInfo.
        if (springsChanged) {
            registerComponents(horizontalGroup, SwingConstants.HORIZONTAL);
            registerComponents(verticalGroup, SwingConstants.VERTICAL);
            checkComponents();
        }
        // Step 3: remove and re-insert the automatic padding (idempotent: only the springs
        // this layout created itself are removed, the developer's are re-targeted).
        horizontalGroup.removeAutopadding();
        verticalGroup.removeAutopadding();
        if (getAutoCreateGaps()) {
            insertAutopadding(true);
        } else if (hasPreferredPaddingSprings || getAutoCreateContainerGaps()) {
            insertAutopadding(false);
        }
        springsChanged = false;
        // Step 4: for size queries, resolve the padding springs' sizes now.
        if (sizeType != SPECIFIC_SIZE
                && (getAutoCreateGaps() || getAutoCreateContainerGaps() || hasPreferredPaddingSprings)) {
            calculateAutopadding(horizontalGroup, SwingConstants.HORIZONTAL, sizeType, 0, 0);
            calculateAutopadding(verticalGroup, SwingConstants.VERTICAL, sizeType, 0, 0);
        }
    }

    private void calculateAutopadding(Group group, int axis, int sizeType, int origin, int size) {
        group.unsetAutopadding();
        switch (sizeType) {
            case MIN_SIZE:
                size = group.getMinimumSize(axis);
                break;
            case PREF_SIZE:
                size = group.getPreferredSize(axis);
                break;
            case MAX_SIZE:
                size = group.getMaximumSize(axis);
                break;
            default:
                break;
        }
        group.setSize(axis, origin, size);
        group.calculateAutopadding(axis);
    }

    private void checkComponents() {
        for (ComponentInfo info : componentInfos.values()) {
            if (info.horizontalSpring == null) {
                throw new IllegalStateException(info.component + " is not attached to a horizontal group");
            }
            if (info.verticalSpring == null) {
                throw new IllegalStateException(info.component + " is not attached to a vertical group");
            }
        }
    }

    private void registerComponents(Group group, int axis) {
        List<Spring> springs = group.springs;
        for (int counter = springs.size() - 1; counter >= 0; counter--) {
            Spring spring = springs.get(counter);
            if (spring instanceof ComponentSpring) {
                ((ComponentSpring) spring).installIfNecessary(axis);
            } else if (spring instanceof Group) {
                registerComponents((Group) spring, axis);
            }
        }
    }

    /** Adds the container's insets to a size computed by the groups. */
    private Dimension adjustSize(int width, int height) {
        Insets insets = host.getInsets();
        return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
    }

    private void checkParent(Container parent) {
        if (parent != host) {
            throw new IllegalArgumentException("GroupLayout can only be used with one Container at a time");
        }
    }

    /** The group must have been created by this layout. */
    private void checkGroup(Group group) {
        if (group.owner != this) {
            throw new IllegalArgumentException("Group is not attached to this GroupLayout");
        }
    }

    /** Returns the info for a component, registering (and parenting) it if needed. */
    private ComponentInfo getComponentInfo(Component component) {
        ComponentInfo info = componentInfos.get(component);
        if (info == null) {
            info = new ComponentInfo(component);
            componentInfos.put(component, info);
            if (component.getParent() != host) {
                host.add(component);
            }
        }
        return info;
    }

    /**
     * Inserts or refreshes the automatic padding springs in both axes.
     *
     * @param insert {@code true} to insert the auto-created gap springs, {@code false} to
     *               only re-target the springs the developer added explicitly
     */
    private void insertAutopadding(boolean insert) {
        horizontalGroup.insertAutopadding(SwingConstants.HORIZONTAL,
                new ArrayList<AutoGapSpring>(1), new ArrayList<AutoGapSpring>(1),
                new ArrayList<ComponentSpring>(1), new ArrayList<ComponentSpring>(1), insert);
        verticalGroup.insertAutopadding(SwingConstants.VERTICAL,
                new ArrayList<AutoGapSpring>(1), new ArrayList<AutoGapSpring>(1),
                new ArrayList<ComponentSpring>(1), new ArrayList<ComponentSpring>(1), insert);
    }

    /**
     * True when the two components share a parallel-group ancestor along the given axis,
     * which is the JDK's rule for "these two components are laid out next to each other"
     * and therefore for whether an automatic gap may be inserted between them.
     */
    private boolean areParallelSiblings(Component source, Component target, int axis) {
        ComponentInfo sourceInfo = getComponentInfo(source);
        ComponentInfo targetInfo = getComponentInfo(target);
        Spring sourceSpring = (axis == SwingConstants.HORIZONTAL)
                ? sourceInfo.horizontalSpring : sourceInfo.verticalSpring;
        Spring targetSpring = (axis == SwingConstants.HORIZONTAL)
                ? targetInfo.horizontalSpring : targetInfo.verticalSpring;

        ancestorScratch.clear();
        Spring spring = sourceSpring.getParent();
        while (spring != null) {
            ancestorScratch.add(spring);
            spring = spring.getParent();
        }
        spring = targetSpring.getParent();
        while (spring != null) {
            if (ancestorScratch.contains(spring)) {
                // Walk up from the shared ancestor: a parallel group anywhere on that path
                // makes the two components siblings.
                while (spring != null) {
                    if (spring instanceof ParallelGroup) {
                        ancestorScratch.clear();
                        return true;
                    }
                    spring = spring.getParent();
                }
                ancestorScratch.clear();
                return false;
            }
            spring = spring.getParent();
        }
        ancestorScratch.clear();
        return false;
    }

    /** This port has no ComponentOrientation, so layout is always left to right. */
    private boolean isLeftToRight() {
        return true;
    }

    private static boolean isValidRangeValue(int value, boolean componentSpring) {
        if (value >= 0) {
            return true;
        }
        return componentSpring ? (value == DEFAULT_SIZE || value == PREFERRED_SIZE) : (value == PREFERRED_SIZE);
    }

    private static void checkSize(int min, int pref, int max, boolean componentSpring) {
        if (!isValidRangeValue(min, componentSpring)) {
            throw new IllegalArgumentException("Invalid size");
        }
        if (!componentSpring && pref < 0) {
            throw new IllegalArgumentException("Pref must be >= 0");
        }
        if (componentSpring && !isValidRangeValue(pref, true)) {
            throw new IllegalArgumentException("Invalid size");
        }
        if (!isValidRangeValue(max, componentSpring)) {
            throw new IllegalArgumentException("Invalid size");
        }
        if (min >= 0 && pref >= 0 && min > pref) {
            throw new IllegalArgumentException("Invalid size");
        }
        if (pref >= 0 && max >= 0 && pref > max) {
            throw new IllegalArgumentException("Invalid size");
        }
    }

    private static void checkPreferredGapValues(int pref, int max) {
        if ((pref < 0 && pref != DEFAULT_SIZE && pref != PREFERRED_SIZE)
                || (max < 0 && max != DEFAULT_SIZE && max != PREFERRED_SIZE)
                || (pref >= 0 && max >= 0 && pref > max)) {
            throw new IllegalArgumentException(
                    "Pref and max must be either DEFAULT_SIZE, PREFERRED_SIZE, or >= 0 and pref <= max");
        }
    }

    // ------------------------------------------------------------------- springs

    /**
     * A spring: a size range (minimum, preferred, maximum) plus the size the last layout
     * pass assigned to it. Ranges are calculated lazily and cached until {@link #unset()}.
     */
    public abstract class Spring {
        final GroupLayout owner = GroupLayout.this;
        private int size = UNSET;
        private int min = UNSET;
        private int pref = UNSET;
        private int max = UNSET;
        private Spring parent;
        private Alignment alignment;
        /** Origin assigned by the last layout pass; only components really use it. */
        private int origin;

        abstract int calculateMinimumSize(int axis);

        abstract int calculatePreferredSize(int axis);

        abstract int calculateMaximumSize(int axis);

        void setParent(Spring parent) {
            this.parent = parent;
        }

        Spring getParent() {
            return parent;
        }

        /** Alignment of this spring inside its parent group, may be {@code null}. */
        void setAlignment(Alignment alignment) {
            this.alignment = alignment;
        }

        Alignment getAlignment() {
            return alignment;
        }

        /** Cached minimum size along {@code axis}. */
        final int getMinimumSize(int axis) {
            if (min == UNSET) {
                min = constrain(calculateMinimumSize(axis));
            }
            return min;
        }

        /** Cached preferred size along {@code axis}. */
        final int getPreferredSize(int axis) {
            if (pref == UNSET) {
                pref = constrain(calculatePreferredSize(axis));
            }
            return pref;
        }

        /** Cached maximum size along {@code axis}. */
        final int getMaximumSize(int axis) {
            if (max == UNSET) {
                max = constrain(calculateMaximumSize(axis));
            }
            return max;
        }

        /**
         * Records the size and origin assigned by a layout pass; {@link #UNSET} clears the
         * spring instead.
         */
        void setSize(int axis, int origin, int size) {
            this.size = size;
            this.origin = origin;
            if (size == UNSET) {
                unset();
            }
        }

        /** Clears the cached range and size so they are recalculated on demand. */
        void unset() {
            size = min = pref = max = UNSET;
        }

        /** Size assigned by the last layout pass. */
        int getSize() {
            return size;
        }

        /** Origin assigned by the last layout pass. */
        int getOrigin() {
            return origin;
        }

        int constrain(int value) {
            return Math.min(value, INFINITE);
        }

        /**
         * True when this spring will always be zero-sized (an invisible component, or an
         * automatic gap when those are treated as zero).
         */
        abstract boolean willHaveZeroSize(boolean treatAutopaddingAsZeroSized);
    }

    /**
     * A group of springs: either {@link SequentialGroup} (children one after another) or
     * {@link ParallelGroup} (children aligned).
     */
    public abstract class Group extends Spring {
        final List<Spring> springs = new ArrayList<>();

        /**
         * Declared by the stub generator; the JDK creates groups only through
         * {@code GroupLayout.createSequentialGroup()/createParallelGroup()}.
         */
        public Group() {
        }

        /** Adds a group as a child. */
        public Group addGroup(Group group) {
            return addSpring(group);
        }

        /** Adds a component using its own size range. */
        public Group addComponent(Component component) {
            return addComponent(component, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE);
        }

        /** Adds a component with an explicit size range. */
        public Group addComponent(Component component, int min, int pref, int max) {
            return addSpring(new ComponentSpring(component, min, pref, max));
        }

        /** Adds a rigid gap of {@code size} pixels. */
        public Group addGap(int size) {
            return addGap(size, size, size);
        }

        /** Adds a gap with an explicit size range. */
        public Group addGap(int min, int pref, int max) {
            return addSpring(new GapSpring(min, pref, max));
        }

        Spring getSpring(int index) {
            return springs.get(index);
        }

        int indexOf(Spring spring) {
            return springs.indexOf(spring);
        }

        /** Adds a spring and returns this group. */
        Group addSpring(Spring spring) {
            springs.add(spring);
            spring.setParent(this);
            if (!(spring instanceof AutoGapSpring) || !((AutoGapSpring) spring).getUserCreated()) {
                springsChanged = true;
            }
            return this;
        }

        @Override
        void setSize(int axis, int origin, int size) {
            super.setSize(axis, origin, size);
            if (size == UNSET) {
                for (int counter = springs.size() - 1; counter >= 0; counter--) {
                    getSpring(counter).setSize(axis, origin, size);
                }
            } else {
                setValidSize(axis, origin, size);
            }
        }

        /** Lays the children out into the given span. */
        abstract void setValidSize(int axis, int origin, int size);

        @Override
        int calculateMinimumSize(int axis) {
            return calculateSize(axis, MIN_SIZE);
        }

        @Override
        int calculatePreferredSize(int axis) {
            return calculateSize(axis, PREF_SIZE);
        }

        @Override
        int calculateMaximumSize(int axis) {
            return calculateSize(axis, MAX_SIZE);
        }

        /** Combines the children's ranges with {@link #operator}. */
        int calculateSize(int axis, int type) {
            int count = springs.size();
            if (count == 0) {
                return 0;
            }
            // Combine pairwise and clamp after every step, which is what keeps a sum of
            // unbounded springs from overflowing (the JDK's calculateSize does the same).
            long size = 0;
            for (int counter = 0; counter < count; counter++) {
                size = operator(constrain((int) size), getSpringSize(getSpring(counter), axis, type));
                size = constrain((int) size);
            }
            return (int) size;
        }

        int getSpringSize(Spring spring, int axis, int type) {
            switch (type) {
                case MIN_SIZE:
                    return spring.getMinimumSize(axis);
                case PREF_SIZE:
                    return spring.getPreferredSize(axis);
                case MAX_SIZE:
                    return spring.getMaximumSize(axis);
                default:
                    return 0;
            }
        }

        /** How two child ranges combine: sum for sequential groups, max for parallel ones. */
        abstract int operator(int a, int b);

        /**
         * Inserts (when {@code insert}) or re-targets the automatic padding springs of this
         * group and its children, JDK style: {@code leading}/{@code trailing} carry the
         * visible component springs around the cursor and the padding lists carry the
         * springs that still need targets.
         */
        abstract void insertAutopadding(int axis, List<AutoGapSpring> leadingPadding,
                List<AutoGapSpring> trailingPadding, List<ComponentSpring> leading,
                List<ComponentSpring> trailing, boolean insert);

        /** Removes the springs this layout created automatically. */
        void removeAutopadding() {
            unset();
            for (int counter = springs.size() - 1; counter >= 0; counter--) {
                Spring spring = springs.get(counter);
                if (spring instanceof AutoGapSpring) {
                    if (((AutoGapSpring) spring).getUserCreated()) {
                        ((AutoGapSpring) spring).reset();
                    } else {
                        springs.remove(counter);
                    }
                } else if (spring instanceof Group) {
                    ((Group) spring).removeAutopadding();
                }
            }
        }

        /** Recomputes the automatic gap sizes from the origins of the last layout pass. */
        void calculateAutopadding(int axis) {
            for (int counter = springs.size() - 1; counter >= 0; counter--) {
                Spring spring = springs.get(counter);
                if (spring instanceof AutoGapSpring) {
                    spring.unset();
                    ((AutoGapSpring) spring).calculatePadding(axis);
                } else if (spring instanceof Group) {
                    ((Group) spring).calculateAutopadding(axis);
                }
            }
            // The sizes above changed the group's range.
            unset();
        }

        /** Clears only the cached ranges of the automatic padding springs and this group. */
        void unsetAutopadding() {
            for (int counter = springs.size() - 1; counter >= 0; counter--) {
                Spring spring = springs.get(counter);
                if (spring instanceof AutoGapSpring) {
                    spring.unset();
                } else if (spring instanceof Group) {
                    ((Group) spring).unsetAutopadding();
                }
            }
            unset();
        }

        int indexOfNextNonZeroSpring(int index, boolean treatAutopaddingAsZeroSized) {
            while (index < springs.size()) {
                Spring spring = springs.get(index);
                if (!spring.willHaveZeroSize(treatAutopaddingAsZeroSized)) {
                    return index;
                }
                index++;
            }
            return index;
        }

        @Override
        boolean willHaveZeroSize(boolean treatAutopaddingAsZeroSized) {
            for (int i = springs.size() - 1; i >= 0; i--) {
                Spring spring = springs.get(i);
                if (!spring.willHaveZeroSize(treatAutopaddingAsZeroSized)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Lays its children out one after another along the axis. Its range is the sum of the
     * children's ranges.
     */
    public class SequentialGroup extends Group {

        /**
         * Declared by the stub generator; the JDK creates groups only through
         * {@code GroupLayout.createSequentialGroup()}.
         */
        public SequentialGroup() {
        }

        @Override
        public SequentialGroup addGroup(Group group) {
            return (SequentialGroup) super.addGroup(group);
        }

        @Override
        public SequentialGroup addComponent(Component component) {
            return (SequentialGroup) super.addComponent(component);
        }

        @Override
        public SequentialGroup addComponent(Component component, int min, int pref, int max) {
            return (SequentialGroup) super.addComponent(component, min, pref, max);
        }

        @Override
        public SequentialGroup addGap(int size) {
            return (SequentialGroup) super.addGap(size);
        }

        @Override
        public SequentialGroup addGap(int min, int pref, int max) {
            return (SequentialGroup) super.addGap(min, pref, max);
        }

        /** Adds the gap the theme prefers between the two components. */
        public SequentialGroup addPreferredGap(JComponent component1, JComponent component2,
                                               LayoutStyle.ComponentPlacement type) {
            return addPreferredGap(component1, component2, type, DEFAULT_SIZE, PREFERRED_SIZE);
        }

        /** Adds the gap the theme prefers between the two components, with explicit sizes. */
        public SequentialGroup addPreferredGap(JComponent component1, JComponent component2,
                                               LayoutStyle.ComponentPlacement type, int pref, int max) {
            if (type == null) {
                throw new IllegalArgumentException("Type must be non-null");
            }
            if (component1 == null || component2 == null) {
                throw new IllegalArgumentException("Components must be non-null");
            }
            checkPreferredGapValues(pref, max);
            return (SequentialGroup) addSpring(new PreferredGapSpring(component1, component2, type, pref, max));
        }

        /** Adds the preferred gap between the nearest components (or nothing if none). */
        public SequentialGroup addPreferredGap(LayoutStyle.ComponentPlacement type) {
            return addPreferredGap(type, DEFAULT_SIZE, DEFAULT_SIZE);
        }

        /**
         * Adds a gap between the nearest components, with an explicit size range; the gap
         * is at least the theme's preferred gap when the two neighbours are found.
         */
        public SequentialGroup addPreferredGap(LayoutStyle.ComponentPlacement type, int pref, int max) {
            if (type != LayoutStyle.ComponentPlacement.RELATED
                    && type != LayoutStyle.ComponentPlacement.UNRELATED) {
                throw new IllegalArgumentException("Type must be one of LayoutStyle.ComponentPlacement.RELATED"
                        + " or LayoutStyle.ComponentPlacement.UNRELATED");
            }
            checkPreferredGapValues(pref, max);
            hasPreferredPaddingSprings = true;
            return (SequentialGroup) addSpring(new AutoGapSpring(type, pref, max));
        }

        /** Adds the theme's gap between the container edge and the components touching it. */
        public SequentialGroup addContainerGap() {
            return addContainerGap(DEFAULT_SIZE, DEFAULT_SIZE);
        }

        /** Adds an edge gap with an explicit size range. */
        public SequentialGroup addContainerGap(int pref, int max) {
            if ((pref < 0 && pref != DEFAULT_SIZE)
                    || (max < 0 && max != DEFAULT_SIZE && max != PREFERRED_SIZE)
                    || (pref >= 0 && max >= 0 && pref > max)) {
                throw new IllegalArgumentException(
                        "Pref and max must be either DEFAULT_SIZE, PREFERRED_SIZE, or >= 0 and pref <= max");
            }
            hasPreferredPaddingSprings = true;
            return (SequentialGroup) addSpring(new ContainerGapSpring(pref, max));
        }

        @Override
        int operator(int a, int b) {
            return constrain(a) + constrain(b);
        }

        @Override
        void setValidSize(int axis, int origin, int size) {
            int pref = getPreferredSize(axis);
            if (size == pref) {
                for (Spring spring : springs) {
                    int springPref = spring.getPreferredSize(axis);
                    spring.setSize(axis, origin, springPref);
                    origin += springPref;
                }
            } else if (springs.size() == 1) {
                Spring spring = getSpring(0);
                spring.setSize(axis, origin,
                        Math.min(Math.max(size, spring.getMinimumSize(axis)), spring.getMaximumSize(axis)));
            } else if (springs.size() > 1) {
                setValidSizeNotPreferred(axis, origin, size);
            }
        }

        /**
         * The JDK's redistribution algorithm: build the list of springs that can grow (or
         * shrink) towards the target size, sort it by how flexible each spring is, hand
         * out an equal share to each, and give whatever a spring could not take back to
         * the remaining ones.
         */
        private void setValidSizeNotPreferred(int axis, int origin, int size) {
            int delta = size - getPreferredSize(axis);
            boolean useMin = (delta < 0);
            int springCount = springs.size();
            if (useMin) {
                delta *= -1;
            }

            List<SpringDelta> resizable = buildResizableList(axis, useMin);
            int resizableCount = resizable.size();

            if (resizableCount > 0) {
                int sDelta = delta / resizableCount;
                int slop = delta - sDelta * resizableCount;
                int[] sizes = new int[springCount];
                int sign = useMin ? -1 : 1;
                for (int counter = 0; counter < resizableCount; counter++) {
                    SpringDelta springDelta = resizable.get(counter);
                    if ((counter + 1) == resizableCount) {
                        sDelta += slop;
                    }
                    springDelta.delta = Math.min(sDelta, springDelta.delta);
                    delta -= springDelta.delta;
                    if (springDelta.delta != sDelta && counter + 1 < resizableCount) {
                        sDelta = delta / (resizableCount - counter - 1);
                        slop = delta - sDelta * (resizableCount - counter - 1);
                    }
                    sizes[springDelta.index] = sign * springDelta.delta;
                }

                for (int counter = 0; counter < springCount; counter++) {
                    Spring spring = getSpring(counter);
                    int sSize = spring.getPreferredSize(axis) + sizes[counter];
                    spring.setSize(axis, origin, sSize);
                    origin += sSize;
                }
            } else {
                // Nothing can change size: use each spring's minimum or maximum.
                for (int counter = 0; counter < springCount; counter++) {
                    Spring spring = getSpring(counter);
                    int sSize = useMin ? spring.getMinimumSize(axis) : spring.getMaximumSize(axis);
                    spring.setSize(axis, origin, sSize);
                    origin += sSize;
                }
            }
        }

        private List<SpringDelta> buildResizableList(int axis, boolean useMin) {
            int size = springs.size();
            List<SpringDelta> sorted = new ArrayList<>(size);
            for (int counter = 0; counter < size; counter++) {
                Spring spring = getSpring(counter);
                int sDelta = useMin
                        ? spring.getPreferredSize(axis) - spring.getMinimumSize(axis)
                        : spring.getMaximumSize(axis) - spring.getPreferredSize(axis);
                if (sDelta > 0) {
                    sorted.add(new SpringDelta(counter, sDelta));
                }
            }
            Collections.sort(sorted);
            return sorted;
        }

        @Override
        void insertAutopadding(int axis, List<AutoGapSpring> leadingPadding,
                List<AutoGapSpring> trailingPadding, List<ComponentSpring> leading,
                List<ComponentSpring> trailing, boolean insert) {
            List<AutoGapSpring> newLeadingPadding = new ArrayList<>(leadingPadding);
            List<AutoGapSpring> newTrailingPadding = new ArrayList<>(1);
            List<ComponentSpring> newLeading = new ArrayList<>(leading);
            List<ComponentSpring> newTrailing = null;
            int counter = 0;
            // springs.size() may change while the loop runs (gaps are inserted).
            while (counter < springs.size()) {
                Spring spring = getSpring(counter);
                if (spring instanceof AutoGapSpring) {
                    if (newLeadingPadding.isEmpty()) {
                        AutoGapSpring padding = (AutoGapSpring) spring;
                        padding.setSources(newLeading);
                        newLeading.clear();
                        counter = indexOfNextNonZeroSpring(counter + 1, true);
                        if (counter == springs.size()) {
                            if (!(padding instanceof ContainerGapSpring)) {
                                trailingPadding.add(padding);
                            }
                        } else {
                            newLeadingPadding.clear();
                            newLeadingPadding.add(padding);
                        }
                    } else {
                        counter = indexOfNextNonZeroSpring(counter + 1, true);
                    }
                } else {
                    if (!newLeading.isEmpty() && newLeadingPadding.isEmpty() && insert) {
                        // Two neighbouring components and no gap between them: insert one.
                        springs.add(counter, new AutoGapSpring());
                        continue;
                    }
                    if (spring instanceof ComponentSpring) {
                        ComponentSpring cSpring = (ComponentSpring) spring;
                        if (!cSpring.isVisible()) {
                            counter++;
                            continue;
                        }
                        for (AutoGapSpring gapSpring : newLeadingPadding) {
                            gapSpring.addTarget(cSpring, axis);
                        }
                        newLeading.clear();
                        newLeadingPadding.clear();
                        counter = indexOfNextNonZeroSpring(counter + 1, false);
                        if (counter == springs.size()) {
                            trailing.add(cSpring);
                        } else {
                            newLeading.add(cSpring);
                        }
                    } else if (spring instanceof Group) {
                        if (newTrailing == null) {
                            newTrailing = new ArrayList<>(1);
                        } else {
                            newTrailing.clear();
                        }
                        newTrailingPadding.clear();
                        ((Group) spring).insertAutopadding(axis, newLeadingPadding, newTrailingPadding,
                                newLeading, newTrailing, insert);
                        newLeading.clear();
                        newLeadingPadding.clear();
                        counter = indexOfNextNonZeroSpring(counter + 1, newTrailing.isEmpty());
                        if (counter == springs.size()) {
                            trailing.addAll(newTrailing);
                            trailingPadding.addAll(newTrailingPadding);
                        } else {
                            newLeading.addAll(newTrailing);
                            newLeadingPadding.addAll(newTrailingPadding);
                        }
                    } else {
                        // A rigid gap; it stands between the components on either side.
                        newLeadingPadding.clear();
                        newLeading.clear();
                        counter++;
                    }
                }
            }
        }
    }

    /**
     * Aligns its children along the cross axis. Its range is the maximum of the children's
     * ranges.
     */
    public class ParallelGroup extends Group {
        private final Alignment childAlignment;
        private final boolean resizable;

        /**
         * Declared by the stub generator; the JDK creates groups only through
         * {@code GroupLayout.createParallelGroup()}.
         */
        public ParallelGroup() {
            this(Alignment.LEADING, true);
        }

        ParallelGroup(Alignment childAlignment, boolean resizable) {
            if (childAlignment == null) {
                throw new IllegalArgumentException("Alignment must be non-null");
            }
            this.childAlignment = childAlignment;
            this.resizable = resizable;
        }

        /** Returns a copy of this group with a different alignment. */
        public ParallelGroup addGroup(Group group) {
            return (ParallelGroup) super.addGroup(group);
        }

        /** Adds a group aligned with {@code alignment} instead of the group's alignment. */
        public ParallelGroup addGroup(Alignment alignment, Group group) {
            checkChildAlignment(alignment);
            group.setAlignment(alignment);
            return (ParallelGroup) super.addGroup(group);
        }

        @Override
        public ParallelGroup addComponent(Component component) {
            return (ParallelGroup) super.addComponent(component);
        }

        @Override
        public ParallelGroup addComponent(Component component, int min, int pref, int max) {
            return (ParallelGroup) super.addComponent(component, min, pref, max);
        }

        /** Adds a component with the given size range and alignment. */
        public ParallelGroup addComponent(Component component, int min, int pref, int max, Alignment alignment) {
            checkChildAlignment(alignment);
            ComponentSpring spring = new ComponentSpring(component, min, pref, max);
            spring.setAlignment(alignment);
            return (ParallelGroup) addSpring(spring);
        }

        /** Adds a component with the given alignment. */
        public ParallelGroup addComponent(Component component, Alignment alignment) {
            return addComponent(component, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE, alignment);
        }

        private void checkChildAlignment(Alignment alignment) {
            if (alignment == null) {
                throw new IllegalArgumentException("Alignment must be non-null");
            }
        }

        boolean isResizable() {
            return resizable;
        }

        @Override
        int operator(int a, int b) {
            return Math.max(a, b);
        }

        @Override
        int calculateMinimumSize(int axis) {
            return resizable ? super.calculateMinimumSize(axis) : getPreferredSize(axis);
        }

        @Override
        int calculateMaximumSize(int axis) {
            return resizable ? super.calculateMaximumSize(axis) : getPreferredSize(axis);
        }

        @Override
        void setValidSize(int axis, int origin, int size) {
            for (Spring spring : springs) {
                setChildSize(spring, axis, origin, size);
            }
        }

        private void setChildSize(Spring spring, int axis, int origin, int size) {
            Alignment alignment = spring.getAlignment();
            int springSize = Math.min(Math.max(spring.getMinimumSize(axis), size), spring.getMaximumSize(axis));
            if (alignment == null) {
                alignment = childAlignment;
            }
            switch (alignment) {
                case TRAILING:
                    spring.setSize(axis, origin + size - springSize, springSize);
                    break;
                case CENTER:
                    spring.setSize(axis, origin + (size - springSize) / 2, springSize);
                    break;
                default:
                    // LEADING, and BASELINE which has no baseline to align to here.
                    spring.setSize(axis, origin, springSize);
                    break;
            }
        }

        @Override
        void insertAutopadding(int axis, List<AutoGapSpring> leadingPadding,
                List<AutoGapSpring> trailingPadding, List<ComponentSpring> leading,
                List<ComponentSpring> trailing, boolean insert) {
            for (Spring spring : springs) {
                if (spring instanceof ComponentSpring) {
                    if (((ComponentSpring) spring).isVisible()) {
                        for (AutoGapSpring gapSpring : leadingPadding) {
                            gapSpring.addTarget((ComponentSpring) spring, axis);
                        }
                        trailing.add((ComponentSpring) spring);
                    }
                } else if (spring instanceof Group) {
                    ((Group) spring).insertAutopadding(axis, leadingPadding, trailingPadding, leading,
                            trailing, insert);
                } else if (spring instanceof AutoGapSpring) {
                    ((AutoGapSpring) spring).setSources(leading);
                    trailingPadding.add((AutoGapSpring) spring);
                }
            }
        }
    }

    /** A spring for one component, whose range comes from the component (or the caller). */
    private class ComponentSpring extends Spring {
        private Component component;
        private final int min;
        private final int pref;
        private final int max;
        private boolean installed;

        ComponentSpring(Component component, int min, int pref, int max) {
            if (component == null) {
                throw new IllegalArgumentException("Component must be non-null");
            }
            checkSize(min, pref, max, true);
            this.component = component;
            this.min = min;
            this.pref = pref;
            this.max = max;
            // Registers the component (and adds it to the container) as the JDK does.
            getComponentInfo(component);
        }

        Component getComponent() {
            return component;
        }

        void setComponent(Component component) {
            this.component = component;
        }

        boolean isVisible() {
            return getComponentInfo(component).isVisible();
        }

        @Override
        int calculateMinimumSize(int axis) {
            return calculateNonlinkedMinimumSize(axis);
        }

        @Override
        int calculatePreferredSize(int axis) {
            int minimum = getMinimumSize(axis);
            int preferred = calculateNonlinkedPreferredSize(axis);
            int maximum = getMaximumSize(axis);
            return Math.min(maximum, Math.max(minimum, preferred));
        }

        @Override
        int calculateMaximumSize(int axis) {
            return Math.max(getMinimumSize(axis), calculateNonlinkedMaximumSize(axis));
        }

        int calculateNonlinkedMinimumSize(int axis) {
            if (!isVisible()) {
                return 0;
            }
            if (min >= 0) {
                return min;
            }
            if (min == PREFERRED_SIZE) {
                return calculateNonlinkedPreferredSize(axis);
            }
            return sizeAlongAxis(axis, component.getMinimumSize());
        }

        int calculateNonlinkedPreferredSize(int axis) {
            if (!isVisible()) {
                return 0;
            }
            if (pref >= 0) {
                return pref;
            }
            return sizeAlongAxis(axis, component.getPreferredSize());
        }

        int calculateNonlinkedMaximumSize(int axis) {
            if (!isVisible()) {
                return 0;
            }
            if (max >= 0) {
                return max;
            }
            if (max == PREFERRED_SIZE) {
                return calculateNonlinkedPreferredSize(axis);
            }
            return sizeAlongAxis(axis, BoxLayout.maximumSizeOf(component));
        }

        private int sizeAlongAxis(int axis, Dimension size) {
            if (size == null) {
                return 0;
            }
            return (axis == SwingConstants.HORIZONTAL) ? size.width : size.height;
        }

        /** Binds this spring to its component info on the given axis. */
        void installIfNecessary(int axis) {
            if (!installed) {
                installed = true;
                ComponentInfo info = getComponentInfo(component);
                if (axis == SwingConstants.HORIZONTAL) {
                    info.horizontalSpring = this;
                } else {
                    info.verticalSpring = this;
                }
            }
        }

        @Override
        boolean willHaveZeroSize(boolean treatAutopaddingAsZeroSized) {
            return !isVisible();
        }
    }

    /** A spring with a fixed size range. */
    private class GapSpring extends Spring {
        private final int min;
        private final int pref;
        private final int max;

        GapSpring(int min, int pref, int max) {
            checkSize(min, pref, max, false);
            this.min = min;
            this.pref = pref;
            this.max = max;
        }

        @Override
        int calculateMinimumSize(int axis) {
            if (min == PREFERRED_SIZE) {
                return getPreferredSize(axis);
            }
            return min;
        }

        @Override
        int calculatePreferredSize(int axis) {
            return pref;
        }

        @Override
        int calculateMaximumSize(int axis) {
            if (max == PREFERRED_SIZE) {
                return getPreferredSize(axis);
            }
            return max;
        }

        @Override
        boolean willHaveZeroSize(boolean treatAutopaddingAsZeroSized) {
            return false;
        }
    }

    /** The theme's preferred distance between two named components. */
    private class PreferredGapSpring extends Spring {
        private final JComponent source;
        private final JComponent target;
        private final LayoutStyle.ComponentPlacement type;
        private final int pref;
        private final int max;

        PreferredGapSpring(JComponent source, JComponent target, LayoutStyle.ComponentPlacement type, int pref,
                           int max) {
            this.source = source;
            this.target = target;
            this.type = type;
            this.pref = pref;
            this.max = max;
        }

        @Override
        int calculateMinimumSize(int axis) {
            return getPadding(axis);
        }

        @Override
        int calculatePreferredSize(int axis) {
            if (pref == DEFAULT_SIZE || pref == PREFERRED_SIZE) {
                return getMinimumSize(axis);
            }
            int min = getMinimumSize(axis);
            int max = getMaximumSize(axis);
            return Math.min(max, Math.max(min, pref));
        }

        @Override
        int calculateMaximumSize(int axis) {
            if (max == PREFERRED_SIZE || max == DEFAULT_SIZE) {
                return getPadding(axis);
            }
            return Math.max(getMinimumSize(axis), max);
        }

        private int getPadding(int axis) {
            int position = (axis == SwingConstants.HORIZONTAL) ? SwingConstants.EAST : SwingConstants.SOUTH;
            return getLayoutStyle0().getPreferredGap(source, target, type, position, host);
        }

        @Override
        boolean willHaveZeroSize(boolean treatAutopaddingAsZeroSized) {
            return false;
        }
    }

    /**
     * A gap whose size is worked out from the components that end up next to it, either
     * because the developer asked for the preferred gap between the nearest components or
     * because {@code setAutoCreateGaps(true)} is on.
     */
    private class AutoGapSpring extends Spring {
        List<ComponentSpring> sources;
        ComponentSpring source;
        private List<ComponentSpring[]> matches;
        /** The gap size the last {@link #calculatePadding(int)} pass arrived at. */
        int padding;
        int lastSize;
        private final int pref;
        private final int max;
        private LayoutStyle.ComponentPlacement type;
        boolean userCreated;

        AutoGapSpring() {
            this.pref = PREFERRED_SIZE;
            this.max = PREFERRED_SIZE;
            this.type = LayoutStyle.ComponentPlacement.RELATED;
        }

        AutoGapSpring(LayoutStyle.ComponentPlacement type, int pref, int max) {
            this.type = type;
            this.pref = pref;
            this.max = max;
            this.userCreated = true;
        }

        void setSource(ComponentSpring source) {
            this.source = source;
        }

        void setSources(List<ComponentSpring> sources) {
            this.sources = new ArrayList<>(sources);
        }

        boolean getUserCreated() {
            return userCreated;
        }

        @Override
        void unset() {
            lastSize = getSize();
            super.unset();
            padding = 0;
        }

        void reset() {
            padding = 0;
            sources = null;
            source = null;
            matches = null;
        }

        /** Works the gap size out from the origins the last layout pass assigned. */
        void calculatePadding(int axis) {
            padding = 0;
            int maxPadding = 0;
            if (matches != null) {
                LayoutStyle style = getLayoutStyle0();
                int position;
                if (axis == SwingConstants.HORIZONTAL) {
                    position = isLeftToRight() ? SwingConstants.EAST : SwingConstants.WEST;
                } else {
                    position = SwingConstants.SOUTH;
                }
                for (int i = matches.size() - 1; i >= 0; i--) {
                    ComponentSpring[] match = matches.get(i);
                    int padding2 = calculatePadding(style, position, match[0], match[1]);
                    maxPadding = Math.max(maxPadding, padding2);
                }
            }
            if (lastSize != UNSET) {
                padding += Math.min(maxPadding, lastSize);
            }
        }

        private int calculatePadding(LayoutStyle style, int position, ComponentSpring gapSource,
                                     ComponentSpring gapTarget) {
            int delta = gapTarget.getOrigin() - (gapSource.getOrigin() + gapSource.getSize());
            if (delta >= 0) {
                int p;
                if ((gapSource.getComponent() instanceof JComponent)
                        && (gapTarget.getComponent() instanceof JComponent)) {
                    p = style.getPreferredGap((JComponent) gapSource.getComponent(),
                            (JComponent) gapTarget.getComponent(), type, position, host);
                } else {
                    p = 10;
                }
                if (p > delta) {
                    padding = Math.max(padding, p - delta);
                }
                return p;
            }
            return 0;
        }

        /** Records that {@code spring} follows this gap along {@code axis}. */
        void addTarget(ComponentSpring spring, int axis) {
            int otherAxis = (axis == SwingConstants.HORIZONTAL)
                    ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL;
            if (source != null) {
                if (areParallelSiblings(source.getComponent(), spring.getComponent(), otherAxis)) {
                    addValidTarget(source, spring);
                }
            } else if (sources != null) {
                Component component = spring.getComponent();
                for (int counter = sources.size() - 1; counter >= 0; counter--) {
                    ComponentSpring gapSource = sources.get(counter);
                    if (areParallelSiblings(gapSource.getComponent(), component, otherAxis)) {
                        addValidTarget(gapSource, spring);
                    }
                }
            }
        }

        private void addValidTarget(ComponentSpring gapSource, ComponentSpring gapTarget) {
            if (matches == null) {
                matches = new ArrayList<>(1);
            }
            matches.add(new ComponentSpring[] {gapSource, gapTarget});
        }

        @Override
        int calculateMinimumSize(int axis) {
            return padding;
        }

        @Override
        int calculatePreferredSize(int axis) {
            if (pref == PREFERRED_SIZE || pref == DEFAULT_SIZE) {
                return padding;
            }
            return Math.max(padding, pref);
        }

        @Override
        int calculateMaximumSize(int axis) {
            if (max >= 0) {
                return Math.max(getPreferredSize(axis), max);
            }
            return padding;
        }

        @Override
        boolean willHaveZeroSize(boolean treatAutopaddingAsZeroSized) {
            return treatAutopaddingAsZeroSized;
        }
    }

    /** The gap between a container edge and the components touching it. */
    private class ContainerGapSpring extends AutoGapSpring {
        private List<ComponentSpring> targets;

        ContainerGapSpring() {
            super();
            userCreated = true;
        }

        ContainerGapSpring(int pref, int max) {
            super(LayoutStyle.ComponentPlacement.RELATED, pref, max);
            userCreated = true;
        }

        @Override
        void addTarget(ComponentSpring spring, int axis) {
            if (targets == null) {
                targets = new ArrayList<>(1);
            }
            targets.add(spring);
        }

        @Override
        void calculatePadding(int axis) {
            LayoutStyle style = getLayoutStyle0();
            int maxPadding = 0;
            padding = 0;
            if (targets != null) {
                // Leading edge: the gap pushes the first components inwards.
                int position;
                if (axis == SwingConstants.HORIZONTAL) {
                    position = isLeftToRight() ? SwingConstants.WEST : SwingConstants.EAST;
                } else {
                    position = SwingConstants.SOUTH;
                }
                for (int i = targets.size() - 1; i >= 0; i--) {
                    ComponentSpring targetSpring = targets.get(i);
                    int p = 10;
                    if (targetSpring.getComponent() instanceof JComponent) {
                        p = style.getContainerGap((JComponent) targetSpring.getComponent(), position, host);
                        maxPadding = Math.max(p, maxPadding);
                        p -= targetSpring.getOrigin();
                    } else {
                        maxPadding = Math.max(p, maxPadding);
                    }
                    padding = Math.max(padding, p);
                }
            } else {
                // Trailing edge: the gap fills whatever the last components left over.
                int position;
                if (axis == SwingConstants.HORIZONTAL) {
                    position = isLeftToRight() ? SwingConstants.EAST : SwingConstants.WEST;
                } else {
                    position = SwingConstants.SOUTH;
                }
                if (sources != null) {
                    for (int i = sources.size() - 1; i >= 0; i--) {
                        maxPadding = Math.max(maxPadding, updateSize(style, sources.get(i), position));
                    }
                } else if (source != null) {
                    maxPadding = updateSize(style, source, position);
                }
            }
            if (lastSize != UNSET) {
                padding += Math.min(maxPadding, lastSize);
            }
        }

        private int updateSize(LayoutStyle style, ComponentSpring sourceSpring, int position) {
            int p = 10;
            if (sourceSpring.getComponent() instanceof JComponent) {
                p = style.getContainerGap((JComponent) sourceSpring.getComponent(), position, host);
            }
            int delta = Math.max(0, getParent().getSize() - sourceSpring.getSize() - sourceSpring.getOrigin());
            padding = Math.max(padding, p - delta);
            return p;
        }
    }

    /** Per-component bookkeeping: springs per axis, cached visibility, linked sizes. */
    private final class ComponentInfo {
        private Component component;
        ComponentSpring horizontalSpring;
        ComponentSpring verticalSpring;
        private boolean visible;
        private Boolean honorsVisibility;

        ComponentInfo(Component component) {
            this.component = component;
            refreshVisibility();
        }

        void dispose() {
            removeSpring(horizontalSpring);
            horizontalSpring = null;
            removeSpring(verticalSpring);
            verticalSpring = null;
        }

        void setHonorsVisibility(Boolean honorsVisibility) {
            this.honorsVisibility = honorsVisibility;
        }

        Boolean getHonorsVisibility() {
            return honorsVisibility;
        }

        private void removeSpring(Spring spring) {
            if (spring != null) {
                ((Group) spring.getParent()).springs.remove(spring);
            }
        }

        boolean isVisible() {
            return visible;
        }

        /** Refreshes the cached visibility from the component and the visibility policy. */
        void refreshVisibility() {
            boolean honor;
            if (this.honorsVisibility == null) {
                honor = GroupLayout.this.getHonorsVisibility();
            } else {
                honor = this.honorsVisibility;
            }
            visible = honor ? component.isVisible() : true;
        }

        void clearCachedSize() {
            if (horizontalSpring != null) {
                horizontalSpring.unset();
            }
            if (verticalSpring != null) {
                verticalSpring.unset();
            }
        }

        void setBounds(Insets insets, int parentWidth, boolean ltr) {
            int x = horizontalSpring.getOrigin();
            int w = horizontalSpring.getSize();
            int y = verticalSpring.getOrigin();
            int h = verticalSpring.getSize();

            if (!ltr) {
                x = parentWidth - x - w;
            }
            component.setBounds(x + insets.left, y + insets.top, w, h);
        }

        Component getComponent() {
            return component;
        }
    }

    /** One spring's flexibility, used to sort the list the resizer walks. */
    private static final class SpringDelta implements Comparable<SpringDelta> {
        final int index;
        int delta;

        SpringDelta(int index, int delta) {
            this.index = index;
            this.delta = delta;
        }

        @Override
        public int compareTo(SpringDelta o) {
            return delta - o.delta;
        }
    }
}
