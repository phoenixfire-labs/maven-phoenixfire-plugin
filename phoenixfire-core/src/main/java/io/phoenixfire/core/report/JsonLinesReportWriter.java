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
 * Emits a flat, append-friendly "fact table" as JSON Lines ({@code phoenixfire-facts.jsonl}): one
 * JSON object per line, designed to be shipped verbatim into any log/analytics pipeline (Loki via
 * {@code | json}, Elasticsearch/ECS, Splunk, an OpenTelemetry Collector {@code filelog} receiver, or
 * just {@code jq}/DuckDB). The plugin stays vendor-neutral: it writes records with stable field
 * names and assumes nothing about the destination.
 *
 * <p>Three record types are produced:
 * <ul>
 *   <li>{@code run} - one per run, carrying the full {@link RunEnvelope} and summary counts.</li>
 *   <li>{@code test_result} - one per test, with the final state and the fork-reuse diagnosis.</li>
 *   <li>{@code test_attempt} - one per attempt, with outcome, isolation, fork reuse and exit code.</li>
 * </ul>
 *
 * <p>The most useful low-cardinality run dimensions (runId, git sha/branch, os, jvm) are denormalised
 * onto every line so a log tool can slice without a join.
 */
public final class JsonLinesReportWriter implements ReportWriter {

    private static final int SCHEMA_VERSION = 1;

    @Override
    public void write(ReportModel model, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        Map<String, Object> dims = runDimensions(model.envelope());

        StringBuilder sb = new StringBuilder();
        sb.append(Json.encode(runLine(model))).append('\n');
        for (TestRecord r : model.records()) {
            sb.append(Json.encode(testResultLine(r, dims))).append('\n');
            for (ExecutionAttempt a : r.attempts()) {
                sb.append(Json.encode(attemptLine(r, a, dims))).append('\n');
            }
        }

        Path file = outputDirectory.resolve("phoenixfire-facts.jsonl");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> runLine(ReportModel model) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("schemaVersion", SCHEMA_VERSION);
        line.put("type", "run");
        RunEnvelope env = model.envelope();
        if (env != null) {
            line.put("runId", env.runId());
            RunMetadata md = env.metadata();
            putIfPresent(line, "gitSha", md.gitSha());
            putIfPresent(line, "gitBranch", md.gitBranch());
            if (md.gitDirty() != null) {
                line.put("gitDirty", md.gitDirty());
            }
            putIfPresent(line, "ciProvider", md.ciProvider());
            putIfPresent(line, "ciBuildId", md.ciBuildId());
            putIfPresent(line, "ciBuildUrl", md.ciBuildUrl());
            putIfPresent(line, "host", env.host());
            putIfPresent(line, "os", env.osName());
            putIfPresent(line, "arch", env.osArch());
            putIfPresent(line, "jvm", env.jvm());
            putIfPresent(line, "groupId", md.projectGroupId());
            putIfPresent(line, "artifactId", md.projectArtifactId());
            putIfPresent(line, "version", md.projectVersion());
            line.put("maxAttempts", env.maxAttempts());
            line.put("forkCount", env.forkCount());
            if (env.shardCount() > 0) {
                line.put("shardIndex", env.shardIndex());
                line.put("shardCount", env.shardCount());
            }
            line.put("escalationLadder", new ArrayList<Object>(env.escalationLadder()));
            if (!md.labels().isEmpty()) {
                line.put("labels", new LinkedHashMap<Object, Object>(md.labels()));
            }
        }
        line.put("startMillis", model.startMillis());
        line.put("endMillis", model.endMillis());
        line.put("durationMillis", model.durationMillis());
        line.put("total", model.total());
        line.put("passed", model.count(TestState.PASSED));
        line.put("failed", model.count(TestState.FAILED));
        line.put("crashed", model.count(TestState.CRASHED));
        line.put("skipped", model.count(TestState.SKIPPED));
        line.put("flaky", model.flakyCount());
        return line;
    }

    private Map<String, Object> testResultLine(TestRecord r, Map<String, Object> dims) {
        Map<String, Object> line = new LinkedHashMap<>(dims);
        line.put("type", "test_result");
        line.put("testKey", testKey(r));
        line.put("uniqueId", r.testId().uniqueId());
        line.put("class", r.testId().className());
        line.put("method", r.testId().displayName());
        line.put("finalState", r.state().name());
        line.put("attempts", r.attemptCount());
        line.put("recovered", r.recovered());
        line.put("forkReuseSensitive", r.forkReuseSensitive());
        IsolationLevel firstFail = r.firstFailLevel();
        line.put("firstFailLevel", firstFail == null ? null : firstFail.name());
        IsolationLevel recoveryLevel = r.recoveryLevel();
        line.put("recoveryLevel", recoveryLevel == null ? null : recoveryLevel.name());
        line.put("lastFailureMode", r.lastFailureMode().name());
        line.put("failureSignature", NativeJsonReportWriter.signatureOf(r));
        return line;
    }

    private Map<String, Object> attemptLine(TestRecord r, ExecutionAttempt a, Map<String, Object> dims) {
        Map<String, Object> line = new LinkedHashMap<>(dims);
        line.put("type", "test_attempt");
        line.put("testKey", testKey(r));
        line.put("uniqueId", r.testId().uniqueId());
        line.put("class", r.testId().className());
        line.put("method", r.testId().displayName());
        line.put("attempt", a.attemptNumber());
        line.put("isolation", a.isolationLevel().name());
        line.put("forkReuse", a.isolationLevel() == IsolationLevel.SHARED_FORK_POOL);
        line.put("outcome", a.outcome().name());
        line.put("failureMode", a.failureMode().name());
        line.put("exitCode", a.exitCode());
        line.put("fork", a.forkId());
        line.put("durationMillis", a.durationMillis());
        line.put("failureSignature", FailureSignatures.of(a.throwableStackTrace(), a.throwableMessage()));
        line.put("ts", a.endMillis());
        return line;
    }

    /** Low-cardinality run dimensions denormalised onto every fact line for join-free slicing. */
    private Map<String, Object> runDimensions(RunEnvelope env) {
        Map<String, Object> dims = new LinkedHashMap<>();
        dims.put("schemaVersion", SCHEMA_VERSION);
        if (env != null) {
            dims.put("runId", env.runId());
            putIfPresent(dims, "gitSha", env.metadata().gitSha());
            putIfPresent(dims, "gitBranch", env.metadata().gitBranch());
            putIfPresent(dims, "os", env.osName());
            putIfPresent(dims, "jvm", env.jvm());
            if (env.shardCount() > 0) {
                dims.put("shardIndex", env.shardIndex());
                dims.put("shardCount", env.shardCount());
            }
        }
        return dims;
    }

    private static String testKey(TestRecord r) {
        return r.testId().className() + "#" + r.testId().displayName();
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
