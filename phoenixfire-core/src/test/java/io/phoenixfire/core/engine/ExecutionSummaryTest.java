package io.phoenixfire.core.engine;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionSummaryTest {

    @Test
    void shouldFailBuildHonorsFlakyFlag() {
        TestRecord flaky = new TestRecord(new TestId("u", "C", "t"));
        flaky.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.FAILED).build());
        flaky.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(2).outcome(TestState.PASSED).build());
        flaky.internalSetState(TestState.PASSED);

        ExecutionSummary summary = new ExecutionSummary(
                new ReportModel(List.of(flaky), 0, 1));
        assertFalse(summary.shouldFailBuild(false));
        assertTrue(summary.shouldFailBuild(true));

        TestRecord failed = new TestRecord(new TestId("u2", "C", "t2"));
        failed.internalSetState(TestState.FAILED);
        ExecutionSummary hardFail = new ExecutionSummary(new ReportModel(List.of(failed), 0, 1));
        assertTrue(hardFail.shouldFailBuild(false));
    }

    @Test
    void aggregatesCountsAndDescribe() {
        TestRecord passed = new TestRecord(new TestId("p", "C", "p"));
        passed.internalSetState(TestState.PASSED);
        TestRecord failed = new TestRecord(new TestId("f", "C", "f"));
        failed.internalSetState(TestState.FAILED);
        TestRecord crashed = new TestRecord(new TestId("c", "C", "c"));
        crashed.internalSetState(TestState.CRASHED);
        TestRecord skipped = new TestRecord(new TestId("s", "C", "s"));
        skipped.internalSetState(TestState.SKIPPED);

        ReportModel model = new ReportModel(List.of(passed, failed, crashed, skipped), 0, 10);
        ExecutionSummary summary = new ExecutionSummary(model);

        assertEquals(4, summary.total());
        assertEquals(1, summary.passed());
        assertEquals(1, summary.failed());
        assertEquals(1, summary.crashed());
        assertEquals(1, summary.skipped());
        assertTrue(summary.hasFailures());
        assertTrue(summary.describe().contains("Tests run: 4"));
        assertEquals(model, summary.reportModel());
    }
}
