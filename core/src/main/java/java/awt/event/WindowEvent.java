package java.awt.event;

public class WindowEvent {
    public static final int WINDOW_OPENED = 200;
    public static final int WINDOW_CLOSING = 201;
    public static final int WINDOW_CLOSED = 202;
    public static final int WINDOW_ICONIFIED = 203;
    public static final int WINDOW_DEICONIFIED = 204;
    public static final int WINDOW_ACTIVATED = 205;
    public static final int WINDOW_DEACTIVATED = 206;

    private int id;
    private java.awt.Window source;

    public WindowEvent(java.awt.Window source, int id) {
        this.source = source;
        this.id = id;
    }

    public int getID() { return id; }

    public java.awt.Window getSource() { return source; }
}
