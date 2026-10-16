package javax.swing.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

/** Swing {@code TitledBorder}: a titled frame; the port draws the title line over the border. */
public class TitledBorder extends AbstractBorder {
    public static final int DEFAULT_POSITION = 0;
    public static final int ABOVE_TOP = 1;
    public static final int TOP = 2;
    public static final int BELOW_TOP = 3;
    public static final int ABOVE_BOTTOM = 4;
    public static final int BOTTOM = 5;
    public static final int BELOW_BOTTOM = 6;

    public static final int DEFAULT_JUSTIFICATION = 0;
    public static final int LEFT = 1;
    public static final int CENTER = 2;
    public static final int RIGHT = 3;
    public static final int LEADING = 4;
    public static final int TRAILING = 5;

    private String title = "";
    private Border border;
    private int titlePosition = DEFAULT_POSITION;
    private int titleJustification = DEFAULT_JUSTIFICATION;
    private Font titleFont;
    private Color titleColor;

    public TitledBorder(String title) {
        this.title = title;
    }

    public TitledBorder(Border border) {
        this.border = border;
    }

    public TitledBorder(Border border, String title) {
        this.border = border;
        this.title = title;
    }

    public TitledBorder(Border border, String title, int titleJustification, int titlePosition) {
        this(border, title);
        this.titleJustification = titleJustification;
        this.titlePosition = titlePosition;
    }

    public TitledBorder(Border border, String title, int titleJustification, int titlePosition, Font titleFont) {
        this(border, title, titleJustification, titlePosition);
        this.titleFont = titleFont;
    }

    public TitledBorder(Border border, String title, int titleJustification, int titlePosition,
                        Font titleFont, Color titleColor) {
        this(border, title, titleJustification, titlePosition, titleFont);
        this.titleColor = titleColor;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Border getBorder() {
        return border;
    }

    public void setBorder(Border border) {
        this.border = border;
    }

    public int getTitlePosition() {
        return titlePosition;
    }

    public void setTitlePosition(int titlePosition) {
        this.titlePosition = titlePosition;
    }

    public int getTitleJustification() {
        return titleJustification;
    }

    public void setTitleJustification(int titleJustification) {
        this.titleJustification = titleJustification;
    }

    public Font getTitleFont() {
        return titleFont;
    }

    public void setTitleFont(Font titleFont) {
        this.titleFont = titleFont;
    }

    public Color getTitleColor() {
        return titleColor;
    }

    public void setTitleColor(Color titleColor) {
        this.titleColor = titleColor;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (border != null) {
            border.paintBorder(c, g, x, y, width, height);
        }
        if (title == null || title.isEmpty()) {
            return;
        }
        Font font = titleFont != null ? titleFont : (c != null ? c.getFont() : null);
        if (font == null) {
            font = new Font("Dialog", Font.PLAIN, 12);
        }
        Font oldFont = g.getFont();
        Color oldColor = g.getColor();
        g.setFont(font);
        Color color = titleColor;
        if (color == null) {
            color = c != null ? c.getForeground() : Color.WHITE;
        }
        if (color != null) {
            g.setColor(color);
        }
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(title);
        int tx = x + 5;
        if (titleJustification == CENTER) {
            tx = x + (width - textWidth) / 2;
        } else if (titleJustification == RIGHT || titleJustification == TRAILING) {
            tx = x + width - textWidth - 5;
        }
        g.drawString(title, tx, y + fm.getAscent());
        g.setFont(oldFont);
        g.setColor(oldColor);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        Insets insets = border == null ? new Insets(0, 0, 0, 0) : border.getBorderInsets(c);
        Font font = c != null ? c.getFont() : titleFont;
        int height = font != null ? font.getSize() + 4 : 16;
        return new Insets(insets.top + height, insets.left, insets.bottom, insets.right);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        Insets computed = getBorderInsets(c);
        insets.set(computed.top, computed.left, computed.bottom, computed.right);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return border != null && border.isBorderOpaque();
    }
}
