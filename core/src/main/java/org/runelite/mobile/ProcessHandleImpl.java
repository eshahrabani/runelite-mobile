package org.runelite.mobile;

import java.util.Optional;

public class ProcessHandleImpl implements ProcessHandle {
    private static final ProcessHandleImpl INSTANCE = new ProcessHandleImpl();

    public static ProcessHandleImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public Info info() {
        return new Info() {
            @Override
            public Optional<String> command() {
                return Optional.of("runelite");
            }
        };
    }

    @Override
    public Optional<ProcessHandle> parent() {
        return Optional.empty();
    }

    @Override
    public int compareTo(ProcessHandle other) {
        return 0;
    }
}
