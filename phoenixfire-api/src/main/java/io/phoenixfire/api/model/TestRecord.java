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
