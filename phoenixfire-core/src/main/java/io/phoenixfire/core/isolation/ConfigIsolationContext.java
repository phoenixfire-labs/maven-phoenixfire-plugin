package io.phoenixfire.core.isolation;

import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.core.config.PhoenixfireConfiguration;

import java.util.List;
import java.util.Map;

/** {@link IsolationContext} backed by the active {@link PhoenixfireConfiguration}. */
public final class ConfigIsolationContext implements IsolationContext {

    private final PhoenixfireConfiguration config;

    public ConfigIsolationContext(PhoenixfireConfiguration config) {
        this.config = config;
    }

    @Override
    public List<String> baseJvmArgs() {
        return config.baseJvmArgs();
    }

    @Override
    public Map<String, String> systemProperties() {
        return config.systemProperties();
    }

    @Override
    public Map<String, String> environment() {
        return config.environment();
    }

    @Override
    public int forkCount() {
        return config.forkCount();
    }
}
