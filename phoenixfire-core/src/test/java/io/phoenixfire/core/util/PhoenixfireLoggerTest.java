package io.phoenixfire.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PhoenixfireLoggerTest {

    @Test
    void consoleLoggerAcceptsMessages() {
        PhoenixfireLogger log = PhoenixfireLogger.console();
        assertDoesNotThrow(() -> {
            log.info("info");
            log.warn("warn");
            log.debug("debug");
            log.error("error");
            log.error("error", new RuntimeException("x"));
        });
    }
}
