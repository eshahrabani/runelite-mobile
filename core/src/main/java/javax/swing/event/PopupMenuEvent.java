package javax.swing.event;

/** Swing {@code PopupMenuEvent}: a popup menu was shown or hidden. */
public class PopupMenuEvent extends java.util.EventObject {
    private static final long serialVersionUID = -4561688650910667291L;

    public PopupMenuEvent(Object source) {
        super(source == null ? PopupMenuEvent.class : source);
    }
}
