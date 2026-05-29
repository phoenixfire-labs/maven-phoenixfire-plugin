package io.phoenixfire.api.model;

/**
 * An immutable record of a single execution attempt of a test, including the isolation level it ran
 * at, the outcome, and (for failures) the failure mode and captured throwable details.
 */
public final class ExecutionAttempt {

    private final int attemptNumber;
    private final IsolationLevel isolationLevel;
    private final TestState outcome;
    private final FailureMode failureMode;
    private final String forkId;
    private final long startMillis;
    private final long endMillis;
    private final String throwableMessage;
    private final String throwableStackTrace;

    private ExecutionAttempt(Builder b) {
        this.attemptNumber = b.attemptNumber;
        this.isolationLevel = b.isolationLevel;
        this.outcome = b.outcome;
        this.failureMode = b.failureMode == null ? FailureMode.NONE : b.failureMode;
        this.forkId = b.forkId;
        this.startMillis = b.startMillis;
        this.endMillis = b.endMillis;
        this.throwableMessage = b.throwableMessage;
        this.throwableStackTrace = b.throwableStackTrace;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public IsolationLevel isolationLevel() {
        return isolationLevel;
    }

    public TestState outcome() {
        return outcome;
    }

    public FailureMode failureMode() {
        return failureMode;
    }

    public String forkId() {
        return forkId;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    public long durationMillis() {
        return endMillis >= startMillis ? endMillis - startMillis : 0L;
    }

    public String throwableMessage() {
        return throwableMessage;
    }

    public String throwableStackTrace() {
        return throwableStackTrace;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder for {@link ExecutionAttempt}. */
    public static final class Builder {
        private int attemptNumber;
        private IsolationLevel isolationLevel = IsolationLevel.SHARED_FORK_POOL;
        private TestState outcome = TestState.NOT_RUN;
        private FailureMode failureMode = FailureMode.NONE;
        private String forkId;
        private long startMillis;
        private long endMillis;
        private String throwableMessage;
        private String throwableStackTrace;

        public Builder attemptNumber(int attemptNumber) {
            this.attemptNumber = attemptNumber;
            return this;
        }

        public Builder isolationLevel(IsolationLevel isolationLevel) {
            this.isolationLevel = isolationLevel;
            return this;
        }

        public Builder outcome(TestState outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder failureMode(FailureMode failureMode) {
            this.failureMode = failureMode;
            return this;
        }

        public Builder forkId(String forkId) {
            this.forkId = forkId;
            return this;
        }

        public Builder startMillis(long startMillis) {
            this.startMillis = startMillis;
            return this;
        }

        public Builder endMillis(long endMillis) {
            this.endMillis = endMillis;
            return this;
        }

        public Builder throwableMessage(String throwableMessage) {
            this.throwableMessage = throwableMessage;
            return this;
        }

        public Builder throwableStackTrace(String throwableStackTrace) {
            this.throwableStackTrace = throwableStackTrace;
            return this;
        }

        public ExecutionAttempt build() {
            return new ExecutionAttempt(this);
        }
    }
}
