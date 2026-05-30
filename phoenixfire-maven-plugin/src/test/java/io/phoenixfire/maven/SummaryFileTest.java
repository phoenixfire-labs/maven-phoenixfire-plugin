package io.phoenixfire.maven;

import io.phoenixfire.core.engine.ExecutionSummary;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryFileTest {

    @TempDir
    File reportsDir;

    @Test
    void writeAndReadRoundTrip() throws Exception {
        TestRecord r = new TestRecord(new TestId("u", "C", "t"));
        r.internalSetState(TestState.PASSED);
        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(r), 0, 1));
        SummaryFile.write(reportsDir, summary);
        SummaryFile.Summary read = SummaryFile.read(reportsDir);
        assertNotNull(read);
        org.junit.jupiter.api.Assertions.assertEquals(1, read.total);
        assertFalse(read.shouldFail(false));
    }

    @Test
    void readMissingReturnsNull() throws Exception {
        assertNull(SummaryFile.read(reportsDir));
    }

    @Test
    void shouldFailHonorsFlaky() throws Exception {
        SummaryFile.Summary s = new SummaryFile.Summary(1, 0, 0, 1);
        assertFalse(s.shouldFail(false));
        assertTrue(s.shouldFail(true));
        SummaryFile.Summary bad = new SummaryFile.Summary(1, 1, 0, 0);
        assertTrue(bad.hasFailures());
    }

    @Test
    void parseIgnoresBadNumbers() throws Exception {
        Files.writeString(reportsDir.toPath().resolve(SummaryFile.FILE_NAME), "total=not-a-number\n");
        SummaryFile.Summary read = SummaryFile.read(reportsDir);
        assertNotNull(read);
        org.junit.jupiter.api.Assertions.assertEquals(0L, read.total);
    }
}
