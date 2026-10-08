package java.awt;

public class EventQueue {
    public EventQueue() {}

    public SecondaryLoop createSecondaryLoop() {
        // This port has no AWT event pump, so a secondary loop can never be entered;
        // enter() reports that and callers run their work inline instead.
        return new SecondaryLoop() {
            @Override
            public boolean enter() {
                return false;
            }

            public void exit() {
                // No secondary loop is ever running, so there is nothing to exit.
            }

            void run(Runnable runnable) {
                if (runnable != null) {
                    runnable.run();
                }
                exit();
            }
        };
    }

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
