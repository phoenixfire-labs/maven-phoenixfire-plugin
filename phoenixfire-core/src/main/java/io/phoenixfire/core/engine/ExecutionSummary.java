package io.phoenixfire.core.engine;

import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.model.TestState;

/** Outcome of an entire Phoenixfire run, returned to the Maven layer. */
public final class ExecutionSummary {

    private final ReportModel reportModel;

    public ExecutionSummary(ReportModel reportModel) {
        this.reportModel = reportModel;
    }

    public ReportModel reportModel() {
        return reportModel;
    }

    public int total() {
        return reportModel.total();
    }

    public long passed() {
        return reportModel.count(TestState.PASSED);
    }

    public long failed() {
        return reportModel.count(TestState.FAILED);
    }

    public long crashed() {
        return reportModel.count(TestState.CRASHED);
    }

    public long skipped() {
        return reportModel.count(TestState.SKIPPED);
    }

    public boolean hasFailures() {
        return reportModel.hasFailures();
    }

    public String describe() {
        return "Tests run: " + total()
                + ", Passed: " + passed()
                + ", Failures: " + failed()
                + ", Crashed: " + crashed()
                + ", Skipped: " + skipped();
    }
}
