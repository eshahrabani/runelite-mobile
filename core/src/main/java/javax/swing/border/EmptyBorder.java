package javax.swing.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/**
 * Swing {@code EmptyBorder}: transparent space around a component. RuneLite uses it for
 * every "add some padding" case, so it must report insets and paint nothing.
 */
public class EmptyBorder extends AbstractBorder {
    protected int left;
    protected int right;
    protected int top;
    protected int bottom;

    public EmptyBorder(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public EmptyBorder(Insets borderInsets) {
        this(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(top, left, bottom, right);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(top, left, bottom, right);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
