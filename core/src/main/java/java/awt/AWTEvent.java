package java.awt;

/**
 * AWTEvent compatibility stub for Android runtime.
 */
public class AWTEvent {
    public static final long serialVersionUID = -1826253588690080251L;

    protected int id;
    protected int modifiers;

    public AWTEvent(Object source, int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }
}
