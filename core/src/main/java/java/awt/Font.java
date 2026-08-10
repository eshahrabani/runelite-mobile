package java.awt;

/**
 * AWT Font compatibility stub for Android runtime.
 */
public class Font {
    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;

    protected String name;
    protected int style;
    protected int size;

    public Font(String name, int style, int size) {
        this.name = name;
        this.style = style;
        this.size = size;
    }

    public String getName() { return name; }
    public int getStyle() { return style; }
    public int getSize() { return size; }
}
