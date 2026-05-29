package io.phoenixfire.core.isolation;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.WorkUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard execution: partitions tests into up to {@code forkCount} roughly even batches, one work
 * unit per batch, to be run concurrently by the scheduler's fork pool.
 */
public final class SharedForkPoolStrategy implements IsolationStrategy {

    @Override
    public IsolationLevel level() {
        return IsolationLevel.SHARED_FORK_POOL;
    }

    @Override
    public List<WorkUnit> plan(List<TestId> tests, IsolationContext context) {
        List<WorkUnit> units = new ArrayList<>();
        if (tests.isEmpty()) {
            return units;
        }
        int forks = Math.max(1, Math.min(context.forkCount(), tests.size()));
        ForkConfig forkConfig = Isolations.forkConfigFrom(context);

        List<List<TestId>> buckets = new ArrayList<>();
        for (int i = 0; i < forks; i++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < tests.size(); i++) {
            buckets.get(i % forks).add(tests.get(i));
        }
        int index = 0;
        for (List<TestId> bucket : buckets) {
            if (!bucket.isEmpty()) {
                units.add(new WorkUnit("pool-" + (index++), level(), bucket, forkConfig));
            }
        }
        return units;
    }
}
