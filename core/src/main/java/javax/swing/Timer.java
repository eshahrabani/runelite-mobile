package javax.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.TimerTask;

import org.runelite.mobile.bridge.AWTBridge;

/**
 * {@code javax.swing.Timer} backed by {@link java.util.Timer} (java.base) with the
 * action delivered on the UI thread through {@link AWTBridge#post}.
 *
 * <p>Plugins use it for periodic overlay work (e.g. "re-check every 600 ms"), so the
 * timer must actually fire: a no-op stub would silently break those plugins. Firing on
 * the UI thread keeps RuneLite's single-threaded plugin contract intact.
 */
public class Timer {

    private final int delay;
    private final ActionListener listener;
    private final java.util.Timer timer = new java.util.Timer("swing-timer", true);
    private volatile boolean repeats = true;
    private volatile boolean running;
    private TimerTask task;
    private long initialDelay = -1;

    public Timer(int delay, ActionListener listener) {
        this.delay = delay;
        this.listener = listener;
    }

    public void addActionListener(ActionListener listener) {
        // the constructor listener is the only one RuneLite uses; extra listeners are
        // accepted and ignored rather than breaking the timer
    }

    public void setRepeats(boolean flag) {
        this.repeats = flag;
    }

    public boolean isRepeats() {
        return repeats;
    }

    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getInitialDelay() {
        return initialDelay < 0 ? delay : (int) initialDelay;
    }

    public int getDelay() {
        return delay;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        final long period = initialDelay < 0 ? delay : initialDelay;
        task = new TimerTask() {
            @Override
            public void run() {
                if (!repeats) {
                    stop();
                }
                fire();
            }
        };
        try {
            timer.scheduleAtFixedRate(task, Math.max(1, period), Math.max(1, delay));
        } catch (Throwable t) {
            running = false;
        }
    }

    public synchronized void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public synchronized void restart() {
        stop();
        start();
    }

    private void fire() {
        final ActionListener l = listener;
        if (l == null) {
            return;
        }
        AWTBridge.post(() -> l.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "timer")));
    }
}
