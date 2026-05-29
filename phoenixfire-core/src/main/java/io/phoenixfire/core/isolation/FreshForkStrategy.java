package io.phoenixfire.core.isolation;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.WorkUnit;

import java.util.List;

/**
 * Runs all affected tests together in a single brand-new JVM fork. The first escalation step after
 * a crash in the shared pool: a clean JVM, but tests still share it.
 */
public final class FreshForkStrategy implements IsolationStrategy {

    @Override
    public IsolationLevel level() {
        return IsolationLevel.FRESH_FORK;
    }

    @Override
    public List<WorkUnit> plan(List<TestId> tests, IsolationContext context) {
        if (tests.isEmpty()) {
            return List.of();
        }
        return List.of(new WorkUnit("fresh-0", level(), tests, Isolations.forkConfigFrom(context)));
    }
}
