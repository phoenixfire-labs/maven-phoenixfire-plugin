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
 *   <li><b>Infrastructure failures</b> (crash, hang, OOM, abnormal exit) escalate isolation along
 *       the configured ladder. By default a crashed test is <em>never</em> retried in the shared pool
 *       that poisoned it - it jumps to at least {@link IsolationLevel#FRESH_FORK}. When
 *       {@code sharedForkPoolMaxPasses} &gt; 1, the victims are first resumed in a <em>fresh</em>
 *       shared-pool fork up to that many shared attempts (treating an early crash as possibly
 *       transient) before paying for isolation.</li>
 *   <li><b>Deterministic assertion failures</b> are re-run at the same isolation level up to
 *       {@code rerunFailingTestsCount} times (Surefire parity).</li>
 *   <li>All paths are bounded by {@code maxAttempts} to guarantee termination.</li>
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

        if (context.failureMode().isInfrastructureFailure()) {
            // Optionally resume in a fresh shared-pool fork before escalating, treating an early crash
            // as possibly transient. The poisoned fork is dead; this is a brand-new shared-pool JVM.
            if (context.currentLevel() == IsolationLevel.SHARED_FORK_POOL
                    && countAttemptsAt(record, IsolationLevel.SHARED_FORK_POOL) < sharedForkPoolMaxPasses) {
                return RetryDecision.retryAt(IsolationLevel.SHARED_FORK_POOL, backoffMillis);
            }
            IsolationLevel next = escalate(context.currentLevel());
            // Never re-run a crashed test in the shared pool once shared passes are exhausted.
            if (next == IsolationLevel.SHARED_FORK_POOL) {
                next = IsolationLevel.FRESH_FORK;
            }
            return RetryDecision.retryAt(next, backoffMillis);
        }

        // Deterministic test failure: re-run at the same level up to the configured count.
        if (context.attemptsSoFar() <= rerunFailingTestsCount && context.outcome() == TestState.FAILED) {
            return RetryDecision.retryAt(context.currentLevel(), backoffMillis);
        }
        return RetryDecision.noRetry();
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
