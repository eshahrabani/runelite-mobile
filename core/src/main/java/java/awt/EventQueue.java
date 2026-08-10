package java.awt;

public class EventQueue {
    public EventQueue() {}

    public void postEvent(AWTEvent event) {}

    public AWTEvent peekEvent() { return null; }
    public AWTEvent peekEvent(int id) { return null; }
    public static boolean isDispatchThread() { return false; }
    public static void invokeLater(Runnable runnable) {
        if (runnable != null) {
            new Thread(runnable).start();
        }
    }
    public static void invokeAndWait(Runnable runnable) throws Exception {
        if (runnable != null) {
            runnable.run();
        }
    }
}
