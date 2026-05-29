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

    /** Tests that ultimately passed but only after an initial failure/crash (recovered/flaky). */
    public long flaky() {
        return reportModel.flakyCount();
    }

    /** True if any test never recovered (final state FAILED or CRASHED). */
    public boolean hasFailures() {
        return reportModel.hasFailures();
    }

    /**
     * Whether the build should be failed.
     *
     * @param failOnFlaky if true, recovered/flaky tests also fail the build; otherwise only
     *                    non-recovered failures/crashes do.
     */
    public boolean shouldFailBuild(boolean failOnFlaky) {
        return hasFailures() || (failOnFlaky && flaky() > 0);
    }

    public String describe() {
        return "Tests run: " + total()
                + ", Passed: " + passed()
                + ", Failures: " + failed()
                + ", Crashed: " + crashed()
                + ", Skipped: " + skipped()
                + ", Flaky(recovered): " + flaky();
    }
}
