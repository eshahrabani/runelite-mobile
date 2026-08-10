package org.runelite.mobile;

import java.util.Optional;

public interface ProcessHandle extends Comparable<ProcessHandle> {
    
    Info info();
    
    Optional<ProcessHandle> parent();

    static ProcessHandle current() {
        return ProcessHandleImpl.getInstance();
    }

    interface Info {
        Optional<String> command();
    }
}
