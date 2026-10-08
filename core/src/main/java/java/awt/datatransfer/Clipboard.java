package java.awt.datatransfer;

public class Clipboard {
    private String name;
    public Clipboard(String name) { this.name = name; }
    public String getName() { return name; }
    public void setContents(Transferable contents, ClipboardOwner owner) {}
    public Transferable getContents(Object requestor) { return null; }

    /**
     * Returns this clipboard's contents for the requested flavor, or throws
     * {@code UnsupportedFlavorException} when it does not hold that flavor.
     *
     * <p>This port has no AWT clipboard: the core module cannot reference
     * {@code android.content.ClipboardManager}, so there is no backing store to
     * read from. A real implementation would need a bridge to the Android
     * clipboard; returning a fabricated value (or null) here would silently
     * corrupt whatever asked for the data.
     */
    public Object getData(DataFlavor flavor) throws UnsupportedFlavorException, java.io.IOException {
        throw new UnsupportedFlavorException(flavor);
    }
}
