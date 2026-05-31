package io.phoenixfire.core.testsupport.spi;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.WorkUnit;

import java.util.List;

/** Test SPI that plans no work units at shared-pool level. */
public final class EmptySharedPoolStrategy implements IsolationStrategy {

    @Override
    public IsolationLevel level() {
        return IsolationLevel.SHARED_FORK_POOL;
    }

    @Override
    public List<WorkUnit> plan(List<TestId> tests, IsolationContext context) {
        return List.of();
    }
}
