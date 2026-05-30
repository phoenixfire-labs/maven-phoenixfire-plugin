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
        FailureContext ctx = new FailureContext(
                TestState.CRASHED, FailureMode.SIGKILL, IsolationLevel.FRESH_FORK, 1, 137, true);
        assertTrue(ctx.forkTerminatedAbnormally());
        assertFalse(RetryDecision.noRetry().shouldRetry());
        RetryDecision retry = RetryDecision.retryAt(IsolationLevel.ONE_FORK_PER_CLASS, -1);
        assertTrue(retry.shouldRetry());
        assertEquals(0L, retry.backoffMillis());
    }
}
