package io.phoenixfire.api.spi;

import java.util.List;
import java.util.Map;

/**
 * Read-only view of the runtime configuration an {@link IsolationStrategy} may need when planning
 * work units (base JVM arguments, system properties, environment, and the configured fork count).
 */
public interface IsolationContext {

    /** Base JVM arguments derived from {@code argLine} and friends. */
    List<String> baseJvmArgs();

    /** System properties to pass to every fork. */
    Map<String, String> systemProperties();

    /** Environment variables to pass to every fork. */
    Map<String, String> environment();

    /** Configured maximum number of concurrent forks for the shared pool. */
    int forkCount();
}
