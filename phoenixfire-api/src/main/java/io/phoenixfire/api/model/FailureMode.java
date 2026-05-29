package io.phoenixfire.api.model;

/**
 * Why a test attempt did not complete normally.
 *
 * <p>{@link #ASSERTION_FAILURE} is a deterministic, in-JVM test failure. Everything else describes
 * an infrastructure failure of the forked JVM, which generally warrants escalating isolation rather
 * than re-running in a potentially poisoned JVM.
 */
public enum FailureMode {
    /** No failure (the attempt completed normally). */
    NONE,
    /** A normal test failure: assertion error or thrown exception inside the test. */
    ASSERTION_FAILURE,
    /** The fork exited with a non-zero status that is not a recognised signal. */
    ABNORMAL_EXIT,
    /** The fork was forcibly killed (e.g. SIGKILL / exit 137). */
    SIGKILL,
    /** The fork was terminated (e.g. SIGTERM / exit 143). */
    SIGTERM,
    /** The fork ran out of memory. */
    OOM,
    /** No heartbeat was received within the configured timeout (deadlock / hang). */
    HEARTBEAT_TIMEOUT,
    /** The fork never completed the IPC handshake (classloading failure / fork instability). */
    HANDSHAKE_FAILURE,
    /** Failure mode could not be determined. */
    UNKNOWN;

    /** True when the failure is an infrastructure problem rather than a deterministic test failure. */
    public boolean isInfrastructureFailure() {
        return this != NONE && this != ASSERTION_FAILURE;
    }
}
