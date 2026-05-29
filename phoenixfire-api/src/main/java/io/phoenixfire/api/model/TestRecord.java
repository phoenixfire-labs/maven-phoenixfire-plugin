package io.phoenixfire.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Durable, per-test execution record held by the controller's journal.
 *
 * <p>Tracks the current {@link TestState}, the full audit trail of {@link ExecutionAttempt}s, and
 * the isolation level to use for the next attempt. Instances are mutated only by the journal, which
 * serialises access and enforces legal state transitions.
 */
public final class TestRecord {

    private final TestId testId;
    private final List<ExecutionAttempt> attempts = new ArrayList<>();
    private TestState state = TestState.NOT_RUN;
    private IsolationLevel targetLevel = IsolationLevel.SHARED_FORK_POOL;
    private FailureMode lastFailureMode = FailureMode.NONE;

    public TestRecord(TestId testId) {
        this.testId = testId;
    }

    public TestId testId() {
        return testId;
    }

    public TestState state() {
        return state;
    }

    void setState(TestState state) {
        this.state = state;
    }

    public IsolationLevel targetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(IsolationLevel targetLevel) {
        this.targetLevel = targetLevel;
    }

    public FailureMode lastFailureMode() {
        return lastFailureMode;
    }

    void setLastFailureMode(FailureMode lastFailureMode) {
        this.lastFailureMode = lastFailureMode;
    }

    public List<ExecutionAttempt> attempts() {
        return Collections.unmodifiableList(attempts);
    }

    public int attemptCount() {
        return attempts.size();
    }

    /** The most recent attempt, or {@code null} if the test has never run. */
    public ExecutionAttempt lastAttempt() {
        return attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);
    }

    /**
     * True if this test ultimately succeeded but only after one or more failed/crashed attempts -
     * i.e. it crashed or failed initially and was recovered by an escalated retry. Such "flaky"
     * tests do not fail the build by default, but are reported so they remain visible.
     */
    public boolean recovered() {
        if (!state.isSuccessful()) {
            return false;
        }
        for (ExecutionAttempt attempt : attempts) {
            TestState outcome = attempt.outcome();
            if (outcome == TestState.FAILED || outcome == TestState.CRASHED) {
                return true;
            }
        }
        return false;
    }

    /** True if any attempt of this test crashed (infrastructure failure), regardless of final state. */
    public boolean everCrashed() {
        for (ExecutionAttempt attempt : attempts) {
            if (attempt.outcome() == TestState.CRASHED) {
                return true;
            }
        }
        return false;
    }

    /** Isolation level of the first attempt that failed or crashed, or {@code null} if none did. */
    public IsolationLevel firstFailLevel() {
        for (ExecutionAttempt attempt : attempts) {
            TestState outcome = attempt.outcome();
            if (outcome == TestState.FAILED || outcome == TestState.CRASHED) {
                return attempt.isolationLevel();
            }
        }
        return null;
    }

    /**
     * Isolation level at which this test finally succeeded after having previously failed/crashed,
     * or {@code null} if it did not recover. This is the level that "rescued" the test.
     */
    public IsolationLevel recoveryLevel() {
        if (!recovered()) {
            return null;
        }
        ExecutionAttempt last = lastAttempt();
        return last == null ? null : last.isolationLevel();
    }

    /**
     * True if this test failed/crashed while running in a reused pooled fork
     * ({@link IsolationLevel#SHARED_FORK_POOL}) but then succeeded once given a more isolated fork.
     * This is the signature of a fork-reuse / state-pollution sensitivity: green in isolation, broken
     * when sharing a JVM with other tests.
     */
    public boolean forkReuseSensitive() {
        if (!recovered()) {
            return false;
        }
        boolean failedUnderReuse = false;
        for (ExecutionAttempt attempt : attempts) {
            TestState outcome = attempt.outcome();
            if (attempt.isolationLevel() == IsolationLevel.SHARED_FORK_POOL
                    && (outcome == TestState.FAILED || outcome == TestState.CRASHED)) {
                failedUnderReuse = true;
            }
        }
        IsolationLevel recoveryLevel = recoveryLevel();
        return failedUnderReuse && recoveryLevel != null
                && recoveryLevel.ordinal() > IsolationLevel.SHARED_FORK_POOL.ordinal();
    }

    void addAttempt(ExecutionAttempt attempt) {
        attempts.add(attempt);
        this.lastFailureMode = attempt.failureMode();
    }

    // Package-private mutators are exposed to the journal via these public hooks; the journal is the
    // only component expected to call setState/addAttempt and does so under its own lock.
    public void internalSetState(TestState state) {
        setState(state);
    }

    public void internalAddAttempt(ExecutionAttempt attempt) {
        addAttempt(attempt);
    }

    public void internalSetLastFailureMode(FailureMode mode) {
        setLastFailureMode(mode);
    }
}
