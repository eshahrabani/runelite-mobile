package java.awt.datatransfer;

import java.io.IOException;

/**
 * Hand-written {@code java.awt.datatransfer.Transferable}, matching the desktop
 * interface exactly (the port's copy previously threw the broader {@code Exception},
 * which forced every implementation to declare it).
 */
public interface Transferable {

    /** All flavors this object can produce. */
    DataFlavor[] getTransferDataFlavors();

    /** True when {@code flavor} is among {@link #getTransferDataFlavors()}. */
    boolean isDataFlavorSupported(DataFlavor flavor);

    /** The data in the requested flavor. */
    Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException;
}
