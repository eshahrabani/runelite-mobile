package java.awt.datatransfer;

public class DataFlavor {
    public static final DataFlavor stringFlavor = new DataFlavor();

    /**
     * Flavor describing an image transfer, matching the JDK's
     * {@code (image/x-java-image, BufferedImage)} flavor.
     *
     * <p>This stub only has the no-arg constructor (the client never references
     * a {@code DataFlavor(Class, String)} constructor, so none is declared), so
     * the mime type and representation class are filled in by the static
     * initializer below rather than by the constructor.
     */
    public static final DataFlavor imageFlavor = new DataFlavor();

    private String mimeType;
    private Class<?> representationClass;

    static {
        imageFlavor.mimeType = "image/x-java-image";
        imageFlavor.representationClass = java.awt.image.BufferedImage.class;
    }

    public DataFlavor() {}

    /**
     * Value equality: same MIME type and same representation class.
     *
     * <p>This is an overload of {@link Object#equals(Object)}, not an override
     * (the client references {@code equals(DataFlavor)}). Because
     * {@code Object.equals}/{@code hashCode} are left as the identity-based
     * inherited implementations, the two paths cannot contradict each other;
     * collection keys still use identity, while the client's flavor
     * comparisons use this method.
     *
     * <p>A {@code null} representation class is tolerated the way the JDK
     * tolerates it: two flavors are equal only when both carry the same class,
     * including the both-{@code null} case.
     */
    public boolean equals(DataFlavor that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }
        if (!java.util.Objects.equals(this.mimeType, that.mimeType)) {
            return false;
        }
        return this.representationClass == that.representationClass;
    }
}
