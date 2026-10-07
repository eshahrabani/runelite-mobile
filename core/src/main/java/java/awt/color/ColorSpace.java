package java.awt.color;

/**
 * AWT ColorSpace compatibility stub for Android runtime.
 *
 * <p>Only the identity of the colour space matters to the overlay surface:
 * {@link #getInstance(int)} returns a singleton for sRGB (and the other
 * standard ids) so {@code DirectColorModel}'s colour-space constructor can
 * record which space its masks describe. No conversion is implemented.
 */
public class ColorSpace {
    public static final int TYPE_XYZ = 0;
    public static final int TYPE_Lab = 1;
    public static final int TYPE_Luv = 2;
    public static final int TYPE_YCbCr = 3;
    public static final int TYPE_Yxy = 4;
    public static final int TYPE_RGB = 5;
    public static final int TYPE_GRAY = 6;
    public static final int TYPE_HSV = 7;
    public static final int TYPE_HLS = 8;
    public static final int TYPE_CMYK = 9;
    public static final int TYPE_CMY = 11;
    public static final int TYPE_2CLR = 12;

    public static final int CS_sRGB = 1000;
    public static final int CS_CIEXYZ = 1001;
    public static final int CS_PYCC = 1002;
    public static final int CS_GRAY = 1003;
    public static final int CS_LINEAR_RGB = 1004;

    private static final ColorSpace SRGB = new ColorSpace(TYPE_RGB, 3, CS_sRGB);
    private static final ColorSpace GRAY = new ColorSpace(TYPE_GRAY, 1, CS_GRAY);
    private static final ColorSpace LINEAR_RGB = new ColorSpace(TYPE_RGB, 3, CS_LINEAR_RGB);
    private static final ColorSpace CIEXYZ = new ColorSpace(TYPE_XYZ, 3, CS_CIEXYZ);

    private final int type;
    private final int numComponents;
    private final int cs;

    protected ColorSpace(int type, int numcomponents, int cs) {
        this.type = type;
        this.numComponents = numcomponents;
        this.cs = cs;
    }

    public static ColorSpace getInstance(int colorspace) {
        switch (colorspace) {
            case CS_GRAY:
                return GRAY;
            case CS_LINEAR_RGB:
                return LINEAR_RGB;
            case CS_CIEXYZ:
                return CIEXYZ;
            case CS_sRGB:
            default:
                return SRGB;
        }
    }

    public int getType() {
        return type;
    }

    public int getNumComponents() {
        return numComponents;
    }

    public boolean isCS_sRGB() {
        return cs == CS_sRGB;
    }

    @Override
    public String toString() {
        return "ColorSpace[" + cs + "]";
    }
}
