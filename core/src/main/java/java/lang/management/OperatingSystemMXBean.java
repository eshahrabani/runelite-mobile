package java.lang.management;

/**
 * java.lang.management.OperatingSystemMXBean compatibility stub for Android
 * runtime.
 *
 * <p>Android's boot classpath has no {@code java.lang.management} module, so this
 * is the type {@link ManagementFactory#getOperatingSystemMXBean()} returns. It
 * mirrors the real JDK interface's five members; the port's single implementation
 * ({@link ManagementFactory}) answers them with the host's real values where
 * Android can (the {@code os.*} system properties, the runtime's processor
 * count) and the JDK's documented "not available" value elsewhere, rather than
 * fabricating numbers.
 *
 * <p>Hand-written, without the stub generator's marker: it is never regenerated.
 * The client reaches the extended metrics only through
 * {@code com.sun.management.OperatingSystemMXBean}, which it obtains behind an
 * {@code instanceof} check; the port's bean deliberately implements only this
 * interface and is not an instance of that subtype, so those extended metrics
 * stay unreachable instead of being answered with lies.
 */
public interface OperatingSystemMXBean {
    /** Operating system name, e.g. {@code Linux}. */
    String getName();

    /** Operating system architecture, e.g. {@code aarch64}. */
    String getArch();

    /** Operating system version, e.g. the kernel release. */
    String getVersion();

    /** Number of processors available to this process. */
    int getAvailableProcessors();

    /** System load average, or -1 when the platform cannot report one. */
    double getSystemLoadAverage();
}
