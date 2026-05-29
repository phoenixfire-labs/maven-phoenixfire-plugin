package io.phoenixfire.api.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JVM configuration for a single forked worker: the extra JVM arguments, system properties and
 * environment variables to apply when launching it.
 */
public final class ForkConfig {

    private final List<String> jvmArgs;
    private final Map<String, String> systemProperties;
    private final Map<String, String> environment;

    public ForkConfig(List<String> jvmArgs,
                      Map<String, String> systemProperties,
                      Map<String, String> environment) {
        this.jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        this.systemProperties = systemProperties == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(systemProperties));
        this.environment = environment == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(environment));
    }

    public List<String> jvmArgs() {
        return jvmArgs;
    }

    public Map<String, String> systemProperties() {
        return systemProperties;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public static ForkConfig empty() {
        return new ForkConfig(List.of(), Map.of(), Map.of());
    }
}
