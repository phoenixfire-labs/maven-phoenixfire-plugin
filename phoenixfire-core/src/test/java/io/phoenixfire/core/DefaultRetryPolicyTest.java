package io.phoenixfire.core;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.core.retry.DefaultRetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRetryPolicyTest {

    private static final List<IsolationLevel> LADDER = List.of(
            IsolationLevel.SHARED_FORK_POOL,
            IsolationLevel.FRESH_FORK,
            IsolationLevel.ONE_FORK_PER_CLASS);

    private TestRecord recordWithAttempts(int count, TestState outcome, FailureMode mode) {
        return recordWithAttempts(count, outcome, mode, IsolationLevel.SHARED_FORK_POOL);
    }

    private TestRecord recordWithAttempts(int count, TestState outcome, FailureMode mode, IsolationLevel level) {
        TestRecord record = new TestRecord(new TestId("id", "Cls", "test()"));
        for (int i = 0; i < count; i++) {
            record.internalAddAttempt(ExecutionAttempt.builder()
                    .attemptNumber(i + 1).isolationLevel(level).outcome(outcome).failureMode(mode).build());
        }
        return record;
    }

    @Test
    void crashedTestNeverRetriesInSharedPoolByDefault() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(3, 0, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.SIGKILL);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL, IsolationLevel.SHARED_FORK_POOL, 1, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.FRESH_FORK, decision.nextLevel());
    }

    @Test
    void sharedCrashResumesInSharedPoolWhilePassesRemain() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 2);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.SIGKILL);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL, IsolationLevel.SHARED_FORK_POOL, 1, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, decision.nextLevel());
    }

    @Test
    void sharedCrashEscalatesAfterPassesExhausted() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 2);
        TestRecord record = recordWithAttempts(2, TestState.CRASHED, FailureMode.SIGKILL);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL, IsolationLevel.SHARED_FORK_POOL, 2, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.FRESH_FORK, decision.nextLevel());
    }

    @Test
    void failureInCrashedForkEscalatesToIsolation() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 1, 137, true));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.FRESH_FORK, decision.nextLevel());
    }

    @Test
    void failureInCleanForkDoesNotEscalateByDefault() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 1, 0, false));

        assertFalse(decision.shouldRetry());
    }

    @Test
    void crashedTestEscalatesAlongLadder() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.HEARTBEAT_TIMEOUT,
                IsolationLevel.FRESH_FORK);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.HEARTBEAT_TIMEOUT,
                        IsolationLevel.FRESH_FORK, 1, -1));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, decision.nextLevel());
    }

    @Test
    void stopsRetryingAtMaxAttempts() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(2, 5, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(2, TestState.CRASHED, FailureMode.OOM, IsolationLevel.FRESH_FORK);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.OOM, IsolationLevel.FRESH_FORK, 2, 137));

        assertFalse(decision.shouldRetry());
    }

    @Test
    void deterministicFailureRerunsAtSameLevelWithinCount() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 2, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 1, 0));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, decision.nextLevel());
    }

    @Test
    void deterministicFailureStopsAfterRerunCount() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 1, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(2, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 2, 0));

        assertFalse(decision.shouldRetry());
    }

    @Test
    void successfulOutcomeNeverRetries() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(3, 1, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.PASSED, FailureMode.NONE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.PASSED, FailureMode.NONE,
                        IsolationLevel.SHARED_FORK_POOL, 1, 0));

        assertFalse(decision.shouldRetry());
    }

    @Test
    void appliesBackoffMillisOnRetry() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 500L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.SIGKILL);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL,
                        IsolationLevel.SHARED_FORK_POOL, 1, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(500L, decision.backoffMillis());
    }

    @Test
    void escalatesToFreshForkWhenLadderReturnsSharedPool() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L,
                List.of(IsolationLevel.FRESH_FORK, IsolationLevel.SHARED_FORK_POOL), 1);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.SIGKILL,
                IsolationLevel.FRESH_FORK);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL,
                        IsolationLevel.FRESH_FORK, 1, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.FRESH_FORK, decision.nextLevel());
    }

    @Test
    void staysAtTopOfLadderWhenAlreadyMaximum() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(2, TestState.CRASHED, FailureMode.SIGKILL,
                IsolationLevel.ONE_FORK_PER_CLASS);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL,
                        IsolationLevel.ONE_FORK_PER_CLASS, 2, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, decision.nextLevel());
    }

    @Test
    void escalatesUnknownLevelViaEnumNext() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, List.of(), 1);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.UNKNOWN,
                IsolationLevel.FRESH_FORK);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.UNKNOWN,
                        IsolationLevel.FRESH_FORK, 1, 1));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, decision.nextLevel());
    }

    @Test
    void deterministicFailureRetriesOnFinalAllowedAttempt() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 2, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(2, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 2, 0, false));

        assertTrue(decision.shouldRetry());
    }

    @Test
    void infrastructureFailureRetriesWithoutAbnormalForkFlag() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER, 1);
        TestRecord record = recordWithAttempts(1, TestState.FAILED, FailureMode.OOM);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.OOM,
                        IsolationLevel.SHARED_FORK_POOL, 1, 137, false));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.FRESH_FORK, decision.nextLevel());
    }
}
