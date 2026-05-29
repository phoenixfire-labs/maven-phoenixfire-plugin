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
        TestRecord record = new TestRecord(new TestId("id", "Cls", "test()"));
        for (int i = 0; i < count; i++) {
            record.internalAddAttempt(ExecutionAttempt.builder()
                    .attemptNumber(i + 1).outcome(outcome).failureMode(mode).build());
        }
        return record;
    }

    @Test
    void crashedTestNeverRetriesInSharedPool() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(3, 0, 0L, LADDER);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.SIGKILL);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.SIGKILL, IsolationLevel.SHARED_FORK_POOL, 1, 137));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.FRESH_FORK, decision.nextLevel());
    }

    @Test
    void crashedTestEscalatesAlongLadder() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 0, 0L, LADDER);
        TestRecord record = recordWithAttempts(1, TestState.CRASHED, FailureMode.HEARTBEAT_TIMEOUT);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.HEARTBEAT_TIMEOUT,
                        IsolationLevel.FRESH_FORK, 1, -1));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, decision.nextLevel());
    }

    @Test
    void stopsRetryingAtMaxAttempts() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(2, 5, 0L, LADDER);
        TestRecord record = recordWithAttempts(2, TestState.CRASHED, FailureMode.OOM);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.CRASHED, FailureMode.OOM, IsolationLevel.FRESH_FORK, 2, 137));

        assertFalse(decision.shouldRetry());
    }

    @Test
    void deterministicFailureRerunsAtSameLevelWithinCount() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 2, 0L, LADDER);
        TestRecord record = recordWithAttempts(1, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 1, 0));

        assertTrue(decision.shouldRetry());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, decision.nextLevel());
    }

    @Test
    void deterministicFailureStopsAfterRerunCount() {
        DefaultRetryPolicy policy = new DefaultRetryPolicy(5, 1, 0L, LADDER);
        TestRecord record = recordWithAttempts(2, TestState.FAILED, FailureMode.ASSERTION_FAILURE);

        RetryDecision decision = policy.decide(record,
                new FailureContext(TestState.FAILED, FailureMode.ASSERTION_FAILURE,
                        IsolationLevel.SHARED_FORK_POOL, 2, 0));

        assertFalse(decision.shouldRetry());
    }
}
