package java.lang.management;

import java.util.Collections;
import java.util.List;

/**
 * java.lang.management.ManagementFactory compatibility stub for Android runtime.
 *
 * <p>Android's boot classpath has no {@code java.lang.management} module and this
 * port runs no JMX MBean server, so these stubs <em>are</em> the runtime: each
 * accessor either reports a value this process really has or fails honestly,
 * never a fabricated one.
 */
public class ManagementFactory {
    /**
     * Reference point for {@link RuntimeMXBean#getUptime()} when Android's own
     * process clock is unreachable: milliseconds since this class was first
     * loaded, the closest thing to "JVM uptime" capturable in pure Java.
     */
    private static final long CLASS_LOAD_NANOS = System.nanoTime();

    private static final RuntimeMXBean runtimeMXBean = new RuntimeMXBean() {
        @Override
        public List<String> getInputArguments() {
            return Collections.emptyList();
        }

        @Override
        public long getUptime() {
            return uptimeMillis();
        }
    };

    /**
     * A bean over the host the app is actually running on: the {@code os.*}
     * system properties and the runtime's processor count. It implements only
     * {@code java.lang.management.OperatingSystemMXBean} and is deliberately NOT
     * an instance of {@code com.sun.management.OperatingSystemMXBean}: the client
     * reaches the extended metrics only behind an {@code instanceof} check, so
     * claiming to be one would have it ask for values no Android API can supply.
     */
    private static final OperatingSystemMXBean operatingSystemMXBean = new OperatingSystemMXBean() {
        @Override
        public String getName() {
            return System.getProperty("os.name");
        }

        @Override
        public String getArch() {
            return System.getProperty("os.arch");
        }

        @Override
        public String getVersion() {
            return System.getProperty("os.version");
        }

        @Override
        public int getAvailableProcessors() {
            return Runtime.getRuntime().availableProcessors();
        }

        @Override
        public double getSystemLoadAverage() {
            return -1.0d;
        }
    };

    public static RuntimeMXBean getRuntimeMXBean() {
        return runtimeMXBean;
    }

    /**
     * Android exposes no per-collector GC MXBeans (there is no such type in this
     * runtime), so the honest answer is an empty list. Returning elements is not
     * possible without inventing a collector-bean type the runtime does not have;
     * the client's loop over this result (calling {@code isValid()} /
     * {@code getCollectionTime()} on each element) is therefore unreachable,
     * which is exactly the point.
     */
    public static List getGarbageCollectorMXBeans() {
        return Collections.emptyList();
    }

    public static OperatingSystemMXBean getOperatingSystemMXBean() {
        return operatingSystemMXBean;
    }

    /**
     * There is no JMX MBean server on this port ({@code javax.management} is a
     * data-only stub), so this fails honestly instead of returning null or a
     * fabricated server. The only caller is the desktop entry point
     * {@code net.runelite.client.RuneLite}, which never runs here.
     */
    public static javax.management.MBeanServer getPlatformMBeanServer() {
        throw new UnsupportedOperationException("no JMX MBean server on this port");
    }

    /**
     * Uptime of this process in milliseconds. {@code android.os.Process} records
     * the process start on the same clock as
     * {@code android.os.SystemClock.uptimeMillis()}, so their difference is the
     * real process uptime; both are reached reflectively because core must not
     * reference {@code android.*} directly. When that is unavailable (a
     * non-Android host, or an API that throws), fall back to
     * {@code System.nanoTime()} since this class was first loaded - documented
     * here because it measures class-load uptime, not process uptime.
     */
    private static long uptimeMillis() {
        try {
            long startUptime = (Long) Class.forName("android.os.Process")
                    .getMethod("getStartUptimeMillis").invoke(null);
            long nowUptime = (Long) Class.forName("android.os.SystemClock")
                    .getMethod("uptimeMillis").invoke(null);
            if (startUptime > 0L && nowUptime >= startUptime) {
                return nowUptime - startUptime;
            }
        } catch (Throwable ignored) {
            // Fall through to the class-load-relative value below.
        }
        return (System.nanoTime() - CLASS_LOAD_NANOS) / 1_000_000L;
    }
}
