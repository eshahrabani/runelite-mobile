package javax.swing.event;

/** Swing {@code MenuEvent}: a menu was selected or deselected. */
public class MenuEvent extends java.util.EventObject {
    private static final long serialVersionUID = 4264753141280094282L;

    public MenuEvent(Object source) {
        super(source == null ? MenuEvent.class : source);
    }
}
