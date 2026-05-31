package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.api.spi.RetryPolicy;

/** Test SPI that never retries failed tests. */
public final class DeclineRetryPolicy implements RetryPolicy {

    @Override
    public RetryDecision decide(TestRecord record, FailureContext context) {
        return RetryDecision.noRetry();
    }
}
