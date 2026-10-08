package java.lang.management;

import java.util.List;

public interface RuntimeMXBean {
    List<String> getInputArguments();

    /**
     * Uptime of the process this JVM runs in, in milliseconds. Android exposes
     * no JVM start time, so the port reports the app process's own uptime; see
     * {@link ManagementFactory} for how it is obtained and the fallback when the
     * platform cannot report it.
     */
    long getUptime();
}
