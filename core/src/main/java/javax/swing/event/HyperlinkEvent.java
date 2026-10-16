package javax.swing.event;

import java.net.URL;

/**
 * Swing {@code HyperlinkEvent}: a link in an HTML document was activated.
 *
 * <p>Hand-written because {@code JEditorPane}'s link handling on this port really fires
 * it (the info panel and RuneLite's rich-text panes listen for it).
 */
public class HyperlinkEvent extends java.util.EventObject {
    private static final long serialVersionUID = -7296984589999209483L;

    private final EventType type;
    private final URL u;
    private final String desc;

    public HyperlinkEvent(Object source, EventType type, URL u) {
        this(source, type, u, null);
    }

    public HyperlinkEvent(Object source, EventType type, URL u, String desc) {
        super(source == null ? HyperlinkEvent.class : source);
        this.type = type;
        this.u = u;
        this.desc = desc;
    }

    public EventType getEventType() {
        return type;
    }

    public URL getURL() {
        return u;
    }

    public String getDescription() {
        return desc;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[type=" + type + ",url=" + u + "]";
    }

    /** The kind of link event; only {@link #ACTIVATED} is produced on this port. */
    public static final class EventType {
        public static final EventType ENTERED = new EventType("ENTERED");
        public static final EventType EXITED = new EventType("EXITED");
        public static final EventType ACTIVATED = new EventType("ACTIVATED");

        private final String name;

        private EventType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
