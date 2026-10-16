package java.awt.desktop;

/**
 * AWT {@code QuitStrategy}: what the desktop does when the application is asked to quit.
 * The port has no desktop integration, so the value is stored and ignored — but the
 * constants must exist because {@code ClientUI} sets one during bootstrap.
 */
public enum QuitStrategy {
    NORMAL_EXIT,
    CLOSE_ALL_WINDOWS
}
