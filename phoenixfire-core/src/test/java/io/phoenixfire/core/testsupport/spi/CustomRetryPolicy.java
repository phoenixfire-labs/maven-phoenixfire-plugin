package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.api.spi.RetryPolicy;

/** Test SPI that always retries at the current isolation level. */
public final class CustomRetryPolicy implements RetryPolicy {

    @Override
    public RetryDecision decide(TestRecord record, FailureContext context) {
        if (context.outcome().isSuccessful()) {
            return RetryDecision.noRetry();
        }
        return RetryDecision.retryAt(context.currentLevel(), 0L);
    }
}
