package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.api.spi.RetryPolicy;

/** Test SPI that escalates from the shared pool to a fresh fork with backoff. */
public final class EscalatingRetryPolicy implements RetryPolicy {

    @Override
    public RetryDecision decide(TestRecord record, FailureContext context) {
        if (context.outcome().isSuccessful()) {
            return RetryDecision.noRetry();
        }
        if (context.currentLevel() == IsolationLevel.SHARED_FORK_POOL) {
            return RetryDecision.retryAt(IsolationLevel.FRESH_FORK, 5L);
        }
        return RetryDecision.noRetry();
    }
}
