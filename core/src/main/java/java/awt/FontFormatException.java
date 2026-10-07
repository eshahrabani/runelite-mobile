package java.awt;

/**
 * Thrown by {@link Font#createFont(int, java.io.InputStream)} when the supplied
 * data is not a usable font. Extends {@link Exception} (not {@code IOException})
 * exactly like the desktop JDK, so the client jar's
 * {@code catch (IOException | FontFormatException)} compiles.
 */
public class FontFormatException extends Exception {

    public FontFormatException(String reason) {
        super(reason);
    }

    public FontFormatException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
