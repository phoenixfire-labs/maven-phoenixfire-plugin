package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.report.ReportWriter;

import java.io.IOException;
import java.nio.file.Path;

/** Test SPI that always fails report emission. */
public final class FailingReportWriter implements ReportWriter {

    @Override
    public void write(ReportModel model, Path outputDirectory) throws IOException {
        throw new IOException("simulated report writer failure");
    }
}
