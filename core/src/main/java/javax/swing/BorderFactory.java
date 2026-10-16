package javax.swing;

import java.awt.Color;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;

/**
 * Swing {@code BorderFactory}: the factories RuneLite's panels use.
 *
 * <p>The shared instances exist too ({@code createEmptyBorder()} returns the same object
 * every time), because the configuration UI compares borders for equality.
 */
public class BorderFactory {
    private static Border emptyBorder;
    private static Border lineBorder;
    private static Border raisedBevelBorder;
    private static Border loweredBevelBorder;

    private BorderFactory() {
    }

    public static Border createEmptyBorder() {
        if (emptyBorder == null) {
            emptyBorder = new EmptyBorder(0, 0, 0, 0);
        }
        return emptyBorder;
    }

    public static Border createEmptyBorder(int top, int left, int bottom, int right) {
        if (top == 0 && left == 0 && bottom == 0 && right == 0) {
            return createEmptyBorder();
        }
        return new EmptyBorder(top, left, bottom, right);
    }

    public static Border createLineBorder(Color color) {
        return new LineBorder(color, 1);
    }

    public static Border createLineBorder(Color color, int thickness) {
        return new LineBorder(color, thickness);
    }

    public static Border createLineBorder(Color color, int thickness, boolean rounded) {
        return new LineBorder(color, thickness, rounded);
    }

    public static Border createBlackLineBorder() {
        return LineBorder.createBlackLineBorder();
    }

    public static Border createGrayLineBorder() {
        return LineBorder.createGrayLineBorder();
    }

    public static Border createEtchedBorder() {
        return new EtchedBorder();
    }

    public static Border createEtchedBorder(Color highlight, Color shadow) {
        return new EtchedBorder(highlight, shadow);
    }

    public static Border createEtchedBorder(int type) {
        return new EtchedBorder(type);
    }

    public static Border createBevelBorder(int type) {
        return type == BevelBorder.RAISED ? createRaisedBevelBorder() : createLoweredBevelBorder();
    }

    public static Border createBevelBorder(int type, Color highlight, Color shadow) {
        return new BevelBorder(type, highlight, shadow);
    }

    public static Border createRaisedBevelBorder() {
        if (raisedBevelBorder == null) {
            raisedBevelBorder = new BevelBorder(BevelBorder.RAISED);
        }
        return raisedBevelBorder;
    }

    public static Border createLoweredBevelBorder() {
        if (loweredBevelBorder == null) {
            loweredBevelBorder = new BevelBorder(BevelBorder.LOWERED);
        }
        return loweredBevelBorder;
    }

    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, Color color) {
        return new MatteBorder(top, left, bottom, right, color);
    }

    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, Icon tileIcon) {
        return new MatteBorder(top, left, bottom, right, tileIcon);
    }

    public static CompoundBorder createCompoundBorder() {
        return new CompoundBorder();
    }

    public static CompoundBorder createCompoundBorder(Border outsideBorder, Border insideBorder) {
        return new CompoundBorder(outsideBorder, insideBorder);
    }

    public static TitledBorder createTitledBorder(String title) {
        return new TitledBorder(title);
    }

    public static TitledBorder createTitledBorder(Border border) {
        return new TitledBorder(border);
    }

    public static TitledBorder createTitledBorder(Border border, String title) {
        return new TitledBorder(border, title);
    }

    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification,
                                                  int titlePosition) {
        return new TitledBorder(border, title, titleJustification, titlePosition);
    }

    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification,
                                                  int titlePosition, java.awt.Font titleFont) {
        return new TitledBorder(border, title, titleJustification, titlePosition, titleFont);
    }

    public static TitledBorder createTitledBorder(Border border, String title, int titleJustification,
                                                  int titlePosition, java.awt.Font titleFont, Color titleColor) {
        return new TitledBorder(border, title, titleJustification, titlePosition, titleFont, titleColor);
    }
}
