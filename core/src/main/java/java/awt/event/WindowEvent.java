package java.awt.event;

import java.awt.Component;

public class WindowEvent {
    public static final int WINDOW_OPENED = 200;
    public static final int WINDOW_CLOSING = 201;
    public static final int WINDOW_CLOSED = 202;
    public static final int WINDOW_ICONIFIED = 203;
    public static final int WINDOW_DEICONIFIED = 204;
    public static final int WINDOW_ACTIVATED = 205;
    public static final int WINDOW_DEACTIVATED = 206;

    private int id;
    private Component source;

    public WindowEvent(Component source, int id) {
        this.source = source;
        this.id = id;
    }

    public int getID() { return id; }
    public Component getSource() { return source; }
}
