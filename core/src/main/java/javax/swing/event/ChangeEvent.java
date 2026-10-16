package javax.swing.event;

/**
 * Swing {@code ChangeEvent}: a component's model changed state (tabs, spinners, scroll
 * bars, text carets). Hand-written because the widgets on this port really fire it.
 */
public class ChangeEvent extends java.util.EventObject {
    private static final long serialVersionUID = -6624080050314246080L;

    public ChangeEvent(Object source) {
        super(source == null ? ChangeEvent.class : source);
    }
}
