package java.awt.datatransfer;

public class StringSelection implements Transferable, ClipboardOwner {
    private String data;
    public StringSelection(String data) { this.data = data; }
    public Object getTransferData(DataFlavor flavor) { return data; }
    public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.stringFlavor}; }
    public boolean isDataFlavorSupported(DataFlavor flavor) { return true; }
    public void lostOwnership(Clipboard clipboard, Transferable contents) {}
}
