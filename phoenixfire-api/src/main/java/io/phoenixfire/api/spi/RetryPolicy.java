package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.TestRecord;

/**
 * Pluggable policy that decides whether and how a failed/crashed test should be retried.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}. The default policy
 * escalates isolation on infrastructure failures and re-runs deterministic assertion failures at
 * the same level up to a configured limit, always enforcing global retry bounds to avoid infinite
 * loops.
 */
public interface RetryPolicy {

    /**
     * Decide what to do with a test after its most recent attempt.
     *
     * @param record  the current journal record (including full attempt history)
     * @param context details of the most recent attempt
     * @return a {@link RetryDecision}
     */
    RetryDecision decide(TestRecord record, FailureContext context);
}
