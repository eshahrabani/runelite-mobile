package java.awt.datatransfer;

public interface Transferable {
    Object getTransferData(DataFlavor flavor) throws Exception;
    DataFlavor[] getTransferDataFlavors();
    boolean isDataFlavorSupported(DataFlavor flavor);
}
