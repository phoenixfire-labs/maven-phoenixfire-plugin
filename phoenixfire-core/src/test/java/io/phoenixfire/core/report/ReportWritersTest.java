package io.phoenixfire.core.report;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.run.RunEnvelope;
import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWritersTest {

    @Test
    void writersEmitAllFormats(@TempDir Path dir) throws Exception {
        TestRecord passed = record("u1", "com.Foo", "ok", TestState.PASSED, null);
        TestRecord failed = record("u2", "", "fail", TestState.FAILED, FailureMode.ASSERTION_FAILURE);
        TestRecord crashed = record("u3", "com.Bar", "crash", TestState.CRASHED, FailureMode.SIGKILL);
        TestRecord skipped = record("u4", "com.Baz", "skip", TestState.SKIPPED, FailureMode.NONE);
        TestRecord incomplete = record("u5", "com.Qux", "run", TestState.RUNNING, FailureMode.NONE);

        RunEnvelope envelope = RunEnvelope.builder()
                .runId("run-1")
                .host("host")
                .shardIndex(1)
                .shardCount(2)
                .metadata(RunMetadata.builder().gitSha("abc").labels(java.util.Map.of("env", "ci")).build())
                .build();
        ReportModel model = new ReportModel(
                List.of(passed, failed, crashed, skipped, incomplete), 0, 100, envelope);

        new JUnitXmlReportWriter().write(model, dir);
        new NativeJsonReportWriter().write(model, dir);
        new JsonLinesReportWriter().write(model, dir);

        assertTrue(Files.exists(dir.resolve("TEST-com.Foo.xml")));
        assertTrue(Files.exists(dir.resolve("TEST-UnknownClass.xml")));
        assertTrue(Files.exists(dir.resolve("phoenixfire-report.json")));
        String jsonl = Files.readString(dir.resolve("phoenixfire-facts.jsonl"), StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"type\":\"run\""));
        assertTrue(jsonl.contains("\"type\":\"test_attempt\""));
    }

    private static TestRecord record(String uid, String className, String display, TestState state,
                                     FailureMode mode) {
        TestRecord r = new TestRecord(new TestId(uid, className, display));
        r.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1)
                .isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(state)
                .failureMode(mode)
                .throwableMessage("msg")
                .throwableStackTrace("stack")
                .startMillis(0)
                .endMillis(5)
                .build());
        r.internalSetState(state);
        return r;
    }
}
