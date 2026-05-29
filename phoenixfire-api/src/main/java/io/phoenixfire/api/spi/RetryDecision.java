package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.IsolationLevel;

/**
 * Outcome of a {@link RetryPolicy} evaluation: whether to retry, at which isolation level, and how
 * long to wait first.
 */
public final class RetryDecision {

    private static final RetryDecision NO_RETRY = new RetryDecision(false, null, 0L);

    private final boolean retry;
    private final IsolationLevel nextLevel;
    private final long backoffMillis;

    private RetryDecision(boolean retry, IsolationLevel nextLevel, long backoffMillis) {
        this.retry = retry;
        this.nextLevel = nextLevel;
        this.backoffMillis = backoffMillis;
    }

    public static RetryDecision noRetry() {
        return NO_RETRY;
    }

    public static RetryDecision retryAt(IsolationLevel nextLevel, long backoffMillis) {
        return new RetryDecision(true, nextLevel, Math.max(0L, backoffMillis));
    }

    public boolean shouldRetry() {
        return retry;
    }

    public IsolationLevel nextLevel() {
        return nextLevel;
    }

    public long backoffMillis() {
        return backoffMillis;
    }
}
