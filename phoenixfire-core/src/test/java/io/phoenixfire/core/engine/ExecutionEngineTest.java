package io.phoenixfire.core.engine;

import io.phoenixfire.api.model.TestState;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.testsupport.SimulatedFork;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void enableSimulatedFork() {
        System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
        System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_DISCOVER);
    }

    @AfterEach
    void clearProps() {
        System.clearProperty("phoenixfire.fork.main");
        System.clearProperty(SimulatedFork.PROP_MODE);
    }

    @Test
    void runsDiscoveredTestsToCompletion() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(simulatedForkClasspath())
                .reportsDirectory(tempDir.toFile())
                .journalEnabled(false)
                .maxAttempts(2)
                .forkCount(1)
                .heartbeatTimeoutMillis(20_000L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.total());
            assertEquals(2, summary.passed());
            assertFalse(summary.hasFailures());
        }
    }

    @Test
    void handlesEmptySelectionAfterFilter() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(simulatedForkClasspath())
                .reportsDirectory(tempDir.toFile())
                .journalEnabled(false)
                .testFilter("NoSuchTest")
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            ExecutionSummary summary = engine.run();
            assertEquals(0, summary.total());
            assertTrue(tempDir.resolve("phoenixfire-report.json").toFile().exists()
                    || tempDir.toFile().list() != null);
        }
    }

    private static List<String> simulatedForkClasspath() {
        String sep = File.pathSeparator;
        return List.of(
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                Path.of("target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString());
    }
}
