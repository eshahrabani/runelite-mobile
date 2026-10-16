package javax.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * {@code Box}: a lightweight container that uses a {@link BoxLayout}.
 *
 * <p>Hand-written because the Android runtime ships no {@code java.desktop} and the port's
 * sidebar really uses boxes: RuneLite stacks its panels with
 * {@code Box.createVerticalBox()}, spreads toolbars out with
 * {@code Box.createHorizontalGlue()} and spaces rows with struts and rigid areas. The
 * earlier port version made every factory method return a rigid, zero-sized component, so
 * glue could not absorb surplus space and struts were invisible in the layout.
 *
 * <p>Each factory creates a real {@link Filler} whose minimum/preferred/maximum ranges
 * follow the JDK exactly:
 * <ul>
 * <li>glue has zero minimum and preferred size and grows along its axis only
 *     ({@code createGlue} grows in both directions, {@code createHorizontalGlue} only
 *     horizontally, {@code createVerticalGlue} only vertically);</li>
 * <li>a strut is fixed along the axis it was created for but may stretch across it, which
 *     is the JDK's documented behaviour ({@code createHorizontalStrut(width)} has a maximum
 *     of {@code (width, Short.MAX_VALUE)});</li>
 * <li>a rigid area is fixed in both directions.</li>
 * </ul>
 *
 * <p>Port deviations:
 * <ul>
 * <li>{@code createRigidArea} gives each range its own {@code Dimension} rather than
 *     handing the same instance to all three as the JDK does, so neither the caller's
 *     argument nor the object returned by one getter can be mutated into corrupting the
 *     other ranges. The values are the JDK's.</li>
 * <li>{@code setLayout} is not overridden to throw {@code AWTError} the way the JDK's
 *     {@code Box} does; a layout can be replaced on a box in this port.</li>
 * <li>{@code getAlignmentX()/getAlignmentY()} of a reference component are read through
 *     {@link BoxLayout} because this port's {@code java.awt.Component} has no alignment
 *     accessors.</li>
 * </ul>
 */
public class Box extends JComponent {

    /** Creates a box that lays its children out along {@code axis}. */
    public Box(int axis) {
        super();
        super.setLayout(new BoxLayout(this, axis));
    }

    /** Creates a box whose children are laid out horizontally. */
    public static Box createHorizontalBox() {
        return new Box(BoxLayout.X_AXIS);
    }

    /** Creates a box whose children are laid out vertically. */
    public static Box createVerticalBox() {
        return new Box(BoxLayout.Y_AXIS);
    }

    /**
     * Creates an invisible component with a fixed width that can grow vertically, the JDK's
     * "force a gap of {@code width} pixels" component.
     */
    public static Component createHorizontalStrut(int width) {
        return new Filler(new Dimension(width, 0), new Dimension(width, 0),
                new Dimension(width, Short.MAX_VALUE));
    }

    /**
     * Creates an invisible component with a fixed height that can grow horizontally, the
     * JDK's "force a gap of {@code height} pixels" component.
     */
    public static Component createVerticalStrut(int height) {
        return new Filler(new Dimension(0, height), new Dimension(0, height),
                new Dimension(Short.MAX_VALUE, height));
    }

    /**
     * Creates an invisible component that absorbs surplus space along both axes.
     */
    public static Component createGlue() {
        return new Filler(new Dimension(0, 0), new Dimension(0, 0),
                new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
    }

    /** Creates an invisible component that absorbs surplus horizontal space. */
    public static Component createHorizontalGlue() {
        return new Filler(new Dimension(0, 0), new Dimension(0, 0),
                new Dimension(Short.MAX_VALUE, 0));
    }

    /** Creates an invisible component that absorbs surplus vertical space. */
    public static Component createVerticalGlue() {
        return new Filler(new Dimension(0, 0), new Dimension(0, 0),
                new Dimension(0, Short.MAX_VALUE));
    }

    /** Creates an invisible component with the given fixed size. */
    public static Component createRigidArea(Dimension d) {
        return new Filler(new Dimension(d.width, d.height), new Dimension(d.width, d.height),
                new Dimension(d.width, d.height));
    }

    /**
     * An invisible component that participates in layout: its size range is the one it was
     * created with, and it paints nothing unless it is made opaque.
     */
    public static class Filler extends JComponent {

        /** Creates a filler with the given size ranges. */
        public Filler(Dimension min, Dimension pref, Dimension max) {
            setShape(min, pref, max);
            setFocusable(false);
        }

        /**
         * Creates a filler with the given size ranges and the alignments of
         * {@code ref} (the JDK's variant used to keep a filler aligned with a component).
         */
        public Filler(Component ref, Dimension min, Dimension pref, Dimension max) {
            this(min, pref, max);
            if (ref != null) {
                setAlignmentX(BoxLayout.alignmentX(ref));
                setAlignmentY(BoxLayout.alignmentY(ref));
            }
        }

        /** Replaces the size ranges and invalidates the enclosing layout. */
        public void changeShape(Dimension min, Dimension pref, Dimension max) {
            setShape(min, pref, max);
            revalidate();
        }

        /**
         * Stores the three ranges the way the JDK's filler does (through
         * {@code JComponent}'s setters, so they count as "explicitly set" and no UI or
         * component default can override them), but with one {@code Dimension} per range
         * instead of the single shared instance the JDK passes for a rigid area.
         */
        private void setShape(Dimension min, Dimension pref, Dimension max) {
            setMinimumSize(copy(min));
            setPreferredSize(copy(pref));
            setMaximumSize(copy(max));
        }

        private static Dimension copy(Dimension d) {
            return (d == null) ? null : new Dimension(d.width, d.height);
        }

        /**
         * A filler has no view: it paints its background only when it has been made
         * opaque, and honours a UI if one was installed.
         */
        @Override
        protected void paintComponent(Graphics g) {
            if (getUI() != null) {
                super.paintComponent(g);
            } else if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}
