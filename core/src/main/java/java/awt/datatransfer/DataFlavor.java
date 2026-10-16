package java.awt.datatransfer;

import java.io.InputStream;

/**
 * Hand-written {@code java.awt.datatransfer.DataFlavor}.
 *
 * <p>Describes the type of transferred data as a MIME type plus a representation class.
 * The port keeps the three flavors the shipped client actually uses —
 * {@link #stringFlavor}, {@link #plainTextFlavor} (whose data is an
 * {@link InputStream}, as in the JDK) and {@link #imageFlavor} — and the general
 * mechanism (any MIME type / representation class pair, value equality), because
 * {@code TransferableBufferedImage} compares {@code imageFlavor.equals(...)} and plugin
 * code builds its own flavors. Text placed on the clipboard can also be read as a
 * {@code String[]} (see {@link StringSelection}).
 *
 * <p>Deviation: the JDK parses the MIME type (parameters included) and reflects the
 * representation class named by the {@code class=} parameter; this port stores what the
 * constructor was given and compares MIME type strings literally. Every flavor used by
 * the client is constructed with an explicit representation class, so the difference is
 * not observable.
 */
public class DataFlavor {

    /** Plain text: data is a {@link String}. */
    public static final DataFlavor stringFlavor =
            new DataFlavor(String.class, "String");

    /**
     * Plain text as an {@link InputStream} — the JDK's long-deprecated flavor, kept
     * because {@code StringSelection} advertises it.
     */
    public static final DataFlavor plainTextFlavor =
            new DataFlavor("text/plain; charset=unicode; class=java.io.InputStream",
                    "Plain Text", InputStream.class);

    /** An image, matching the JDK's {@code image/x-java-image} flavor. */
    public static final DataFlavor imageFlavor =
            new DataFlavor("image/x-java-image", "Image", java.awt.image.BufferedImage.class);

    private String mimeType;
    private String humanPresentableName;
    private Class<?> representationClass;

    /** The JDK's "unknown flavor" instance. */
    public DataFlavor() {
        this("application/x-java-serialized-object", "Unknown", (Class<?>) null);
    }

    public DataFlavor(String mimeType) {
        this(mimeType, mimeType, (Class<?>) null);
    }

    public DataFlavor(String mimeType, String humanPresentableName) {
        this(mimeType, humanPresentableName, (Class<?>) null);
    }

    public DataFlavor(String mimeType, String humanPresentableName, Class<?> representationClass) {
        this.mimeType = mimeType == null ? "application/x-java-serialized-object" : mimeType;
        this.humanPresentableName = humanPresentableName;
        this.representationClass = representationClass;
    }

    /** JDK form: the representation class is not named (used by text flavors). */
    public DataFlavor(String mimeType, String humanPresentableName, ClassLoader classLoader) {
        this(mimeType, humanPresentableName, (Class<?>) null);
    }

    public DataFlavor(Class<?> representationClass, String humanPresentableName) {
        this("application/x-java-serialized-object", humanPresentableName, representationClass);
    }

    public String getMimeType() {
        return mimeType;
    }

    public Class<?> getRepresentationClass() {
        return representationClass;
    }

    public String getHumanPresentableName() {
        return humanPresentableName;
    }

    public void setHumanPresentableName(String humanPresentableName) {
        this.humanPresentableName = humanPresentableName;
    }

    /** The sub-type of the MIME type (after the {@code /}), as the JDK returns it. */
    public String getSubType() {
        int slash = mimeType == null ? -1 : mimeType.indexOf('/');
        if (slash < 0) {
            return mimeType;
        }
        String sub = mimeType.substring(slash + 1);
        int semi = sub.indexOf(';');
        return (semi < 0 ? sub : sub.substring(0, semi)).trim();
    }

    public String getPrimaryType() {
        int slash = mimeType == null ? -1 : mimeType.indexOf('/');
        return slash < 0 ? mimeType : mimeType.substring(0, slash).trim();
    }

    /** True when {@code dataFlavor} has the same MIME type. */
    public boolean isMimeTypeEqual(DataFlavor dataFlavor) {
        return dataFlavor != null && equalsString(mimeType, dataFlavor.mimeType);
    }

    public boolean isMimeTypeEqual(String mimeType) {
        return equalsString(this.mimeType, mimeType);
    }

    /** True when this flavor is a {@code text/*} flavor. */
    public boolean isFlavorTextType() {
        return mimeType != null && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("text/");
    }

    /** Value equality: same MIME type and same representation class (JDK rule). */
    public boolean equals(DataFlavor that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }
        if (!equalsString(mimeType, that.mimeType)) {
            return false;
        }
        return representationClass == that.representationClass;
    }

    private static boolean equalsString(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DataFlavor) {
            return equals((DataFlavor) o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = mimeType == null ? 0 : mimeType.hashCode();
        result = 31 * result + (representationClass == null ? 0 : representationClass.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[mimetype=\"" + mimeType + "\";representationclass="
                + representationClass + "]";
    }
}
