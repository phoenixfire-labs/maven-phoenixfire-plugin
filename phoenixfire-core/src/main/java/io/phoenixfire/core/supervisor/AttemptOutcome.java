package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.model.TestState;

/**
 * The outcome of a single test as reported by a fork (PASSED / FAILED / SKIPPED), including timing
 * and any captured throwable details.
 */
public final class AttemptOutcome {

    private final TestState state;
    private final String failureMessage;
    private final String stackTrace;
    private final long durationMillis;

    public AttemptOutcome(TestState state, String failureMessage, String stackTrace, long durationMillis) {
        this.state = state;
        this.failureMessage = failureMessage;
        this.stackTrace = stackTrace;
        this.durationMillis = durationMillis;
    }

    public TestState state() {
        return state;
    }

    public String failureMessage() {
        return failureMessage;
    }

    public String stackTrace() {
        return stackTrace;
    }

    public long durationMillis() {
        return durationMillis;
    }
}
