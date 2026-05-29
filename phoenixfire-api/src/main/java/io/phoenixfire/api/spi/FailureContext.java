package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestState;

/**
 * Context supplied to a {@link RetryPolicy} describing the most recent attempt of a test.
 */
public final class FailureContext {

    private final TestState outcome;
    private final FailureMode failureMode;
    private final IsolationLevel currentLevel;
    private final int attemptsSoFar;
    private final int exitCode;

    public FailureContext(TestState outcome,
                          FailureMode failureMode,
                          IsolationLevel currentLevel,
                          int attemptsSoFar,
                          int exitCode) {
        this.outcome = outcome;
        this.failureMode = failureMode;
        this.currentLevel = currentLevel;
        this.attemptsSoFar = attemptsSoFar;
        this.exitCode = exitCode;
    }

    public TestState outcome() {
        return outcome;
    }

    public FailureMode failureMode() {
        return failureMode;
    }

    public IsolationLevel currentLevel() {
        return currentLevel;
    }

    public int attemptsSoFar() {
        return attemptsSoFar;
    }

    public int exitCode() {
        return exitCode;
    }
}
