package java.awt;

/**
 * AWT Cursor compatibility stub for Android runtime.
 */
public class Cursor {
    public static final int DEFAULT_CURSOR = 0;
    protected int type;

    public Cursor(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static Cursor getPredefinedCursor(int type) {
        return new Cursor(type);
    }
}
