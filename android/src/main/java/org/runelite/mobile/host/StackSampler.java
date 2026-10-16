package org.runelite.mobile.host;

import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A poor-man's sampling profiler for the port's own code.
 *
 * <p>Device-only debugging aid: it samples the target thread's stack every few milliseconds
 * and prints the most frequent frames that belong to the port (frames under
 * {@code java.}/{@code javax.}/{@code android.} are attributed to the nearest port frame
 * below them). That is how the sidebar paint cost was localised without a JVM profiler on
 * the device.
 */
public final class StackSampler {
    private static final String TAG = "RuneLiteHost";

    private StackSampler() {
    }

    /** True for the port's own implementations (the shadowed JDK packages plus the host). */
    private static boolean isPortFrame(String className) {
        return className.startsWith("org.runelite.mobile")
            || className.startsWith("javax.swing")
            || className.startsWith("java.awt")
            || className.startsWith("java.beans")
            || className.startsWith("javax.accessibility")
            || className.startsWith("javax.imageio")
            || className.startsWith("javax.sound")
            || className.startsWith("javax.management")
            || className.startsWith("com.sun.net.httpserver")
            || className.startsWith("netscape.javascript");
    }

    /** Samples {@code target} for {@code durationMs} and logs the hottest port frames. */
    public static void sample(Thread target, long periodMs, long durationMs) {
        final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        final long deadline = System.currentTimeMillis() + durationMs;
        Thread sampler = new Thread(() -> {
            while (System.currentTimeMillis() < deadline) {
                StackTraceElement[] stack = target.getStackTrace();
                String owner = null;
                for (StackTraceElement frame : stack) {
                    if (isPortFrame(frame.getClassName())) {
                        owner = frame.getClassName() + "." + frame.getMethodName();
                        break;
                    }
                }
                if (owner != null) {
                    hits.computeIfAbsent(owner, key -> new AtomicInteger()).incrementAndGet();
                }
                try {
                    Thread.sleep(periodMs);
                } catch (InterruptedException e) {
                    return;
                }
            }
            List<Map.Entry<String, AtomicInteger>> sorted = new ArrayList<>(hits.entrySet());
            sorted.sort(Comparator.comparingInt((Map.Entry<String, AtomicInteger> e) -> e.getValue().get()).reversed());
            StringBuilder sb = new StringBuilder("STACK SAMPLES:");
            for (int i = 0; i < Math.min(12, sorted.size()); i++) {
                sb.append(' ').append(sorted.get(i).getKey()).append('=').append(sorted.get(i).getValue());
            }
            Log.i(TAG, sb.toString());
        }, "stack-sampler");
        sampler.setDaemon(true);
        // Fire and forget: the caller IS the thread being sampled, so it must keep running
        // (blocking here would only ever sample the sampler).
        sampler.start();
    }
}
