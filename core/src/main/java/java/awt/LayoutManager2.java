package java.awt;

/** AWT {@code LayoutManager2}: a layout manager that takes constraints per component. */
public interface LayoutManager2 extends LayoutManager {
    void addLayoutComponent(Component comp, Object constraints);

    Dimension maximumLayoutSize(Container target);

    float getLayoutAlignmentX(Container target);

    float getLayoutAlignmentY(Container target);

    void invalidateLayout(Container target);
}
