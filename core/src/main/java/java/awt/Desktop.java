package java.awt;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import java.awt.desktop.QuitStrategy;

/**
 * Hand-written {@code java.awt.Desktop} for this port.
 *
 * <p>There is no desktop environment: opening a URI or a file is delegated to the app
 * through {@link #openUrlHandler} (the Android dex sets it to the Android intent
 * launcher; see {@code MainActivity}). {@code LinkBrowser} asks
 * {@link #isDesktopSupported()} / {@link #isSupported(Action)} first, so BROWSE and OPEN
 * are reported as supported (they really work) while EDIT/PRINT/MAIL are not.
 *
 * <p>{@link #setQuitStrategy} and {@link #requestForeground} are stored and otherwise
 * ignored: the host owns the app's lifecycle, and a virtual window cannot be raised in a
 * window manager because there is none.
 */
public class Desktop {
    public enum Action { OPEN, EDIT, PRINT, MAIL, BROWSE }

    private static final Desktop instance = new Desktop();

    /** Bridge for opening links in the host app's browser. Set by the Android host. */
    public static java.util.function.Consumer<String> openUrlHandler;

    private QuitStrategy quitStrategy;
    private boolean foregroundRequested;

    private Desktop() {
    }

    public static Desktop getDesktop() {
        return instance;
    }

    public static boolean isDesktopSupported() {
        return true;
    }

    public boolean isSupported(Action action) {
        return action == Action.BROWSE || action == Action.OPEN;
    }

    public void browse(URI uri) throws IOException {
        if (openUrlHandler != null) {
            openUrlHandler.accept(uri.toString());
        } else {
            System.out.println("Desktop.browse called for: " + uri);
        }
    }

    public void open(File file) throws IOException {
        // Mirror browse(): hand the file's URI to the host browser bridge when present.
        // A stub has no native "open" action, so an unset handler is a silent no-op
        // rather than an exception (LinkBrowser calls this from live plugin code).
        if (openUrlHandler != null) {
            openUrlHandler.accept(file.toURI().toString());
        }
    }

    /** Stored; the host decides how the app quits (there is no window manager to ask). */
    public void setQuitStrategy(QuitStrategy strategy) {
        this.quitStrategy = strategy;
    }

    public QuitStrategy getQuitStrategy() {
        return quitStrategy;
    }

    /** Stored; a virtual window has no window manager to foreground it through. */
    public void requestForeground(boolean allWindows) {
        this.foregroundRequested = allWindows;
    }

    public boolean isForegroundRequested() {
        return foregroundRequested;
    }
}
