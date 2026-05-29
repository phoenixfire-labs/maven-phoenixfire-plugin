package io.phoenixfire.core.report;

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
import java.util.Locale;

/**
 * Emits Surefire-compatible {@code TEST-<className>.xml} files so CI systems parse Phoenixfire
 * results natively. Crashed/hung tests are rendered as {@code <error>} elements carrying the failure
 * mode and escalation history, guaranteeing every discovered test has a recorded outcome.
 */
public final class JUnitXmlReportWriter implements ReportWriter {

    @Override
    public void write(ReportModel model, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);

        Map<String, List<TestRecord>> byClass = new LinkedHashMap<>();
        for (TestRecord record : model.records()) {
            String className = record.testId().className();
            if (className == null || className.isBlank()) {
                className = "UnknownClass";
            }
            byClass.computeIfAbsent(className, k -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<TestRecord>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            String xml = renderSuite(className, entry.getValue());
            Path file = outputDirectory.resolve("TEST-" + sanitize(className) + ".xml");
            Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String renderSuite(String className, List<TestRecord> records) {
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        double totalTime = 0.0;
        for (TestRecord r : records) {
            switch (r.state()) {
                case FAILED:
                    failures++;
                    break;
                case CRASHED:
                    errors++;
                    break;
                case SKIPPED:
                    skipped++;
                    break;
                default:
                    break;
            }
            ExecutionAttempt last = r.lastAttempt();
            if (last != null) {
                totalTime += last.durationMillis() / 1000.0;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"").append(Xml.escape(className)).append('"')
                .append(" tests=\"").append(records.size()).append('"')
                .append(" failures=\"").append(failures).append('"')
                .append(" errors=\"").append(errors).append('"')
                .append(" skipped=\"").append(skipped).append('"')
                .append(" time=\"").append(formatTime(totalTime)).append("\">\n");

        for (TestRecord r : records) {
            renderCase(sb, className, r);
        }

        sb.append("</testsuite>\n");
        return sb.toString();
    }

    private void renderCase(StringBuilder sb, String className, TestRecord r) {
        ExecutionAttempt last = r.lastAttempt();
        double time = last != null ? last.durationMillis() / 1000.0 : 0.0;
        String name = r.testId().displayName();

        sb.append("  <testcase name=\"").append(Xml.escape(name)).append('"')
                .append(" classname=\"").append(Xml.escape(className)).append('"')
                .append(" time=\"").append(formatTime(time)).append('"');

        TestState state = r.state();
        if (state == TestState.PASSED) {
            sb.append("/>\n");
            return;
        }
        sb.append(">\n");

        String message = last != null && last.throwableMessage() != null ? last.throwableMessage() : "";
        String stack = last != null && last.throwableStackTrace() != null ? last.throwableStackTrace() : "";

        switch (state) {
            case FAILED:
                sb.append("    <failure message=\"").append(Xml.escape(message)).append('"')
                        .append(" type=\"assertionFailure\">")
                        .append(Xml.escape(stack)).append("</failure>\n");
                break;
            case CRASHED:
                String mode = last != null ? last.failureMode().name() : "UNKNOWN";
                sb.append("    <error message=\"")
                        .append(Xml.escape("Fork failure [" + mode + "]: " + message)).append('"')
                        .append(" type=\"").append(Xml.escape(mode)).append("\">")
                        .append(Xml.escape(stack.isEmpty() ? message : stack)).append("</error>\n");
                break;
            case SKIPPED:
                if (message.isEmpty()) {
                    sb.append("    <skipped/>\n");
                } else {
                    sb.append("    <skipped message=\"").append(Xml.escape(message)).append("\"/>\n");
                }
                break;
            default:
                // Non-terminal should never appear in a final report, but guard anyway.
                sb.append("    <error message=\"Test did not reach a terminal state\" type=\"INCOMPLETE\"/>\n");
        }

        sb.append("    <system-out>").append(Xml.escape(renderAuditTrail(r))).append("</system-out>\n");
        sb.append("  </testcase>\n");
    }

    private String renderAuditTrail(TestRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Phoenixfire execution trail for ").append(r.testId().uniqueId()).append('\n');
        int i = 1;
        for (ExecutionAttempt a : r.attempts()) {
            sb.append("  attempt #").append(a.attemptNumber() == 0 ? i : a.attemptNumber())
                    .append(" level=").append(a.isolationLevel())
                    .append(" outcome=").append(a.outcome())
                    .append(" failureMode=").append(a.failureMode())
                    .append(" durationMs=").append(a.durationMillis())
                    .append('\n');
            i++;
        }
        return sb.toString();
    }

    private static String formatTime(double seconds) {
        return String.format(Locale.ROOT, "%.3f", seconds);
    }

    private static String sanitize(String className) {
        return className.replaceAll("[^A-Za-z0-9_.$-]", "_");
    }
}
