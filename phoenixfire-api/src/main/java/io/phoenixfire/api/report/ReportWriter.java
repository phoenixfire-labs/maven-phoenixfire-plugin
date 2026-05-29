package io.phoenixfire.api.report;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Pluggable report serializer. Discovered via {@link java.util.ServiceLoader}; the defaults emit
 * Surefire-compatible JUnit XML and a native JSON audit report.
 */
public interface ReportWriter {

    /**
     * Write the report for {@code model} into {@code outputDirectory}.
     *
     * @param model           the final execution snapshot
     * @param outputDirectory the directory to write report files into (created if necessary)
     */
    void write(ReportModel model, Path outputDirectory) throws IOException;
}
