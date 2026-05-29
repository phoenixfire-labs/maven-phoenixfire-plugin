package io.phoenixfire.maven;

import io.phoenixfire.core.util.PhoenixfireLogger;
import org.apache.maven.plugin.logging.Log;

/** Adapts the Maven {@link Log} to the core {@link PhoenixfireLogger}. */
final class MavenLoggerAdapter implements PhoenixfireLogger {

    private final Log log;

    MavenLoggerAdapter(Log log) {
        this.log = log;
    }

    @Override
    public void debug(String message) {
        log.debug(message);
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message) {
        log.warn(message);
    }

    @Override
    public void error(String message) {
        log.error(message);
    }

    @Override
    public void error(String message, Throwable t) {
        log.error(message, t);
    }
}
