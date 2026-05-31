package io.phoenixfire.core.journal;

import io.phoenixfire.api.json.Json;
import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Authoritative, in-memory execution state for a Phoenixfire run.
 *
 * <p>The journal is the single source of truth while the controller (Maven JVM) is running: forked
 * JVMs are disposable and may die at any time, but orchestration state lives here. State transitions
 * are validated against the per-test state machine. When enabled, each mutation is also appended to
 * {@code journal.ndjson} as an audit timeline (forensics, ordering analysis); that file is not read
 * back to continue a run, and a new invocation truncates it.
 */
public final class ExecutionJournal {

    private static final Map<TestState, Set<TestState>> ALLOWED_TRANSITIONS = buildTransitions();

    private final Map<TestId, TestRecord> records = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final PhoenixfireLogger log;

    private BufferedWriter journalWriter;
    private long startMillis;

    public ExecutionJournal(PhoenixfireLogger log) {
        this.log = log;
    }

    /** Enable append-only NDJSON audit logging to {@code journalPath}. Failures are non-fatal. */
    public void enableJournalFile(Path journalPath) {
        lock.lock();
        try {
            if (journalPath == null) {
                return;
            }
            Files.createDirectories(journalPath.getParent());
            this.journalWriter = Files.newBufferedWriter(journalPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("Could not open journal file, continuing in-memory only: " + e.getMessage());
            this.journalWriter = null;
        } finally {
            lock.unlock();
        }
    }

    /** Seed the journal with the full discovered set; all tests start {@link TestState#NOT_RUN}. */
    public void seed(Collection<TestId> tests) {
        lock.lock();
        try {
            this.startMillis = System.currentTimeMillis();
            for (TestId id : tests) {
                records.computeIfAbsent(id, TestRecord::new);
            }
            appendEvent("SEED", null, "count=" + tests.size());
        } finally {
            lock.unlock();
        }
    }

    public void markRunning(TestId id, String forkId) {
        lock.lock();
        try {
            TestRecord record = require(id);
            transition(record, TestState.RUNNING);
            appendEvent("RUNNING", id, "fork=" + forkId);
        } finally {
            lock.unlock();
        }
    }

    /** Record the outcome of an attempt and transition to its terminal-ish state. */
    public void recordAttempt(TestId id, ExecutionAttempt attempt) {
        lock.lock();
        try {
            TestRecord record = require(id);
            record.internalAddAttempt(attempt);
            transition(record, attempt.outcome());
            appendEvent(attempt.outcome().name(), id,
                    "attempt=" + attempt.attemptNumber()
                            + ",level=" + attempt.isolationLevel()
                            + ",failure=" + attempt.failureMode());
        } finally {
            lock.unlock();
        }
    }

    /** Schedule a test for retry: return it to {@link TestState#NOT_RUN} at {@code nextLevel}. */
    public void scheduleRetry(TestId id, IsolationLevel nextLevel) {
        lock.lock();
        try {
            TestRecord record = require(id);
            transition(record, TestState.NOT_RUN);
            record.setTargetLevel(nextLevel);
            appendEvent("RETRY_SCHEDULED", id, "nextLevel=" + nextLevel);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Force a test that could never be completed (retry limits exhausted, fork unreachable) into a
     * terminal {@link TestState#CRASHED} with a synthetic attempt, guaranteeing it is accounted for.
     */
    public void forceTerminal(TestId id, FailureMode failureMode, String reason) {
        lock.lock();
        try {
            TestRecord record = require(id);
            if (record.state().isTerminal()) {
                return;
            }
            long now = System.currentTimeMillis();
            ExecutionAttempt attempt = ExecutionAttempt.builder()
                    .attemptNumber(record.attemptCount() + 1)
                    .isolationLevel(record.targetLevel())
                    .outcome(TestState.CRASHED)
                    .failureMode(failureMode)
                    .startMillis(now)
                    .endMillis(now)
                    .throwableMessage(reason)
                    .build();
            record.internalAddAttempt(attempt);
            transition(record, TestState.CRASHED);
            appendEvent("FORCED_TERMINAL", id, "reason=" + reason);
        } finally {
            lock.unlock();
        }
    }

    public TestRecord record(TestId id) {
        lock.lock();
        try {
            return records.get(id);
        } finally {
            lock.unlock();
        }
    }

    public List<TestRecord> testsInState(TestState state) {
        lock.lock();
        try {
            List<TestRecord> result = new ArrayList<>();
            for (TestRecord r : records.values()) {
                if (r.state() == state) {
                    result.add(r);
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    public boolean allTerminal() {
        lock.lock();
        try {
            for (TestRecord r : records.values()) {
                if (!r.state().isTerminal()) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return records.size();
        } finally {
            lock.unlock();
        }
    }

    /** Snapshot the current state into an immutable report model. */
    public ReportModel snapshot() {
        lock.lock();
        try {
            return new ReportModel(new ArrayList<>(records.values()), startMillis, System.currentTimeMillis());
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            if (journalWriter != null) {
                try {
                    journalWriter.flush();
                    journalWriter.close();
                } catch (IOException e) {
                    log.warn("Error closing journal file: " + e.getMessage());
                } finally {
                    journalWriter = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private TestRecord require(TestId id) {
        TestRecord record = records.get(id);
        if (record == null) {
            // A test reported by a fork that we did not discover; account for it defensively.
            record = new TestRecord(id);
            records.put(id, record);
            appendEvent("LATE_DISCOVERY", id, "reportedByFork");
        }
        return record;
    }

    private void transition(TestRecord record, TestState target) {
        TestState current = record.state();
        if (current == target) {
            return;
        }
        Set<TestState> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            log.warn("Illegal state transition for " + record.testId() + ": " + current + " -> " + target
                    + " (applying anyway to preserve outcome)");
        }
        record.internalSetState(target);
    }

    private void appendEvent(String event, TestId id, String detail) {
        if (journalWriter == null) {
            return;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", System.currentTimeMillis());
        entry.put("event", event);
        if (id != null) {
            entry.put("testId", id.uniqueId());
        }
        if (detail != null) {
            entry.put("detail", detail);
        }
        try {
            journalWriter.write(Json.encode(entry));
            journalWriter.write('\n');
            journalWriter.flush();
        } catch (IOException e) {
            log.warn("Failed to append journal event, disabling journal file: " + e.getMessage());
            try {
                journalWriter.close();
            } catch (IOException ignored) {
                // best effort
            }
            journalWriter = null;
        }
    }

    private static Map<TestState, Set<TestState>> buildTransitions() {
        Map<TestState, Set<TestState>> map = new EnumMap<>(TestState.class);
        // A fork can die before a test ever starts, so a NOT_RUN test may be attributed CRASHED.
        map.put(TestState.NOT_RUN, EnumSet.of(TestState.RUNNING, TestState.CRASHED));
        map.put(TestState.RUNNING, EnumSet.of(
                TestState.PASSED, TestState.FAILED, TestState.CRASHED, TestState.SKIPPED));
        // Non-successful terminals may be re-opened for a retry at a stronger isolation level.
        map.put(TestState.FAILED, EnumSet.of(TestState.NOT_RUN));
        map.put(TestState.CRASHED, EnumSet.of(TestState.NOT_RUN));
        map.put(TestState.PASSED, EnumSet.noneOf(TestState.class));
        map.put(TestState.SKIPPED, EnumSet.noneOf(TestState.class));
        return map;
    }

    /** Convenience for callers that prefer unchecked IO semantics. */
    /** Same as {@link #enableJournalFile(Path)}; errors are logged and never propagated. */
    public void enableJournalFileUnchecked(Path path) {
        enableJournalFile(path);
    }
}
