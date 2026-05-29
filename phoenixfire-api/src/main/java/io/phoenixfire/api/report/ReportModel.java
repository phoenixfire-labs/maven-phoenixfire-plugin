package io.phoenixfire.api.report;

import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of the final execution state handed to {@link ReportWriter}s.
 *
 * <p>Guarantees that every discovered test appears with a terminal {@link TestState}.
 */
public final class ReportModel {

    private final List<TestRecord> records;
    private final long startMillis;
    private final long endMillis;

    public ReportModel(List<TestRecord> records, long startMillis, long endMillis) {
        this.records = Collections.unmodifiableList(records);
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    public List<TestRecord> records() {
        return records;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    public long durationMillis() {
        return endMillis >= startMillis ? endMillis - startMillis : 0L;
    }

    public int total() {
        return records.size();
    }

    public long count(TestState state) {
        return records.stream().filter(r -> r.state() == state).count();
    }

    /** Number of tests that ultimately succeeded but only after an initial failure/crash. */
    public long flakyCount() {
        return records.stream().filter(TestRecord::recovered).count();
    }

    /** True if any test ended in a non-recovered FAILED or CRASHED state. */
    public boolean hasFailures() {
        return records.stream().anyMatch(r -> r.state() == TestState.FAILED || r.state() == TestState.CRASHED);
    }
}
