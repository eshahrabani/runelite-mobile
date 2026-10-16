package java.awt.datatransfer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Hand-written {@code java.awt.datatransfer.StringSelection}: a {@link Transferable}
 * that carries a string, exactly like the desktop class.
 *
 * <p>It answers {@link DataFlavor#stringFlavor} with the string itself,
 * {@link DataFlavor#plainTextFlavor} with a UTF-8 {@link InputStream} (the JDK returns a
 * {@code StringBufferInputStream}, which no longer exists) and any flavor whose
 * representation class is {@code String[]} with the text as a one-element array. That
 * last one is a port extension for callers that move string lists around with the same
 * clipboard; the JDK's class does not have it.
 */
public class StringSelection implements Transferable, ClipboardOwner {

    private static final DataFlavor STRING_ARRAY_FLAVOR =
            new DataFlavor(String[].class, "String array");

    private final String data;

    public StringSelection(String data) {
        this.data = data;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.plainTextFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor == null) {
            return false;
        }
        return flavor.equals(DataFlavor.stringFlavor)
                || flavor.equals(DataFlavor.plainTextFlavor)
                || flavor.equals(STRING_ARRAY_FLAVOR);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (flavor == null) {
            throw new UnsupportedFlavorException(null);
        }
        if (flavor.equals(DataFlavor.stringFlavor)) {
            return data;
        }
        if (flavor.equals(DataFlavor.plainTextFlavor)) {
            return toStream();
        }
        if (flavor.equals(STRING_ARRAY_FLAVOR)) {
            return new String[] {data};
        }
        throw new UnsupportedFlavorException(flavor);
    }

    private InputStream toStream() throws IOException {
        try {
            return new ByteArrayInputStream(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is mandated by the JLS; unreachable.
            throw new IOException("UTF-8 unavailable", e);
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        // Nothing to release: the port's clipboard holds an in-memory reference only.
    }

    @Override
    public String toString() {
        return getClass().getName() + "[string=" + data + "]";
    }
}
