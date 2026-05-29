package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;

import java.util.List;

/**
 * Pluggable strategy that partitions a set of tests into {@link WorkUnit}s for a given isolation
 * level. Implementations are discovered via {@link java.util.ServiceLoader}; the built-in ladder
 * provides one strategy per {@link IsolationLevel}.
 *
 * <p>Custom strategies allow users to define their own escalation behaviour (for example, grouping
 * tests by package, or pinning specific suites to dedicated forks).
 */
public interface IsolationStrategy {

    /** The isolation level this strategy implements. */
    IsolationLevel level();

    /**
     * Partition {@code tests} into work units appropriate for this isolation level.
     *
     * @param tests   the tests that need to run at this level
     * @param context runtime configuration
     * @return one or more work units covering exactly {@code tests}
     */
    List<WorkUnit> plan(List<TestId> tests, IsolationContext context);
}
