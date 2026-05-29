package io.phoenixfire.core.report;

import io.phoenixfire.api.json.Json;
import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.report.ReportWriter;
import io.phoenixfire.api.run.RunEnvelope;
import io.phoenixfire.api.run.RunMetadata;

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
        if (model.envelope() != null) {
            root.put("run", renderEnvelope(model.envelope()));
        }
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
        obj.put("forkReuseSensitive", r.forkReuseSensitive());
        IsolationLevel firstFail = r.firstFailLevel();
        obj.put("firstFailLevel", firstFail == null ? null : firstFail.name());
        IsolationLevel recoveryLevel = r.recoveryLevel();
        obj.put("recoveryLevel", recoveryLevel == null ? null : recoveryLevel.name());
        obj.put("failureSignature", signatureOf(r));

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
            am.put("exitCode", a.exitCode());
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

    /** Signature of the test's terminal failure (last failed/crashed attempt), or {@code null}. */
    static String signatureOf(TestRecord r) {
        for (int i = r.attempts().size() - 1; i >= 0; i--) {
            ExecutionAttempt a = r.attempts().get(i);
            if (a.outcome() == TestState.FAILED || a.outcome() == TestState.CRASHED) {
                return FailureSignatures.of(a.throwableStackTrace(), a.throwableMessage());
            }
        }
        return null;
    }

    private Map<String, Object> renderEnvelope(RunEnvelope env) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("runId", env.runId());

        RunMetadata md = env.metadata();
        Map<String, Object> git = new LinkedHashMap<>();
        putIfPresent(git, "sha", md.gitSha());
        putIfPresent(git, "branch", md.gitBranch());
        if (md.gitDirty() != null) {
            git.put("dirty", md.gitDirty());
        }
        if (!git.isEmpty()) {
            run.put("git", git);
        }

        Map<String, Object> ci = new LinkedHashMap<>();
        putIfPresent(ci, "provider", md.ciProvider());
        putIfPresent(ci, "buildId", md.ciBuildId());
        putIfPresent(ci, "url", md.ciBuildUrl());
        if (!ci.isEmpty()) {
            run.put("ci", ci);
        }

        Map<String, Object> envInfo = new LinkedHashMap<>();
        putIfPresent(envInfo, "host", env.host());
        putIfPresent(envInfo, "os", env.osName());
        putIfPresent(envInfo, "arch", env.osArch());
        putIfPresent(envInfo, "jvm", env.jvm());
        if (!envInfo.isEmpty()) {
            run.put("env", envInfo);
        }

        Map<String, Object> project = new LinkedHashMap<>();
        putIfPresent(project, "groupId", md.projectGroupId());
        putIfPresent(project, "artifactId", md.projectArtifactId());
        putIfPresent(project, "version", md.projectVersion());
        if (!project.isEmpty()) {
            run.put("project", project);
        }

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("maxAttempts", env.maxAttempts());
        cfg.put("forkCount", env.forkCount());
        cfg.put("escalationLadder", new ArrayList<Object>(env.escalationLadder()));
        run.put("config", cfg);

        if (!md.labels().isEmpty()) {
            run.put("labels", new LinkedHashMap<Object, Object>(md.labels()));
        }
        return run;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
