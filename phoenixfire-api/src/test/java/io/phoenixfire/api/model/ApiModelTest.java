package io.phoenixfire.api.model;

import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.run.RunEnvelope;
import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiModelTest {

    @Test
    void testIdHandlesNullDisplayAndClassName() {
        TestId id = new TestId("uid", null, null);
        assertEquals("", id.className());
        assertEquals("uid", id.displayName());
        assertEquals("uid", id.toString());

        TestId same = new TestId("uid", "Cls", "disp");
        assertEquals(id, same);
        assertTrue(id.compareTo(same) == 0);
    }

    @Test
    void testStateTerminalAndSuccessful() {
        assertFalse(TestState.NOT_RUN.isTerminal());
        assertTrue(TestState.PASSED.isTerminal());
        assertTrue(TestState.PASSED.isSuccessful());
        assertTrue(TestState.SKIPPED.isSuccessful());
        assertFalse(TestState.FAILED.isSuccessful());
    }

    @Test
    void failureModeInfrastructure() {
        assertFalse(FailureMode.NONE.isInfrastructureFailure());
        assertFalse(FailureMode.ASSERTION_FAILURE.isInfrastructureFailure());
        assertTrue(FailureMode.SIGKILL.isInfrastructureFailure());
    }

    @Test
    void isolationLevelNextAndMaximum() {
        assertEquals(IsolationLevel.FRESH_FORK, IsolationLevel.SHARED_FORK_POOL.next());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, IsolationLevel.ONE_FORK_PER_CLASS.next());
        assertTrue(IsolationLevel.ONE_FORK_PER_CLASS.isMaximum());
        assertFalse(IsolationLevel.SHARED_FORK_POOL.isMaximum());
    }

    @Test
    void executionAttemptDefaultsAndDuration() {
        ExecutionAttempt a = ExecutionAttempt.builder()
                .attemptNumber(1)
                .outcome(TestState.PASSED)
                .startMillis(100)
                .endMillis(50)
                .build();
        assertEquals(FailureMode.NONE, a.failureMode());
        assertEquals(0L, a.durationMillis());
    }

    @Test
    void testRecordRecoveryAndForkReuse() {
        TestId id = new TestId("u1", "Cls", "m");
        TestRecord record = new TestRecord(id);
        record.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.FAILED).failureMode(FailureMode.ASSERTION_FAILURE).build());
        record.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(2).isolationLevel(IsolationLevel.FRESH_FORK)
                .outcome(TestState.PASSED).build());
        record.internalSetState(TestState.PASSED);

        assertTrue(record.recovered());
        assertFalse(record.everCrashed());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, record.firstFailLevel());
        assertEquals(IsolationLevel.FRESH_FORK, record.recoveryLevel());
        assertTrue(record.forkReuseSensitive());
        assertNull(new TestRecord(new TestId("u2", "C", "d")).lastAttempt());
    }

    @Test
    void runMetadataBlankNormalization() {
        RunMetadata meta = RunMetadata.builder()
                .gitSha("  ")
                .labels(Map.of("k", "v"))
                .build();
        assertNull(meta.gitSha());
        assertEquals("v", meta.labels().get("k"));
        assertTrue(RunMetadata.empty().labels().isEmpty());
    }

    @Test
    void runEnvelopeAndReportModel() {
        RunEnvelope envelope = RunEnvelope.builder()
                .runId("r1")
                .shardIndex(1)
                .shardCount(2)
                .build();
        assertEquals("r1", envelope.runId());

        TestRecord r = new TestRecord(new TestId("u", "C", "t"));
        r.internalSetState(TestState.FAILED);
        ReportModel model = new ReportModel(List.of(r), 0, 10, envelope);
        assertEquals(10, model.durationMillis());
        assertEquals(1, model.total());
        assertTrue(model.hasFailures());
        assertEquals(0, model.flakyCount());
    }
}
