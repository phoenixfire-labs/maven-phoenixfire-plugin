package io.phoenixfire.core;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionJournalTest {

    private ExecutionJournal newJournal() {
        return new ExecutionJournal(PhoenixfireLogger.console());
    }

    private TestId id(String s) {
        return new TestId(s, "Cls", s);
    }

    @Test
    void seedsAllTestsAsNotRun() {
        ExecutionJournal journal = newJournal();
        journal.seed(List.of(id("a"), id("b")));

        assertEquals(2, journal.size());
        assertEquals(2, journal.testsInState(TestState.NOT_RUN).size());
        assertFalse(journal.allTerminal());
    }

    @Test
    void recordsHappyPathTransition() {
        ExecutionJournal journal = newJournal();
        TestId a = id("a");
        journal.seed(List.of(a));

        journal.markRunning(a, "fork-1");
        assertEquals(TestState.RUNNING, journal.record(a).state());

        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.PASSED).build());

        assertEquals(TestState.PASSED, journal.record(a).state());
        assertTrue(journal.allTerminal());
    }

    @Test
    void supportsRetryReopeningAfterCrash() {
        ExecutionJournal journal = newJournal();
        TestId a = id("a");
        journal.seed(List.of(a));

        journal.markRunning(a, "fork-1");
        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.CRASHED).failureMode(FailureMode.SIGKILL).build());
        assertEquals(TestState.CRASHED, journal.record(a).state());

        journal.scheduleRetry(a, IsolationLevel.FRESH_FORK);
        assertEquals(TestState.NOT_RUN, journal.record(a).state());
        assertEquals(IsolationLevel.FRESH_FORK, journal.record(a).targetLevel());

        journal.markRunning(a, "fork-2");
        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(2).isolationLevel(IsolationLevel.FRESH_FORK)
                .outcome(TestState.PASSED).build());
        assertEquals(TestState.PASSED, journal.record(a).state());
        assertEquals(2, journal.record(a).attemptCount());
    }

    @Test
    void forceTerminalGuaranteesAccounting() {
        ExecutionJournal journal = newJournal();
        TestId a = id("a");
        journal.seed(List.of(a));

        journal.forceTerminal(a, FailureMode.UNKNOWN, "never reached terminal");

        assertEquals(TestState.CRASHED, journal.record(a).state());
        assertTrue(journal.allTerminal());
        assertEquals(1, journal.snapshot().count(TestState.CRASHED));
    }
}
