package io.phoenixfire.maven;

import io.phoenixfire.core.engine.ExecutionSummary;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

/**
 * A small failsafe-style summary file written by the integration-test goal and read by the verify
 * goal, so that integration-test failures do not break the build until verify (allowing post-IT
 * cleanup phases to run first).
 */
final class SummaryFile {

    static final String FILE_NAME = "phoenixfire-summary.properties";

    private SummaryFile() {
    }

    static void write(File reportsDir, ExecutionSummary summary) throws IOException {
        Files.createDirectories(reportsDir.toPath());
        Properties props = new Properties();
        props.setProperty("total", Long.toString(summary.total()));
        props.setProperty("passed", Long.toString(summary.passed()));
        props.setProperty("failed", Long.toString(summary.failed()));
        props.setProperty("crashed", Long.toString(summary.crashed()));
        props.setProperty("skipped", Long.toString(summary.skipped()));
        try (OutputStream out = Files.newOutputStream(new File(reportsDir, FILE_NAME).toPath())) {
            props.store(out, "Phoenixfire integration-test summary");
        }
    }

    static Summary read(File reportsDir) throws IOException {
        File file = new File(reportsDir, FILE_NAME);
        if (!file.exists()) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        }
        return new Summary(
                parse(props, "total"),
                parse(props, "failed"),
                parse(props, "crashed"));
    }

    private static long parse(Properties props, String key) {
        try {
            return Long.parseLong(props.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Minimal counts needed by the verify goal. */
    static final class Summary {
        final long total;
        final long failed;
        final long crashed;

        Summary(long total, long failed, long crashed) {
            this.total = total;
            this.failed = failed;
            this.crashed = crashed;
        }

        boolean hasFailures() {
            return failed > 0 || crashed > 0;
        }
    }
}
