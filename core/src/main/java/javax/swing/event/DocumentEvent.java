package javax.swing.event;

import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * Swing {@code DocumentEvent}: a document mutation. Implemented by
 * {@code AbstractDocument.DefaultDocumentEvent}, which every text component observes.
 */
public interface DocumentEvent {
    int getOffset();

    int getLength();

    Document getDocument();

    EventType getType();

    ElementChange getChange(Element elem);

    /** The kind of change: {@link #INSERT}, {@link #REMOVE} or {@link #CHANGE}. */
    class EventType {
        public static final EventType INSERT = new EventType("INSERT");
        public static final EventType REMOVE = new EventType("REMOVE");
        public static final EventType CHANGE = new EventType("CHANGE");

        private final String name;

        private EventType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Describes the elements added to or removed from a parent element. */
    interface ElementChange {
        Element getElement();

        Element[] getChildrenAdded();

        Element[] getChildrenRemoved();

        int getIndex();
    }
}
