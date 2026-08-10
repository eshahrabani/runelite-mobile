package java.awt.event;

/**
 * AWT ActionEvent compatibility stub for Android runtime.
 */
public class ActionEvent extends java.awt.AWTEvent {
    public static final int ACTION_PERFORMED = 1001;
    public static final int SHIFT_MASK = 1;
    public static final int CTRL_MASK = 2;
    public static final int META_MASK = 4;
    public static final int ALT_MASK = 8;

    private final String command;

    public ActionEvent(Object source, int id, String command) {
        super(source, id);
        this.command = command;
    }

    public ActionEvent(Object source, int id, String command, int modifiers) {
        super(source, id);
        this.command = command;
        this.modifiers = modifiers;
    }

    public String getActionCommand() {
        return command;
    }
}
