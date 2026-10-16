package java.awt.datatransfer;

/**
 * Hand-written {@code java.awt.datatransfer.UnsupportedFlavorException}.
 *
 * <p>The port has no java.desktop, so this exception is spelled out here: the shipped
 * client constructs it with the flavor it could not satisfy
 * ({@code TransferableBufferedImage}) and lets the message be the flavor's
 * human-presentable name, exactly like the JDK.
 */
public class UnsupportedFlavorException extends java.lang.Exception {

    /** The flavor that was requested and is not available. */
    private final DataFlavor flavor;

    public UnsupportedFlavorException(DataFlavor flavor) {
        super(flavor != null ? flavor.getHumanPresentableName() : null);
        this.flavor = flavor;
    }

    /** The flavor that caused this exception. */
    public DataFlavor getFlavor() {
        return flavor;
    }
}
