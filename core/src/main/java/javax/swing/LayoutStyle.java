package javax.swing;

import java.awt.Container;

/**
 * {@code LayoutStyle} supplies the theme's preferred gaps between components, the values
 * {@link GroupLayout} and {@link BoxLayout}-style hand-written panels use when they space
 * things out.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop}. The
 * generated data-only stub declared only the {@code ComponentPlacement} enum, which meant
 * every {@code addPreferredGap(...)} call reached a method that returned {@code null}/
 * zero — GroupLayout needs a real value here or it cannot solve its springs.
 *
 * <p>Port deviation: the JDK's {@code getInstance()} falls back to
 * {@code UIManager.getLookAndFeel().getLayoutStyle()}; this port's {@code LookAndFeel}
 * has no {@code getLayoutStyle()}, so when no instance has been set explicitly
 * {@link #getInstance()} returns this port's fixed dark-theme style instead of consulting
 * the look and feel.
 *
 * <p>The values are the port theme's (RELATED = 6, UNRELATED = 12, INDENT = 6) and the
 * {@code getContainerGap} default is the JDK's cross-platform value of 12 pixels for
 * every edge and component. The JDK's finer rules (a label gets an extra 6 pixels of gap
 * before the component it labels, and {@code INDENT} is 12 for a horizontal position) are
 * not reproduced: no inspection of the components happens at all, which also makes a
 * {@code null} component argument (the JDK's own state for a gap that has no anchor yet)
 * harmless instead of an exception.
 */
public abstract class LayoutStyle {

    /** How two components are related, which selects the gap size. */
    public enum ComponentPlacement {
        /** The components belong to the same logical group (6 pixels). */
        RELATED,
        /** The components are independent (12 pixels). */
        UNRELATED,
        /** The second component is a sub-item of the first (6 pixels). */
        INDENT
    }

    /**
     * Port-friendly alias of {@link ComponentPlacement} with the same three values; the
     * port's own code uses {@code Kind} so it does not have to spell out the JDK's
     * verbose enum name.
     */
    public enum Kind {
        RELATED,
        UNRELATED,
        INDENT
    }

    /** Gap in pixels between two related components. */
    public static final int RELATED = 6;

    /** Gap in pixels between two unrelated components. */
    public static final int UNRELATED = 12;

    /** Gap in pixels between a component and its indented sub-item. */
    public static final int INDENT = 6;

    private static LayoutStyle sharedInstance;

    /** The port theme's fixed style; used when nothing has been set explicitly. */
    private static final LayoutStyle DEFAULT = new LayoutStyle() {
        @Override
        public int getPreferredGap(JComponent component1, JComponent component2, ComponentPlacement type,
                                   int position, Container parent) {
            return gapFor(type);
        }

        @Override
        public int getContainerGap(JComponent component, int position, Container parent) {
            // The JDK's cross-platform style returns 12 for every edge and component.
            return UNRELATED;
        }
    };

    public LayoutStyle() {
    }

    /**
     * Sets the shared instance.
     *
     * @param style the style to use; {@code null} restores the port's default theme style
     */
    public static void setInstance(LayoutStyle style) {
        synchronized (LayoutStyle.class) {
            sharedInstance = style;
        }
    }

    /**
     * Returns the shared instance, or this port's default theme style when none has been
     * set (see the class note for why the look and feel is not consulted).
     */
    public static LayoutStyle getInstance() {
        synchronized (LayoutStyle.class) {
            if (sharedInstance != null) {
                return sharedInstance;
            }
        }
        return DEFAULT;
    }

    /**
     * The gap in pixels between two components.
     *
     * @param component1 the leading component, may be {@code null}
     * @param component2 the trailing component, may be {@code null}
     * @param type       the relation between the two
     * @param position   the side to place the gap on ({@code SwingConstants.EAST} or
     *                   {@code SOUTH}); unused by this style
     * @param parent     the container being laid out
     */
    public abstract int getPreferredGap(JComponent component1, JComponent component2, ComponentPlacement type,
                                        int position, Container parent);

    /**
     * The gap in pixels between a component and the edge of its container.
     *
     * @param component the component touching the edge, may be {@code null}
     * @param position  the edge ({@code SwingConstants.WEST}/{@code EAST}/{@code NORTH}/
     *                  {@code SOUTH}); unused by this style
     * @param parent    the container being laid out
     */
    public abstract int getContainerGap(JComponent component, int position, Container parent);

    /** Convenience overload used by the port's own layout code. */
    public int getPreferredGap(ComponentPlacement type) {
        return gapFor(type);
    }

    /** Convenience overload used by the port's own layout code. */
    public int getPreferredGap(Kind kind) {
        return gapFor(kind);
    }

    /** Convenience overload used by the port's own layout code. */
    public int getContainerGap(ComponentPlacement type) {
        return gapFor(type);
    }

    /** Convenience overload used by the port's own layout code. */
    public int getContainerGap(Kind kind) {
        return gapFor(kind);
    }

    private static int gapFor(ComponentPlacement type) {
        if (type == ComponentPlacement.UNRELATED) {
            return UNRELATED;
        }
        if (type == ComponentPlacement.INDENT) {
            return INDENT;
        }
        return RELATED;
    }

    private static int gapFor(Kind kind) {
        if (kind == Kind.UNRELATED) {
            return UNRELATED;
        }
        if (kind == Kind.INDENT) {
            return INDENT;
        }
        return RELATED;
    }
}
