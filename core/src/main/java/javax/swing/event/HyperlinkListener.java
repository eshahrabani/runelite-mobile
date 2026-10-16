package javax.swing.event;

import java.util.EventListener;

/** Listener for {@link HyperlinkEvent}s fired by an HTML-aware text component. */
public interface HyperlinkListener extends EventListener {
    void hyperlinkUpdate(HyperlinkEvent e);
}
