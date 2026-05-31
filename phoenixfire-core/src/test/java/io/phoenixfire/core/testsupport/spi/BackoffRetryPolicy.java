package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.api.spi.RetryPolicy;

/** Test SPI that retries once with a short backoff. */
public final class BackoffRetryPolicy implements RetryPolicy {

    @Override
    public RetryDecision decide(TestRecord record, FailureContext context) {
        if (context.outcome().isSuccessful() || context.attemptsSoFar() >= 2) {
            return RetryDecision.noRetry();
        }
        return RetryDecision.retryAt(context.currentLevel(), 10L);
    }
}
