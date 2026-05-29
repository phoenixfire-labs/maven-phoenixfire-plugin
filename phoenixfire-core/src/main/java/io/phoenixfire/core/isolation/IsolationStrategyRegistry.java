package io.phoenixfire.core.isolation;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Resolves the {@link IsolationStrategy} for each {@link IsolationLevel}. Built-in strategies are
 * registered for every level; any strategy found via {@link ServiceLoader} overrides the default
 * for its level, allowing users to plug in custom isolation behaviour.
 */
public final class IsolationStrategyRegistry {

    private final Map<IsolationLevel, IsolationStrategy> strategies = new EnumMap<>(IsolationLevel.class);

    private IsolationStrategyRegistry() {
    }

    public static IsolationStrategyRegistry createDefault(PhoenixfireLogger log, ClassLoader serviceLoader) {
        IsolationStrategyRegistry registry = new IsolationStrategyRegistry();
        registry.register(new SharedForkPoolStrategy());
        registry.register(new FreshForkStrategy());
        registry.register(new OneForkPerClassStrategy());

        ClassLoader cl = serviceLoader != null ? serviceLoader : IsolationStrategyRegistry.class.getClassLoader();
        for (IsolationStrategy strategy : ServiceLoader.load(IsolationStrategy.class, cl)) {
            log.info("Registering custom isolation strategy for " + strategy.level() + ": "
                    + strategy.getClass().getName());
            registry.register(strategy);
        }
        return registry;
    }

    public void register(IsolationStrategy strategy) {
        strategies.put(strategy.level(), strategy);
    }

    public IsolationStrategy forLevel(IsolationLevel level) {
        IsolationStrategy strategy = strategies.get(level);
        if (strategy == null) {
            throw new IllegalStateException("No isolation strategy registered for level " + level);
        }
        return strategy;
    }
}
