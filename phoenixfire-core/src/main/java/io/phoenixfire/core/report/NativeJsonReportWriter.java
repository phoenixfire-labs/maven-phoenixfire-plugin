package io.phoenixfire.core.report;

import io.phoenixfire.api.json.Json;
import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.report.ReportWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code phoenixfire-report.json}: the full audit trail for every test, including each
 * attempt's isolation level, outcome, failure mode, timing and the overall escalation path. This is
 * the source of truth for "accountable" execution that the JUnit XML cannot fully express.
 */
public final class NativeJsonReportWriter implements ReportWriter {

    @Override
    public void write(ReportModel model, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("startMillis", model.startMillis());
        root.put("endMillis", model.endMillis());
        root.put("durationMillis", model.durationMillis());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", model.total());
        summary.put("passed", model.count(TestState.PASSED));
        summary.put("failed", model.count(TestState.FAILED));
        summary.put("crashed", model.count(TestState.CRASHED));
        summary.put("skipped", model.count(TestState.SKIPPED));
        summary.put("notRun", model.count(TestState.NOT_RUN));
        summary.put("running", model.count(TestState.RUNNING));
        summary.put("flaky", model.flakyCount());
        root.put("summary", summary);

        List<Object> tests = new ArrayList<>();
        for (TestRecord r : model.records()) {
            tests.add(renderRecord(r));
        }
        root.put("tests", tests);

        Path file = outputDirectory.resolve("phoenixfire-report.json");
        Files.write(file, Json.encode(root).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> renderRecord(TestRecord r) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("uniqueId", r.testId().uniqueId());
        obj.put("className", r.testId().className());
        obj.put("displayName", r.testId().displayName());
        obj.put("finalState", r.state().name());
        obj.put("lastFailureMode", r.lastFailureMode().name());
        obj.put("attemptCount", r.attemptCount());
        obj.put("recovered", r.recovered());

        List<Object> escalationPath = new ArrayList<>();
        List<Object> attempts = new ArrayList<>();
        for (ExecutionAttempt a : r.attempts()) {
            escalationPath.add(a.isolationLevel().name());
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("attemptNumber", a.attemptNumber());
            am.put("isolationLevel", a.isolationLevel().name());
            am.put("outcome", a.outcome().name());
            am.put("failureMode", a.failureMode().name());
            am.put("forkId", a.forkId());
            am.put("durationMillis", a.durationMillis());
            if (a.throwableMessage() != null) {
                am.put("failureMessage", a.throwableMessage());
            }
            attempts.add(am);
        }
        obj.put("escalationPath", escalationPath);
        obj.put("attempts", attempts);
        return obj;
    }
}
