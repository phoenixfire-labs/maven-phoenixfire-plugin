package io.phoenixfire.core.engine;

import io.phoenixfire.api.json.Json;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.RetryPolicy;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.testsupport.SimulatedFork;
import io.phoenixfire.core.testsupport.SpiTestClassLoader;
import io.phoenixfire.core.testsupport.spi.BackoffRetryPolicy;
import io.phoenixfire.core.testsupport.spi.DeclineRetryPolicy;
import io.phoenixfire.core.testsupport.spi.EmptySharedPoolStrategy;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineCoverageTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    @SuppressWarnings("unused") // invoked by JUnit, not by direct calls
    void enableSimulatedFork() {
        System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
    }

    @AfterEach
    @SuppressWarnings("unused") // invoked by JUnit, not by direct calls
    void clearProps() {
        System.clearProperty("phoenixfire.fork.main");
        System.clearProperty(SimulatedFork.PROP_MODE);
    }

    @Test
    void writesJournalFileWhenEnabled() throws Exception {
        Path journalPath = tempDir.resolve("audit").resolve("journal.ndjson");
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(true)
                .journalFile(journalPath.toFile())
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            engine.run();
        }

        assertTrue(Files.exists(journalPath));
        String content = Files.readString(journalPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"event\":\"SEED\""));
        assertTrue(content.contains("\"event\":\"PASSED\""));
    }

    @Test
    void appliesShardingAfterFilter() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .shardIndex(1)
                .shardCount(2)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(1, summary.total());
            assertEquals(1, summary.passed());
        }
    }

    @Test
    void logsRetrySummaryWhenTestsRetry() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(3)
                .rerunFailingTestsCount(2)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.total());
            assertEquals(2, summary.failed());
        }
    }

    @Test
    void exhaustsMaxAttemptsBudget() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(2)
                .rerunFailingTestsCount(5)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.failed());
        }
    }

    @Test
    void handlesNoByeForkAsCrashVictims() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_IGNORE);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.crashed());
            assertTrue(summary.hasFailures());
        }
    }

    @Test
    void handlesExecuteHangAsCrashVictims() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .heartbeatTimeoutMillis(500L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_HANG);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.crashed());
        }
    }

    @Test
    void handlesHangForkAsCrashVictims() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .heartbeatTimeoutMillis(500L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_HANG);
            ExecutionSummary summary = engine.run();
            assertEquals(0, summary.total());
        }
    }

    @Test
    void reportsEmptyDiscoveryMessage() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_DISCOVER_EMPTY);
            ExecutionSummary summary = engine.run();
            assertEquals(0, summary.total());
            assertTrue(Files.exists(tempDir.resolve("phoenixfire-report.json")));
        }
    }

    @Test
    void loadsCustomSpiAndSurvivesFailingReportWriter() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.create(ExecutionEngine.class.getClassLoader());
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.passed());
            assertTrue(Files.exists(tempDir.resolve("phoenixfire-report.json")));
        }
    }

    @Test
    void reportsNoTestsSelectedMessage() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .testFilter("NoSuchTest")
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(0, summary.total());
        }
    }

    @Test
    void envelopeIncludesDetectedHost() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            engine.run();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> report = Json.parseObject(
                Files.readString(tempDir.resolve("phoenixfire-report.json"), StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) report.get("run");
        assertNotNull(run);
        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) run.get("env");
        assertNotNull(env);
        assertNotNull(env.get("os"));
        assertNotNull(env.get("jvm"));
        assertTrue(env.get("host") != null || System.getenv("COMPUTERNAME") != null
                || System.getenv("HOSTNAME") != null);
    }

    @Test
    void recordsSkippedTests() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_SKIP);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.skipped());
            assertFalse(summary.hasFailures());
        }
    }

    @Test
    void runsSharedPoolInParallelAcrossForks() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .forkCount(2)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.passed());
        }
    }

    @Test
    void retryPolicyDeclinedLeavesTerminalFailure() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), RetryPolicy.class, DeclineRetryPolicy.class);
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(5)
                .rerunFailingTestsCount(0)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.failed());
        }
    }

    @Test
    void appliesRetryBackoff() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), RetryPolicy.class, BackoffRetryPolicy.class);
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(3)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.failed());
        }
    }

    @Test
    void skipsEmptySharedPoolPlan() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), IsolationStrategy.class, EmptySharedPoolStrategy.class);
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.crashed());
        }
    }

    @Test
    void recordsMissingOutcomesOnCleanFork() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PARTIAL);
            ExecutionSummary summary = engine.run();
            assertEquals(1, summary.passed());
            assertEquals(1, summary.crashed());
        }
    }

    @Test
    void finalizesRunningTestsWhenForkUnreachable() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .heartbeatTimeoutMillis(500L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_START_HANG);
            ExecutionSummary summary = engine.run();
            assertEquals(2, summary.crashed());
        }
    }

    @Test
    void usesDefaultReportsDirectoryWhenUnset() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(simulatedForkClasspath())
                .reportsDirectory(null)
                .forkCount(1)
                .heartbeatTimeoutMillis(20_000L)
                .journalEnabled(false)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_DISCOVER_EMPTY);
            engine.run();
        }
        assertTrue(Files.exists(Path.of("target", "phoenixfire-reports", "phoenixfire-report.json")));
    }

    @Test
    void breaksWhenNoNotRunTestsRemain() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(1)
                .heartbeatTimeoutMillis(500L)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_START_HANG);
            engine.run();
        }
    }

    @Test
    void sleepsAfterRetryBackoff() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), RetryPolicy.class, BackoffRetryPolicy.class);
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .forkCount(1)
                .maxAttempts(3)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            engine.run();
        }
    }

    @Test
    void logsRetryDeclinedMessage() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), RetryPolicy.class, DeclineRetryPolicy.class);
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(5)
                .rerunFailingTestsCount(5)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            engine.run();
        }
    }

    @Test
    void appliesParallelBackoffSleep() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), RetryPolicy.class, BackoffRetryPolicy.class);
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .forkCount(2)
                .maxAttempts(3)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            engine.run();
        }
    }

    @Test
    void logsPluralRetrySummary() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(3)
                .rerunFailingTestsCount(2)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            engine.run();
        }
    }

    @Test
    void logsRetrySummaryForRecoveredTests() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(3)
                .rerunFailingTestsCount(2)
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            engine.run();
        }
    }

    @Test
    void logsSingularRetrySummaryForOneFlakyTest() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .maxAttempts(3)
                .rerunFailingTestsCount(2)
                .testFilter("FooTest#testOne")
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
            ExecutionSummary summary = engine.run();
            assertEquals(1, summary.total());
            assertEquals(1, summary.failed());
        }
    }

    @Test
    void runsSequentialWhenOnlyOneSharedPoolUnit() throws Exception {
        PhoenixfireConfiguration config = baseConfig()
                .journalEnabled(false)
                .forkCount(2)
                .testFilter("FooTest#testOne")
                .build();

        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
            ExecutionSummary summary = engine.run();
            assertEquals(1, summary.passed());
        }
    }

    private PhoenixfireConfiguration.Builder baseConfig() {
        return PhoenixfireConfiguration.builder()
                .classpath(simulatedForkClasspath())
                .reportsDirectory(tempDir.toFile())
                .forkCount(1)
                .heartbeatTimeoutMillis(20_000L);
    }

    private static List<String> simulatedForkClasspath() {
        return List.of(
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                Path.of("target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString());
    }
}
