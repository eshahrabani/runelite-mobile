package javax.swing;

/**
 * Swing {@code ScrollPaneConstants}: the layout keys {@link JScrollPane} uses for its nine
 * children and the scroll-bar display policies.
 *
 * <p>Hand-written rather than generated: the shipped jars reference these constants
 * directly (e.g. {@code PluginPanel} passes {@code HORIZONTAL_SCROLLBAR_NEVER} to
 * {@code setHorizontalScrollBarPolicy}), so the values have to be the JDK's — a data-only
 * file with no constants would have linked but silently changed the policies. The marker
 * sentence is removed so the generator leaves this file alone.
 *
 * <p>Port deviation: none. The values are the JDK's.
 */
public interface ScrollPaneConstants {

    /** Identifies the viewport: the display area the scrolled contents are visible in. */
    String VIEWPORT = "VIEWPORT";

    /** Identifies the vertical scroll bar. */
    String VERTICAL_SCROLLBAR = "VERTICAL_SCROLLBAR";

    /** Identifies the horizontal scroll bar. */
    String HORIZONTAL_SCROLLBAR = "HORIZONTAL_SCROLLBAR";

    /** Identifies the row header (left of the viewport, between the upper/lower left corners). */
    String ROW_HEADER = "ROW_HEADER";

    /** Identifies the column header (above the viewport, between the upper corners). */
    String COLUMN_HEADER = "COLUMN_HEADER";

    /** Identifies the lower left corner of the viewport. */
    String LOWER_LEFT_CORNER = "LOWER_LEFT_CORNER";

    /** Identifies the lower right corner of the viewport. */
    String LOWER_RIGHT_CORNER = "LOWER_RIGHT_CORNER";

    /** Identifies the upper left corner of the viewport. */
    String UPPER_LEFT_CORNER = "UPPER_LEFT_CORNER";

    /** Identifies the upper right corner of the viewport. */
    String UPPER_RIGHT_CORNER = "UPPER_RIGHT_CORNER";

    /** Identifies the lower leading edge corner (the left one on this port, which is LTR-only). */
    String LOWER_LEADING_CORNER = "LOWER_LEADING_CORNER";

    /** Identifies the lower trailing edge corner (the right one on this port). */
    String LOWER_TRAILING_CORNER = "LOWER_TRAILING_CORNER";

    /** Identifies the upper leading edge corner (the left one on this port). */
    String UPPER_LEADING_CORNER = "UPPER_LEADING_CORNER";

    /** Identifies the upper trailing edge corner (the right one on this port). */
    String UPPER_TRAILING_CORNER = "UPPER_TRAILING_CORNER";

    /** Identifies the vertical scroll bar policy property. */
    String VERTICAL_SCROLLBAR_POLICY = "VERTICAL_SCROLLBAR_POLICY";

    /** Identifies the horizontal scroll bar policy property. */
    String HORIZONTAL_SCROLLBAR_POLICY = "HORIZONTAL_SCROLLBAR_POLICY";

    /** The vertical scroll bar is displayed only when the view is taller than the viewport. */
    int VERTICAL_SCROLLBAR_AS_NEEDED = 20;

    /** The vertical scroll bar is never displayed. */
    int VERTICAL_SCROLLBAR_NEVER = 21;

    /** The vertical scroll bar is always displayed. */
    int VERTICAL_SCROLLBAR_ALWAYS = 22;

    /** The horizontal scroll bar is displayed only when the view is wider than the viewport. */
    int HORIZONTAL_SCROLLBAR_AS_NEEDED = 30;

    /** The horizontal scroll bar is never displayed. */
    int HORIZONTAL_SCROLLBAR_NEVER = 31;

    /** The horizontal scroll bar is always displayed. */
    int HORIZONTAL_SCROLLBAR_ALWAYS = 32;
}
