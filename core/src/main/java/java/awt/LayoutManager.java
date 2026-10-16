package java.awt;

/**
 * AWT {@code LayoutManager}: lays out a container's children.
 *
 * <p>Real interface — the port's Swing runtime runs the JDK algorithms
 * ({@code BorderLayout}, {@code GridBagLayout}, {@code BoxLayout}, …), so the four
 * JDK methods must exist and be implemented by those managers.
 */
public interface LayoutManager {
    void addLayoutComponent(String name, Component comp);

    void removeLayoutComponent(Component comp);

    Dimension preferredLayoutSize(Container parent);

    Dimension minimumLayoutSize(Container parent);

    void layoutContainer(Container parent);
}
