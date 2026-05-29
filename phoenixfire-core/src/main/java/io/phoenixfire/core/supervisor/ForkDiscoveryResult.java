package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.TestId;

import java.util.List;

/** Result of running a DISCOVER pass in a fork. */
public final class ForkDiscoveryResult {

    private final List<TestId> discovered;
    private final boolean clean;
    private final FailureMode failureMode;
    private final int exitCode;
    private final String diagnostic;

    public ForkDiscoveryResult(List<TestId> discovered, boolean clean, FailureMode failureMode,
                               int exitCode, String diagnostic) {
        this.discovered = discovered;
        this.clean = clean;
        this.failureMode = failureMode;
        this.exitCode = exitCode;
        this.diagnostic = diagnostic;
    }

    public List<TestId> discovered() {
        return discovered;
    }

    public boolean clean() {
        return clean;
    }

    public FailureMode failureMode() {
        return failureMode;
    }

    public int exitCode() {
        return exitCode;
    }

    public String diagnostic() {
        return diagnostic;
    }
}
