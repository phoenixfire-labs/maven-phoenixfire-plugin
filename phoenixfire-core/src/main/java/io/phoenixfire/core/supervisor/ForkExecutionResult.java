package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.TestId;

import java.util.Map;
import java.util.Set;

/**
 * Result of running one EXECUTE work unit in a fork.
 *
 * <p>Captures completed test outcomes, the single test that was in-flight when the fork died (if
 * any), the tests that were assigned but never started, the classified failure mode, and the exit
 * code. The {@link #clean()} flag is true only for a normal completion (exit 0 with a BYE).
 */
public final class ForkExecutionResult {

    private final String forkId;
    private final Map<TestId, AttemptOutcome> outcomes;
    private final TestId inFlight;
    private final Set<TestId> notStarted;
    private final FailureMode failureMode;
    private final int exitCode;
    private final boolean clean;
    private final String diagnostic;

    public ForkExecutionResult(String forkId,
                               Map<TestId, AttemptOutcome> outcomes,
                               TestId inFlight,
                               Set<TestId> notStarted,
                               FailureMode failureMode,
                               int exitCode,
                               boolean clean,
                               String diagnostic) {
        this.forkId = forkId;
        this.outcomes = outcomes;
        this.inFlight = inFlight;
        this.notStarted = notStarted;
        this.failureMode = failureMode;
        this.exitCode = exitCode;
        this.clean = clean;
        this.diagnostic = diagnostic;
    }

    public String forkId() {
        return forkId;
    }

    public Map<TestId, AttemptOutcome> outcomes() {
        return outcomes;
    }

    public TestId inFlight() {
        return inFlight;
    }

    public Set<TestId> notStarted() {
        return notStarted;
    }

    public FailureMode failureMode() {
        return failureMode;
    }

    public int exitCode() {
        return exitCode;
    }

    public boolean clean() {
        return clean;
    }

    public String diagnostic() {
        return diagnostic;
    }
}
