package java.awt;

import java.io.IOException;
import java.net.URI;

/**
 * AWT Desktop compatibility stub for Android runtime.
 */
public class Desktop {
    public enum Action { OPEN, EDIT, PRINT, MAIL, BROWSE }

    private static final Desktop instance = new Desktop();

    /** Bridge for opening links in the host app's browser. Set by the Android host. */
    public static java.util.function.Consumer<String> openUrlHandler;

    private Desktop() {}

    public static Desktop getDesktop() {
        return instance;
    }

    public static boolean isDesktopSupported() {
        return true;
    }

    public boolean isSupported(Action action) {
        return true;
    }

    public void browse(URI uri) throws IOException {
        if (openUrlHandler != null) {
            openUrlHandler.accept(uri.toString());
        } else {
            System.out.println("Desktop.browse called for: " + uri);
        }
    }
}
