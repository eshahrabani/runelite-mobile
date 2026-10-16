package java.awt.datatransfer;

import java.io.IOException;

/**
 * Hand-written {@code java.awt.datatransfer.Clipboard}: the port's in-memory clipboard.
 *
 * <p>{@code Toolkit.getSystemClipboard()} returns one of these. It holds the last
 * {@link Transferable} set through {@link #setContents} and answers
 * {@link #getData}/{@link #getContents} from it, including the JDK's ownership rule
 * (the previous owner is told it lost the clipboard).
 *
 * <p>There is deliberately no Android clipboard integration: the core module cannot see
 * {@code android.content.ClipboardManager}, so copy/paste works <em>inside</em> the app
 * (a text field's copy and paste both route through this class) but never crosses the
 * app boundary. The JDK's asynchronous {@code ClipboardOwner} delivery and the
 * flavor-listener API are not implemented — nothing in the shipped client uses them.
 */
public class Clipboard {

    private final String name;
    private Transferable contents;
    private ClipboardOwner owner;

    public Clipboard(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Replaces the clipboard contents, notifying the previous owner (JDK semantics). */
    public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
        ClipboardOwner previousOwner = this.owner;
        Transferable previousContents = this.contents;
        this.contents = contents;
        this.owner = owner;
        if (previousOwner != null && previousOwner != owner) {
            previousOwner.lostOwnership(this, previousContents);
        }
    }

    /** The current contents, or {@code null} when the clipboard is empty. */
    public synchronized Transferable getContents(Object requestor) {
        return contents;
    }

    /** The flavors the current contents can be read as; empty when the clipboard is empty. */
    public synchronized DataFlavor[] getAvailableDataFlavors() {
        Transferable current = contents;
        if (current == null) {
            return new DataFlavor[0];
        }
        DataFlavor[] flavors = current.getTransferDataFlavors();
        return flavors == null ? new DataFlavor[0] : flavors;
    }

    /** True when the current contents can be read as {@code flavor}. */
    public synchronized boolean isDataFlavorAvailable(DataFlavor flavor) {
        Transferable current = contents;
        if (current == null || flavor == null) {
            return false;
        }
        DataFlavor[] flavors = current.getTransferDataFlavors();
        if (flavors == null) {
            return false;
        }
        for (DataFlavor available : flavors) {
            if (flavor.equals(available)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The clipboard data in {@code flavor}.
     *
     * @throws UnsupportedFlavorException when the clipboard is empty or holds nothing
     *         that can produce {@code flavor} — the JDK's behaviour.
     */
    public synchronized Object getData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        Transferable current = contents;
        if (current == null || flavor == null || !current.isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return current.getTransferData(flavor);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[name=" + name + "]";
    }
}
