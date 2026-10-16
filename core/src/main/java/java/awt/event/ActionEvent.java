package java.awt.event;

import java.awt.AWTEvent;

/**
 * AWT {@code ActionEvent}: a component's action fired.
 *
 * <p>Hand-written because the game client constructs these directly
 * ({@code ActionEvent.<init> -> AWTEvent.<init>}) and the Swing buttons and menu items
 * fire them, so every JDK constructor overload has to exist.
 */
public class ActionEvent extends AWTEvent {
    public static final int ACTION_FIRST = 1001;
    public static final int ACTION_PERFORMED = 1001;
    public static final int ACTION_LAST = 1001;

    public static final int SHIFT_MASK = 1;
    public static final int CTRL_MASK = 2;
    public static final int META_MASK = 4;
    public static final int ALT_MASK = 8;

    public static final String ACTION_COMMAND_KEY = "ActionCommandKey";

    private final String actionCommand;
    private final long when;
    private final int modifiers;

    public ActionEvent(Object source, int id, String command) {
        this(source, id, command, 0);
    }

    public ActionEvent(Object source, int id, String command, int modifiers) {
        this(source, id, command, 0L, modifiers);
    }

    public ActionEvent(Object source, int id, String command, long when, int modifiers) {
        super(source, id);
        this.actionCommand = command;
        this.when = when;
        this.modifiers = modifiers;
    }

    public String getActionCommand() {
        return actionCommand;
    }

    public long getWhen() {
        return when;
    }

    public int getModifiers() {
        return modifiers;
    }

    public String paramString() {
        return "cmd=" + actionCommand + ",when=" + when + ",modifiers=" + modifiers;
    }
}
