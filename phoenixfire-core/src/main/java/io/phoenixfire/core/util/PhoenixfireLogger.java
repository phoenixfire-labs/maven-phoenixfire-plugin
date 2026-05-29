package io.phoenixfire.core.util;

/**
 * Tiny logging abstraction so the core engine does not depend on the Maven plugin API. The Maven
 * layer adapts {@code org.apache.maven.plugin.logging.Log} to this interface.
 */
public interface PhoenixfireLogger {

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    default void error(String message, Throwable t) {
        error(message + " : " + t);
    }

    /** A logger that writes to standard out/err; useful for tests and standalone use. */
    static PhoenixfireLogger console() {
        return new PhoenixfireLogger() {
            @Override
            public void debug(String message) {
                // Suppressed by default to keep console output readable.
            }

            @Override
            public void info(String message) {
                System.out.println("[phoenixfire] " + message);
            }

            @Override
            public void warn(String message) {
                System.out.println("[phoenixfire][WARN] " + message);
            }

            @Override
            public void error(String message) {
                System.err.println("[phoenixfire][ERROR] " + message);
            }
        };
    }
}
