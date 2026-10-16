package org.runelite.mobile.bridge;

import java.util.ArrayList;
import java.util.List;

/**
 * AWTBridge bridges pixel rendering data and touch inputs
 * without depending on desktop-only java.awt packages.
 */
public class AWTBridge {
    public static volatile int[] activePixels;
    public static volatile int activeWidth;
    public static volatile int activeHeight;

    /**
     * The thread the host registered as the event dispatch thread plus an executor that
     * posts to it. RuneLite's contract is "plugins run on the event dispatch thread":
     * {@code PluginManager.startPlugin} asserts it and
     * {@code javax.swing.SwingUtilities.invokeLater/invokeAndWait} rely on it.
     *
     * <p>Since the port runs RuneLite's real Swing shell, that thread is
     * {@link SwingEdt}'s {@code Swing-EDT-rlmobile}, not the Android main thread:
     * the main thread only presents bitmaps and forwards input, so a modal Swing
     * dialog or a slow panel cannot freeze the Android UI. {@code MainActivity.onCreate}
     * calls {@code registerUiThread(SwingEdt.ensureStarted(), r -> SwingEdt.post(r))};
     * before that (and in core-only contexts such as the self-tests) the default
     * {@link SwingEdt} queue is used directly.
     */
    private static volatile Thread uiThread;
    private static volatile java.util.concurrent.Executor uiExecutor;

    public static void registerUiThread(Thread thread, java.util.concurrent.Executor executor) {
        uiThread = thread;
        uiExecutor = executor;
    }

    /** The event dispatch thread: the registered one, else the default {@link SwingEdt}. */
    public static Thread swingThread() {
        Thread t = uiThread;
        return t != null ? t : SwingEdt.threadOrNull();
    }

    public static boolean isUiThread() {
        Thread t = swingThread();
        return t != null && Thread.currentThread() == t;
    }

    /** Enqueues {@code r} on the event dispatch thread. */
    public static void post(Runnable r) {
        if (r == null) {
            return;
        }
        java.util.concurrent.Executor executor = uiExecutor;
        if (executor != null) {
            executor.execute(r);
        } else {
            SwingEdt.post(r);
        }
    }

    /** Runs {@code r} on the event dispatch thread and waits for it (inline when already there). */
    public static void invokeAndWait(Runnable r) {
        if (r == null) {
            return;
        }
        if (isUiThread()) {
            r.run();
            return;
        }
        if (uiExecutor == null) {
            SwingEdt.invokeAndWait(r);
            return;
        }
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Throwable[] failure = new Throwable[1];
        uiExecutor.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                latch.countDown();
            }
        });
        boolean interrupted = false;
        for (; ; ) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure[0] != null) {
            throw new RuntimeException("invokeAndWait failed", failure[0]);
        }
    }

    /**
     * MousePathSmoother generates human-like curves between coordinates.
     */
    public static class MousePathSmoother {

        /**
         * Generates a list of MobilePoint points representing a natural path.
         */
        public static List<MobilePoint> generatePath(MobilePoint start, MobilePoint end, int steps) {
            List<MobilePoint> path = new ArrayList<>();
            if (steps <= 1) {
                path.add(end);
                return path;
            }

            int dx = end.x - start.x;
            int dy = end.y - start.y;

            double controlFactor = 0.2 + Math.random() * 0.3;
            MobilePoint ctrl1 = new MobilePoint(
                (int) (start.x + dx * controlFactor + (Math.random() - 0.5) * 50),
                (int) (start.y + dy * controlFactor + (Math.random() - 0.5) * 50)
            );
            MobilePoint ctrl2 = new MobilePoint(
                (int) (start.x + dx * (1.0 - controlFactor) + (Math.random() - 0.5) * 50),
                (int) (start.y + dy * (1.0 - controlFactor) + (Math.random() - 0.5) * 50)
            );

            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                
                double omt = 1.0 - t;
                double omt2 = omt * omt;
                double omt3 = omt2 * omt;
                double t2 = t * t;
                double t3 = t2 * t;

                int x = (int) (omt3 * start.x + 3 * omt2 * t * ctrl1.x + 3 * omt * t2 * ctrl2.x + t3 * end.x);
                int y = (int) (omt3 * start.y + 3 * omt2 * t * ctrl1.y + 3 * omt * t2 * ctrl2.y + t3 * end.y);

                path.add(new MobilePoint(x, y));
            }

            return path;
        }
    }
}
