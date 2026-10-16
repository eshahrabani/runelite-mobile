package org.runelite.mobile.bridge;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * The port's Swing event dispatch thread.
 *
 * <p>RuneLite's contract is that plugins live on the event dispatch thread:
 * {@code PluginManager.startPlugin} asserts it, every Swing mutation is supposed to
 * happen there, and {@code javax.swing.SwingUtilities.invokeLater/invokeAndWait}
 * route to it. On a desktop JVM the EDT is created by AWT himself; here it is this
 * class — one daemon thread named {@code Swing-EDT-rlmobile} owning a deque of
 * {@link Runnable}s.
 *
 * <p>It is started lazily by {@link #post}/{@link #invokeAndWait} or explicitly by
 * {@code MainActivity.onCreate} through {@link #ensureStarted()}; the app registers it
 * as the UI thread with {@link AWTBridge#registerUiThread(Thread, java.util.concurrent.Executor)}.
 * The Android main thread does not become the EDT — it only presents the bitmaps the
 * EDT renders and forwards input events, so a plugin that blocks the EDT cannot freeze
 * the Android UI (and a modal Swing dialog blocks only this thread).
 *
 * <p>Exceptions thrown by a queued task are printed and swallowed: the dispatch thread
 * must survive a broken plugin, exactly like the desktop EDT survives an uncaught
 * exception in an event handler.
 */
public final class SwingEdt {
    private static final LinkedBlockingDeque<Runnable> QUEUE = new LinkedBlockingDeque<>();

    private static volatile Thread thread;

    private SwingEdt() {
    }

    /** Starts the dispatch thread if needed and returns it. */
    public static synchronized Thread ensureStarted() {
        Thread t = thread;
        if (t == null) {
            t = new Thread(SwingEdt::loop, "Swing-EDT-rlmobile");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            thread = t;
            t.start();
        }
        return t;
    }

    /** The dispatch thread, or null before it was started. */
    public static Thread threadOrNull() {
        return thread;
    }

    public static boolean isDispatchThread() {
        Thread t = thread;
        return t != null && Thread.currentThread() == t;
    }

    private static void loop() {
        while (true) {
            Runnable r;
            try {
                r = QUEUE.takeFirst();
            } catch (InterruptedException e) {
                // The thread is a daemon and is never interrupted on purpose; keep serving.
                continue;
            }
            runSafely(r);
        }
    }

    /** Runs {@code r}, reporting an uncaught exception instead of killing the thread. */
    public static void runSafely(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Enqueues {@code r}; the task always runs on the dispatch thread. */
    public static void post(Runnable r) {
        if (r == null) {
            return;
        }
        ensureStarted();
        QUEUE.addLast(r);
    }

    /**
     * Runs {@code r} on the dispatch thread and waits for it. Runs inline when the
     * caller already is the dispatch thread (the JDK throws in that case; running
     * inline is what the port's callers mean).
     */
    public static void invokeAndWait(Runnable r) {
        if (r == null) {
            return;
        }
        if (isDispatchThread()) {
            r.run();
            return;
        }
        ensureStarted();
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Throwable[] failure = new Throwable[1];
        QUEUE.addLast(() -> {
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
            throw failure[0] instanceof RuntimeException
                    ? (RuntimeException) failure[0]
                    : new RuntimeException("invokeAndWait failed", failure[0]);
        }
    }

    /** Dispatches every task queued so far; returns when the queue is empty. */
    public static void dispatchQueued() {
        for (; ; ) {
            Runnable r = QUEUE.pollFirst();
            if (r == null) {
                return;
            }
            runSafely(r);
        }
    }

    /**
     * Nested dispatch loop: keeps dispatching queued tasks until {@code done} reports
     * true. Only legal on the dispatch thread; this is what makes a modal Swing dialog
     * block its caller while the rest of the UI stays alive.
     */
    public static void pumpUntil(BooleanSupplier done) {
        while (!done.getAsBoolean()) {
            Runnable r;
            try {
                r = QUEUE.pollFirst(20, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (r != null) {
                runSafely(r);
            }
        }
    }

    /** Number of tasks waiting to be dispatched. */
    public static int queueSize() {
        return QUEUE.size();
    }
}
