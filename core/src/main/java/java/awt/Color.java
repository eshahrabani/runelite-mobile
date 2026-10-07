package java.awt;

/**
 * AWT Color compatibility stub for Android runtime.
 *
 * <p>Colours are 0xAARRGGBB non-premultiplied values, matching the pixel model
 * of the mobile surface. Implements {@link Paint} so it can be installed with
 * {@code Graphics2D.setPaint}.
 */
public class Color implements Paint {
    public static final Color white = new Color(255, 255, 255);
    public static final Color WHITE = white;
    public static final Color lightGray = new Color(192, 192, 192);
    public static final Color LIGHT_GRAY = lightGray;
    public static final Color gray = new Color(128, 128, 128);
    public static final Color GRAY = gray;
    public static final Color darkGray = new Color(64, 64, 64);
    public static final Color DARK_GRAY = darkGray;
    public static final Color black = new Color(0, 0, 0);
    public static final Color BLACK = black;
    public static final Color red = new Color(255, 0, 0);
    public static final Color RED = red;
    public static final Color pink = new Color(255, 175, 175);
    public static final Color PINK = pink;
    public static final Color orange = new Color(255, 200, 0);
    public static final Color ORANGE = orange;
    public static final Color yellow = new Color(255, 255, 0);
    public static final Color YELLOW = yellow;
    public static final Color green = new Color(0, 255, 0);
    public static final Color GREEN = green;
    public static final Color magenta = new Color(255, 0, 255);
    public static final Color MAGENTA = magenta;
    public static final Color cyan = new Color(0, 255, 255);
    public static final Color CYAN = cyan;
    public static final Color blue = new Color(0, 0, 255);
    public static final Color BLUE = blue;

    private final int value;

    public Color(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public Color(int r, int g, int b, int a) {
        value = ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8)  |
                ((b & 0xFF) << 0);
    }

    public Color(int rgb) {
        value = 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * @param rgba      packed 0xAARRGGBB when {@code hasAlpha}, otherwise
     *                  0x00RRGGBB with an implicit opaque alpha
     * @param hasAlpha  whether the top byte of {@code rgba} is significant
     */
    public Color(int rgba, boolean hasAlpha) {
        value = hasAlpha ? rgba : (0xFF000000 | (rgba & 0xFFFFFF));
    }

    public int getRed() {
        return (getRGB() >> 16) & 0xFF;
    }

    public int getGreen() {
        return (getRGB() >> 8) & 0xFF;
    }

    public int getBlue() {
        return (getRGB() >> 0) & 0xFF;
    }

    public int getAlpha() {
        return (getRGB() >> 24) & 0xFF;
    }

    public int getRGB() {
        return value;
    }

    public Color brighter() {
        int r = getRed();
        int g = getGreen();
        int b = getBlue();
        int alpha = getAlpha();
        int i = (int) (1.0 / 0.7);
        if (r == 0 && g == 0 && b == 0) {
            return new Color(i, i, i, alpha);
        }
        if (r > 0 && r < i) {
            r = i;
        }
        if (g > 0 && g < i) {
            g = i;
        }
        if (b > 0 && b < i) {
            b = i;
        }
        return new Color(Math.min((int) (r / 0.7), 255),
                Math.min((int) (g / 0.7), 255),
                Math.min((int) (b / 0.7), 255), alpha);
    }

    public Color darker() {
        return new Color(Math.max((int) (getRed() * 0.7), 0),
                Math.max((int) (getGreen() * 0.7), 0),
                Math.max((int) (getBlue() * 0.7), 0), getAlpha());
    }

    public static Color decode(String nm) throws NumberFormatException {
        String s = nm.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        int i = Integer.parseInt(s, 16);
        if (s.length() <= 6) {
            return new Color(i);
        }
        return new Color(i, true);
    }

    public static Color getHSBColor(float h, float s, float b) {
        return new Color(HSBtoRGB(h, s, b));
    }

    public static float[] RGBtoHSB(int r, int g, int b, float[] hsbvals) {
        if (hsbvals == null) {
            hsbvals = new float[3];
        }
        float hue;
        float saturation;
        float brightness;
        int cmax = Math.max(r, Math.max(g, b));
        int cmin = Math.min(r, Math.min(g, b));
        brightness = (float) cmax / 255.0f;
        if (cmax != 0) {
            saturation = (float) (cmax - cmin) / (float) cmax;
        } else {
            saturation = 0.0f;
        }
        if (saturation == 0.0f) {
            hue = 0.0f;
        } else {
            float redc = (float) (cmax - r) / (float) (cmax - cmin);
            float greenc = (float) (cmax - g) / (float) (cmax - cmin);
            float bluec = (float) (cmax - b) / (float) (cmax - cmin);
            if (r == cmax) {
                hue = bluec - greenc;
            } else if (g == cmax) {
                hue = 2.0f + redc - bluec;
            } else {
                hue = 4.0f + greenc - redc;
            }
            hue = hue / 6.0f;
            if (hue < 0.0f) {
                hue = hue + 1.0f;
            }
        }
        hsbvals[0] = hue;
        hsbvals[1] = saturation;
        hsbvals[2] = brightness;
        return hsbvals;
    }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0;
        int g = 0;
        int b = 0;
        if (saturation == 0.0f) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - saturation * (1.0f - f));
            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 5:
                default:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                    break;
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public float[] getRGBComponents(float[] compArray) {
        if (compArray == null) {
            compArray = new float[4];
        }
        compArray[0] = ((value >> 16) & 0xFF) / 255.0f;
        compArray[1] = ((value >> 8) & 0xFF) / 255.0f;
        compArray[2] = (value & 0xFF) / 255.0f;
        compArray[3] = ((value >> 24) & 0xFF) / 255.0f;
        return compArray;
    }

    public float[] getRGBColorComponents(float[] compArray) {
        if (compArray == null) {
            compArray = new float[3];
        }
        compArray[0] = ((value >> 16) & 0xFF) / 255.0f;
        compArray[1] = ((value >> 8) & 0xFF) / 255.0f;
        compArray[2] = (value & 0xFF) / 255.0f;
        return compArray;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Color && ((Color) obj).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[r=" + getRed() + ",g=" + getGreen()
                + ",b=" + getBlue() + "]";
    }
}
