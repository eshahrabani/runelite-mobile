package javax.swing;

import java.awt.Component;
import java.awt.Graphics;

/**
 * Swing {@code Icon}: a fixed-size paint routine (the RuneLite sidebar is built from
 * these — one icon per plugin plus the toolbar glyphs).
 */
public interface Icon {
    void paintIcon(Component c, Graphics g, int x, int y);

    int getIconWidth();

    int getIconHeight();
}
