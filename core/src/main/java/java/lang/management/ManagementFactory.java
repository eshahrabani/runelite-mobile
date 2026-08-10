package java.lang.management;

import java.util.Collections;
import java.util.List;

public class ManagementFactory {
    private static final RuntimeMXBean runtimeMXBean = new RuntimeMXBean() {
        @Override
        public List<String> getInputArguments() {
            return Collections.emptyList();
        }
    };

    public static RuntimeMXBean getRuntimeMXBean() {
        return runtimeMXBean;
    }
}
