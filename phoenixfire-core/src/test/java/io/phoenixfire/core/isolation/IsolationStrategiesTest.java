package io.phoenixfire.core.isolation;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.spi.WorkUnit;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolationStrategiesTest {

    private final IsolationContext context = new ConfigIsolationContext(
            PhoenixfireConfiguration.builder().forkCount(2).build());

    @Test
    void sharedPoolPartitionsIntoBuckets() {
        List<TestId> tests = List.of(
                new TestId("a", "A", "a"),
                new TestId("b", "B", "b"),
                new TestId("c", "C", "c"));
        List<WorkUnit> units = new SharedForkPoolStrategy().plan(tests, context);
        assertEquals(2, units.size());
        assertEquals(3, units.stream().mapToInt(u -> u.tests().size()).sum());
    }

    @Test
    void freshForkUsesSingleUnit() {
        assertTrue(new FreshForkStrategy().plan(List.of(new TestId("x", "X", "x")), context).size() == 1);
        assertTrue(new FreshForkStrategy().plan(List.of(), context).isEmpty());
    }

    @Test
    void oneForkPerClassGroupsByClass() {
        List<WorkUnit> units = new OneForkPerClassStrategy().plan(List.of(
                new TestId("a", "com.A", "a"),
                new TestId("b", null, "b")), context);
        assertEquals(2, units.size());
    }

    @Test
    void registryResolvesLevels() {
        IsolationStrategyRegistry registry = IsolationStrategyRegistry.createDefault(PhoenixfireLogger.console(), null);
        assertEquals(IsolationLevel.FRESH_FORK, registry.forLevel(IsolationLevel.FRESH_FORK).level());
        registry.register(new FreshForkStrategy());
        assertEquals(IsolationLevel.FRESH_FORK, registry.forLevel(IsolationLevel.FRESH_FORK).level());
    }
}
