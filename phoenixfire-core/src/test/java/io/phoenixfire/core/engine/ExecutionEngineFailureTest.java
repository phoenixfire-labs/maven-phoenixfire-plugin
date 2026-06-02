package io.phoenixfire.core.engine;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.testsupport.SimulatedFork;
import io.phoenixfire.core.util.PhoenixfireLogger;

class ExecutionEngineFailureTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    @SuppressWarnings("unused")
    void enableSimulatedFork() {
        System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
    }

    @AfterEach
    @SuppressWarnings("unused")
    void clearProps() {
        System.clearProperty("phoenixfire.fork.main");
        System.clearProperty(SimulatedFork.PROP_MODE);
    }

    @Test
    void recordsFailedTests() throws Exception {
        System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(simulatedForkClasspath())
                .reportsDirectory(tempDir.toFile())
                .journalEnabled(false)
                .maxAttempts(1)
                .rerunFailingTestsCount(0)
                .forkCount(1)
                .heartbeatTimeoutMillis(20_000L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.total());
            assertEquals(2, summary.failed());
            assertTrue(summary.shouldFailBuild(false));
        }
    }

    @Test
    void parallelSharedPoolUsesMultipleForks() throws Exception {
        System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(simulatedForkClasspath())
                .reportsDirectory(tempDir.toFile())
                .journalEnabled(false)
                .maxAttempts(1)
                .forkCount(2)
                .heartbeatTimeoutMillis(20_000L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.passed());
        }
    }

    private static List<String> simulatedForkClasspath() {
        return List.of(
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                Path.of("target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString());
    }
}
