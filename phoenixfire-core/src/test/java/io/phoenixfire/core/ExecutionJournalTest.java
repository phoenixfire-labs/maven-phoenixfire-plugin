package io.phoenixfire.core;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void forceTerminalIsNoOpWhenAlreadyTerminal(@TempDir Path tempDir) {
        ExecutionJournal journal = newJournal();
        TestId a = id("a");
        journal.seed(List.of(a));
        journal.markRunning(a, "fork-1");
        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.PASSED).build());

        journal.forceTerminal(a, FailureMode.UNKNOWN, "ignored");
        assertEquals(TestState.PASSED, journal.record(a).state());
        assertEquals(1, journal.record(a).attemptCount());
    }

    @Test
    void enableJournalFileWritesNdjsonEvents(@TempDir Path tempDir) throws Exception {
        ExecutionJournal journal = newJournal();
        Path journalPath = tempDir.resolve("journal.ndjson");
        journal.enableJournalFile(journalPath);

        TestId a = id("a");
        journal.seed(List.of(a));
        journal.markRunning(a, "fork-1");
        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.PASSED).build());
        journal.close();

        String content = Files.readString(journalPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"event\":\"SEED\""));
        assertTrue(content.contains("\"event\":\"RUNNING\""));
        assertTrue(content.contains("\"event\":\"PASSED\""));
    }

    @Test
    void enableJournalFileUncheckedAcceptsValidPath(@TempDir Path tempDir) {
        ExecutionJournal journal = newJournal();
        journal.enableJournalFileUnchecked(tempDir.resolve("journal.ndjson"));
        journal.seed(List.of(id("a")));
        journal.close();
        assertTrue(Files.exists(tempDir.resolve("journal.ndjson")));
    }

    @Test
    void enableJournalFileSurvivesBadPath(@TempDir Path tempDir) throws Exception {
        ExecutionJournal journal = newJournal();
        Path parent = tempDir.resolve("parent-file");
        Files.writeString(parent, "not-a-directory");
        journal.enableJournalFile(parent.resolve("journal.ndjson"));
        journal.seed(List.of(id("a")));
        assertEquals(1, journal.size());
    }

    @Test
    void enableJournalFileUncheckedSurvivesBadPath(@TempDir Path tempDir) throws Exception {
        ExecutionJournal journal = newJournal();
        Path parent = tempDir.resolve("parent-file");
        Files.writeString(parent, "not-a-directory");
        journal.enableJournalFileUnchecked(parent.resolve("journal.ndjson"));
        journal.seed(List.of(id("a")));
        assertEquals(1, journal.size());
    }

    @Test
    void appendFailureClosesWriter(@TempDir Path tempDir) throws Exception {
        ExecutionJournal journal = newJournal();
        Path journalPath = tempDir.resolve("journal.ndjson");
        journal.enableJournalFile(journalPath);
        journal.seed(List.of(id("a")));
        Field writerField = ExecutionJournal.class.getDeclaredField("journalWriter");
        writerField.setAccessible(true);
        BufferedWriter failing = new BufferedWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("write failed");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws IOException {
                throw new IOException("nested close failed");
            }
        });
        writerField.set(journal, failing);
        journal.markRunning(id("a"), "fork-1");
        journal.close();
    }

    @Test
    void appendFailureDisablesJournalWriter(@TempDir Path tempDir) throws Exception {
        ExecutionJournal journal = newJournal();
        Path journalPath = tempDir.resolve("journal.ndjson");
        journal.enableJournalFile(journalPath);
        TestId a = id("a");
        journal.seed(List.of(a));

        Files.delete(journalPath);
        Files.createDirectory(journalPath);

        journal.markRunning(a, "fork-1");
        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.PASSED).build());
        journal.close();
    }

    @Test
    void closeWarnsOnJournalErrors(@TempDir Path tempDir) throws Exception {
        ExecutionJournal journal = newJournal();
        Path journalPath = tempDir.resolve("journal.ndjson");
        journal.enableJournalFile(journalPath);
        journal.seed(List.of(id("a")));
        Field writerField = ExecutionJournal.class.getDeclaredField("journalWriter");
        writerField.setAccessible(true);
        writerField.set(journal, new BufferedWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) {
            }

            @Override
            public void flush() throws IOException {
                throw new IOException("flush failed");
            }

            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        }));
        journal.close();
    }

    @Test
    void warnsOnIllegalTransition() {
        ExecutionJournal journal = newJournal();
        TestId a = id("a");
        journal.seed(List.of(a));
        journal.markRunning(a, "fork-1");
        journal.recordAttempt(a, ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.PASSED).build());

        journal.scheduleRetry(a, IsolationLevel.FRESH_FORK);
        assertEquals(TestState.NOT_RUN, journal.record(a).state());
    }

    @Test
    void enableJournalFileNullIsNoOp() {
        ExecutionJournal journal = newJournal();
        journal.enableJournalFile(null);
        journal.seed(List.of(id("a")));
        assertEquals(1, journal.size());
    }

    @Test
    void enableJournalFileSurvivesMissingParent(@TempDir Path tempDir) {
        ExecutionJournal journal = newJournal();
        journal.enableJournalFile(tempDir.resolve("missing").resolve("parent").resolve("journal.ndjson"));
        journal.seed(List.of(id("a")));
        assertEquals(1, journal.size());
    }

    @Test
    void transitionToSameStateIsNoOp() {
        ExecutionJournal journal = newJournal();
        TestId a = id("a");
        journal.seed(List.of(a));
        journal.markRunning(a, "fork-1");
        journal.markRunning(a, "fork-1");
        assertEquals(TestState.RUNNING, journal.record(a).state());
    }

    @Test
    void requireAddsLateDiscoveredTest() {
        ExecutionJournal journal = newJournal();
        TestId late = id("late");
        journal.markRunning(late, "fork-x");
        assertEquals(1, journal.size());
        assertEquals(TestState.RUNNING, journal.record(late).state());
    }
}
