package java.awt;

import org.runelite.mobile.bridge.SwingEdt;

/**
 * The port's AWT event queue.
 *
 * <p>There is exactly one: the queue owned by {@link SwingEdt}. {@link #postEvent}
 * runs the event's {@code dispatch()} on the Swing dispatch thread, so
 * {@code EventQueue.invokeLater}/{@code invokeAndWait} and every Swing
 * {@code invokeLater} land in the same ordered queue — which is what
 * {@code SwingUtil.pumpPendingEvents()} (a secondary loop) needs to be able to drain.
 */
public class EventQueue {
    public EventQueue() {
    }

    public void postEvent(AWTEvent event) {
        if (event == null) {
            return;
        }
        SwingEdt.post(() -> {
            Object source = event.getSource();
            if (source instanceof Component) {
                ((Component) source).dispatchEvent(event);
            }
        });
    }

    /**
     * No queue peeking: the port's queue is shared with the Swing task queue, so the
     * next entry is not necessarily an {@code AWTEvent}.
     */
    public AWTEvent peekEvent() {
        return null;
    }

    public AWTEvent peekEvent(int id) {
        return null;
    }

    public static boolean isDispatchThread() {
        return SwingEdt.isDispatchThread();
    }

    public static void invokeLater(Runnable runnable) {
        SwingEdt.post(runnable);
    }

    public static void invokeAndWait(Runnable runnable) {
        SwingEdt.invokeAndWait(runnable);
    }

    /**
     * A secondary loop pumps the Swing queue until {@code exit()} is called. The JDK
     * contract is that {@code enter()} blocks the calling (dispatch) thread while other
     * events are still processed; {@link SwingEdt#dispatchQueued()} is that pump, so a
     * task posted from inside the loop can observe its own effects.
     */
    public SecondaryLoop createSecondaryLoop() {
        return new SecondaryLoop() {
            private volatile boolean exited;

            @Override
            public boolean enter() {
                if (!SwingEdt.isDispatchThread()) {
                    // Nothing to pump from a foreign thread; the loop cannot block here.
                    return false;
                }
                while (!exited) {
                    if (SwingEdt.queueSize() == 0) {
                        // Nothing queued: give the poster a moment instead of spinning.
                        try {
                            Thread.sleep(2);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    SwingEdt.dispatchQueued();
                }
                return true;
            }

            public void exit() {
                exited = true;
            }

            void run(Runnable runnable) {
                if (runnable != null) {
                    runnable.run();
                }
                exit();
            }
        };
    }
}
