package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.WorkUnit;

import java.util.List;

/** Test SPI that overrides fresh-fork planning with a single work unit. */
public final class CustomFreshForkStrategy implements IsolationStrategy {

    @Override
    public IsolationLevel level() {
        return IsolationLevel.FRESH_FORK;
    }

    @Override
    public List<WorkUnit> plan(List<TestId> tests, IsolationContext context) {
        if (tests.isEmpty()) {
            return List.of();
        }
        return List.of(new WorkUnit("custom-fresh", IsolationLevel.FRESH_FORK, tests, ForkConfig.empty()));
    }
}
