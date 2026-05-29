package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;

import java.util.List;
import java.util.Objects;

/**
 * A batch of tests to execute in a single forked JVM, together with the isolation level it was
 * planned for and the JVM configuration to launch the fork with.
 */
public final class WorkUnit {

    private final String id;
    private final IsolationLevel isolationLevel;
    private final List<TestId> tests;
    private final ForkConfig forkConfig;

    public WorkUnit(String id, IsolationLevel isolationLevel, List<TestId> tests, ForkConfig forkConfig) {
        this.id = Objects.requireNonNull(id, "id");
        this.isolationLevel = Objects.requireNonNull(isolationLevel, "isolationLevel");
        this.tests = List.copyOf(tests);
        this.forkConfig = forkConfig == null ? ForkConfig.empty() : forkConfig;
    }

    public String id() {
        return id;
    }

    public IsolationLevel isolationLevel() {
        return isolationLevel;
    }

    public List<TestId> tests() {
        return tests;
    }

    public ForkConfig forkConfig() {
        return forkConfig;
    }

    @Override
    public String toString() {
        return "WorkUnit{" + id + ", level=" + isolationLevel + ", tests=" + tests.size() + '}';
    }
}
