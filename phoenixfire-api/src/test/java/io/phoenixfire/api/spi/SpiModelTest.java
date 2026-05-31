package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiModelTest {

    @Test
    void forkConfigEmptyAndCopies() {
        ForkConfig empty = ForkConfig.empty();
        assertTrue(empty.jvmArgs().isEmpty());
        ForkConfig cfg = new ForkConfig(List.of("-Xmx1g"), Map.of("a", "b"), Map.of("k", "v"));
        assertEquals("b", cfg.systemProperties().get("a"));
        assertEquals("v", cfg.environment().get("k"));
        assertTrue(new ForkConfig(null, null, null).jvmArgs().isEmpty());
    }

    @Test
    void workUnitDefaultsForkConfig() {
        WorkUnit unit = new WorkUnit("id", IsolationLevel.FRESH_FORK, List.of(
                new TestId("u", "C", "t")), null);
        assertTrue(unit.forkConfig().jvmArgs().isEmpty());
        assertTrue(unit.toString().contains("WorkUnit"));
    }

    @Test
    void failureContextAndRetryDecision() {
        FailureContext cleanFork = new FailureContext(
                TestState.FAILED, FailureMode.ASSERTION_FAILURE, IsolationLevel.SHARED_FORK_POOL, 1, 0);
        assertEquals(TestState.FAILED, cleanFork.outcome());
        assertEquals(FailureMode.ASSERTION_FAILURE, cleanFork.failureMode());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, cleanFork.currentLevel());
        assertEquals(1, cleanFork.attemptsSoFar());
        assertFalse(cleanFork.forkTerminatedAbnormally());

        FailureContext ctx = new FailureContext(
                TestState.CRASHED, FailureMode.SIGKILL, IsolationLevel.FRESH_FORK, 1, 137, true);
        assertEquals(TestState.CRASHED, ctx.outcome());
        assertTrue(ctx.forkTerminatedAbnormally());
        assertEquals(137, ctx.exitCode());
        assertFalse(RetryDecision.noRetry().shouldRetry());
        RetryDecision retry = RetryDecision.retryAt(IsolationLevel.ONE_FORK_PER_CLASS, -1);
        assertTrue(retry.shouldRetry());
        assertEquals(0L, retry.backoffMillis());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, retry.nextLevel());
        assertFalse(RetryDecision.noRetry().shouldRetry());
    }

    @Test
    void workUnitExposesFields() {
        TestId id = new TestId("u", "Cls", "t");
        WorkUnit unit = new WorkUnit("wid", IsolationLevel.FRESH_FORK, List.of(id),
                new ForkConfig(List.of("-ea"), Map.of(), Map.of()));
        assertEquals("wid", unit.id());
        assertEquals(IsolationLevel.FRESH_FORK, unit.isolationLevel());
        assertEquals(List.of(id), unit.tests());
        assertEquals("-ea", unit.forkConfig().jvmArgs().get(0));
    }
}
