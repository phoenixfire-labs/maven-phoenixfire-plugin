package io.phoenixfire.api.model;

/**
 * Durable execution state of a single discovered test.
 *
 * <p>Every discovered test starts in {@link #NOT_RUN} and must eventually reach a terminal state
 * ({@link #PASSED}, {@link #FAILED}, {@link #CRASHED} or {@link #SKIPPED}). {@link #RUNNING} and
 * {@link #NOT_RUN} are non-terminal; a test may legally return to {@link #NOT_RUN} when scheduled
 * for a retry at a stronger isolation level.
 */
public enum TestState {
    NOT_RUN,
    RUNNING,
    PASSED,
    FAILED,
    CRASHED,
    SKIPPED;

    public boolean isTerminal() {
        return this == PASSED || this == FAILED || this == CRASHED || this == SKIPPED;
    }

    /** True if the outcome represents a successful or intentionally-not-run test. */
    public boolean isSuccessful() {
        return this == PASSED || this == SKIPPED;
    }
}
