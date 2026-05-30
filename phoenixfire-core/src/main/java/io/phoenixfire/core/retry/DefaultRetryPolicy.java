package io.phoenixfire.core.retry;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.api.spi.RetryPolicy;

import java.util.List;

/**
 * Default retry/escalation policy.
 *
 * <ul>
 *   <li><b>Fork incidents</b> (crash, hang, OOM, abnormal exit, or any test failure in a fork that
 *       did not exit cleanly) are retried along the isolation ladder. At {@link IsolationLevel#SHARED_FORK_POOL},
 *       {@code sharedForkPoolMaxPasses} controls how many shared-pool attempts are allowed before
 *       escalating to a clean isolated fork.</li>
 *   <li><b>Clean-fork assertion failures</b> are only re-run at the same level up to
 *       {@code rerunFailingTestsCount} (Surefire parity).</li>
 *   <li>All paths are bounded by {@code maxAttempts}.</li>
 * </ul>
 */
public final class DefaultRetryPolicy implements RetryPolicy {

    private final int maxAttempts;
    private final int rerunFailingTestsCount;
    private final long backoffMillis;
    private final List<IsolationLevel> ladder;
    private final int sharedForkPoolMaxPasses;

    public DefaultRetryPolicy(int maxAttempts, int rerunFailingTestsCount, long backoffMillis,
                              List<IsolationLevel> ladder, int sharedForkPoolMaxPasses) {
        this.maxAttempts = maxAttempts;
        this.rerunFailingTestsCount = rerunFailingTestsCount;
        this.backoffMillis = backoffMillis;
        this.ladder = ladder;
        this.sharedForkPoolMaxPasses = Math.max(1, sharedForkPoolMaxPasses);
    }

    @Override
    public RetryDecision decide(TestRecord record, FailureContext context) {
        if (context.outcome().isSuccessful()) {
            return RetryDecision.noRetry();
        }
        if (context.attemptsSoFar() >= maxAttempts) {
            return RetryDecision.noRetry();
        }

        if (isForkIncident(context)) {
            if (context.currentLevel() == IsolationLevel.SHARED_FORK_POOL
                    && countAttemptsAt(record, IsolationLevel.SHARED_FORK_POOL) < sharedForkPoolMaxPasses) {
                return RetryDecision.retryAt(IsolationLevel.SHARED_FORK_POOL, backoffMillis);
            }
            IsolationLevel next = escalate(context.currentLevel());
            if (next == IsolationLevel.SHARED_FORK_POOL) {
                next = IsolationLevel.FRESH_FORK;
            }
            return RetryDecision.retryAt(next, backoffMillis);
        }

        if (context.outcome() == TestState.FAILED
                && context.attemptsSoFar() <= rerunFailingTestsCount) {
            return RetryDecision.retryAt(context.currentLevel(), backoffMillis);
        }
        return RetryDecision.noRetry();
    }

    /** Infrastructure failure, or a test failure in a fork that crashed or exited abnormally. */
    private static boolean isForkIncident(FailureContext context) {
        return context.failureMode().isInfrastructureFailure()
                || (context.outcome() == TestState.FAILED && context.forkTerminatedAbnormally());
    }

    private static int countAttemptsAt(TestRecord record, IsolationLevel level) {
        int count = 0;
        for (ExecutionAttempt a : record.attempts()) {
            if (a.isolationLevel() == level) {
                count++;
            }
        }
        return count;
    }

    private IsolationLevel escalate(IsolationLevel current) {
        int idx = ladder.indexOf(current);
        if (idx < 0) {
            return current.next();
        }
        if (idx + 1 >= ladder.size()) {
            return ladder.get(ladder.size() - 1);
        }
        return ladder.get(idx + 1);
    }
}
