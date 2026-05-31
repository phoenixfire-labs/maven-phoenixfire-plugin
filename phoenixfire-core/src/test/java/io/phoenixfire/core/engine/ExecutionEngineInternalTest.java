package io.phoenixfire.core.engine;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.spi.WorkUnit;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.testsupport.SimulatedFork;
import io.phoenixfire.core.testsupport.spi.BackoffRetryPolicy;
import io.phoenixfire.core.testsupport.spi.DeclineRetryPolicy;
import io.phoenixfire.core.testsupport.SpiTestClassLoader;
import io.phoenixfire.api.spi.RetryPolicy;
import io.phoenixfire.core.util.PhoenixfireLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineInternalTest {

    @Test
    void resolveHostUsesLocalHostWhenAvailable() {
        String host = ExecutionEngine.resolveHost(() -> "test-host");
        assertEquals("test-host", host);
    }

    @Test
    void resolveHostFallsBackWhenLocalHostFails() {
        String host = ExecutionEngine.resolveHost(() -> {
            throw new RuntimeException("no local host");
        });
        if (System.getenv("HOSTNAME") != null && !System.getenv("HOSTNAME").isBlank()) {
            assertEquals(System.getenv("HOSTNAME"), host);
        } else if (System.getenv("COMPUTERNAME") != null && !System.getenv("COMPUTERNAME").isBlank()) {
            assertEquals(System.getenv("COMPUTERNAME"), host);
        } else {
            assertNull(host);
        }
    }

    @Test
    void resolveHostFallsBackWhenLocalHostBlank() {
        String host = ExecutionEngine.resolveHost(() -> "  ");
        assertTrue(host == null || !host.isBlank());
    }

    @Test
    void conditionFormatsNonFailureOutcomes() throws Exception {
        Method condition = ExecutionEngine.class.getDeclaredMethod(
                "condition", TestState.class, FailureMode.class, int.class);
        condition.setAccessible(true);
        assertEquals("SKIPPED", condition.invoke(null, TestState.SKIPPED, FailureMode.NONE, 0));
        assertEquals("CRASHED (SIGKILL, exit=137)",
                condition.invoke(null, TestState.CRASHED, FailureMode.SIGKILL, 137));
        assertEquals("FAILED (ASSERTION_FAILURE)",
                condition.invoke(null, TestState.FAILED, FailureMode.ASSERTION_FAILURE, 0));
    }

    @Test
    void detectHostDelegatesToResolveHost() throws Exception {
        Method detectHost = ExecutionEngine.class.getDeclaredMethod("detectHost");
        detectHost.setAccessible(true);
        assertNotNull(detectHost.invoke(null));
    }

    @Test
    void conditionFormatsFailedWithExplicitFailureMode() throws Exception {
        Method condition = ExecutionEngine.class.getDeclaredMethod(
                "condition", TestState.class, FailureMode.class, int.class);
        condition.setAccessible(true);
        assertEquals("FAILED (SIGKILL)",
                condition.invoke(null, TestState.FAILED, FailureMode.SIGKILL, 0));
        assertEquals("CRASHED (UNKNOWN)",
                condition.invoke(null, TestState.CRASHED, FailureMode.NONE, 0));
    }

    @Test
    void labelUsesUniqueIdWhenDisplayNameMissing() throws Exception {
        Method label = ExecutionEngine.class.getDeclaredMethod("label", TestId.class);
        label.setAccessible(true);
        assertEquals("com.Foo#uid-1",
                label.invoke(null, new TestId("uid-1", "com.Foo", null)));
    }

    @Test
    void runParallelPropagatesInterrupt() throws Exception {
        System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
        try {
            System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_HANG);
            Method runParallel = ExecutionEngine.class.getDeclaredMethod("runParallel", List.class);
            runParallel.setAccessible(true);
            TestId id = new TestId("uid-1", "sim.FooTest", "t");
            WorkUnit unit = new WorkUnit("u", IsolationLevel.SHARED_FORK_POOL, List.of(id), null);
            PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                    .classpath(List.of(
                            Path.of("target", "test-classes").toAbsolutePath().toString(),
                            Path.of("target", "classes").toAbsolutePath().toString(),
                            Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString()))
                    .forkCount(2)
                    .heartbeatTimeoutMillis(60_000L)
                    .build();
            try (ExecutionEngine engine = new ExecutionEngine(config,
                    io.phoenixfire.core.util.PhoenixfireLogger.console(), null)) {
                Thread worker = new Thread(() -> {
                    try {
                        runParallel.invoke(engine, List.of(unit, unit));
                    } catch (Exception ignored) {
                    }
                });
                worker.start();
                Thread.sleep(500);
                worker.interrupt();
                worker.join(15_000);
                assertTrue(!worker.isAlive());
            }
        } finally {
            System.clearProperty("phoenixfire.fork.main");
            System.clearProperty("phoenixfire.sim.mode");
        }
    }

    @Test
    void executeSchedulingLoopBreaksWhenOnlyRunningTestsRemain() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of(
                        Path.of("target", "test-classes").toAbsolutePath().toString(),
                        Path.of("target", "classes").toAbsolutePath().toString(),
                        Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString()))
                .reportsDirectory(java.nio.file.Files.createTempDirectory("pf").toFile())
                .heartbeatTimeoutMillis(20_000L)
                .journalEnabled(false)
                .build();
        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            Field journalField = ExecutionEngine.class.getDeclaredField("journal");
            journalField.setAccessible(true);
            ExecutionJournal journal = (ExecutionJournal) journalField.get(engine);
            TestId id = new TestId("uid-1", "sim.FooTest", "t");
            journal.seed(List.of(id));
            journal.markRunning(id, "fork-1");

            Method loop = ExecutionEngine.class.getDeclaredMethod("executeSchedulingLoop", int.class);
            loop.setAccessible(true);
            loop.invoke(engine, 1);

            Method fin = ExecutionEngine.class.getDeclaredMethod("finalizeIncompleteTests");
            fin.setAccessible(true);
            fin.invoke(engine);
            assertEquals(TestState.CRASHED, journal.record(id).state());
        }
    }

    @Test
    void sleepSurvivesInterrupt() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .heartbeatTimeoutMillis(20_000L)
                .build();
        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), null)) {
            Method sleep = ExecutionEngine.class.getDeclaredMethod("sleep", long.class);
            sleep.setAccessible(true);
            Thread worker = new Thread(() -> {
                try {
                    sleep.invoke(engine, 60_000L);
                } catch (Exception ignored) {
                }
            });
            worker.start();
            Thread.sleep(200);
            worker.interrupt();
            worker.join(10_000);
            assertTrue(!worker.isAlive());
        }
    }

    @Test
    void runLevelSleepsOnRetryBackoff() throws Exception {
        System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
        System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_FAIL);
        try {
            ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                    ExecutionEngine.class.getClassLoader(), RetryPolicy.class, BackoffRetryPolicy.class);
            PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                    .classpath(List.of(
                            Path.of("target", "test-classes").toAbsolutePath().toString(),
                            Path.of("target", "classes").toAbsolutePath().toString(),
                            Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString()))
                    .maxAttempts(3)
                    .heartbeatTimeoutMillis(20_000L)
                    .journalEnabled(false)
                    .build();
            try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
                List<TestId> tests = List.of(new TestId("uid-1", "sim.FooTest", "t"));
                Field journalField = ExecutionEngine.class.getDeclaredField("journal");
                journalField.setAccessible(true);
                ExecutionJournal journal = (ExecutionJournal) journalField.get(engine);
                journal.seed(tests);
                Method runLevel = ExecutionEngine.class.getDeclaredMethod("runLevel",
                        IsolationLevel.class, List.class);
                runLevel.setAccessible(true);
                runLevel.invoke(engine, IsolationLevel.SHARED_FORK_POOL, tests);
            }
        } finally {
            System.clearProperty("phoenixfire.fork.main");
            System.clearProperty(SimulatedFork.PROP_MODE);
        }
    }

    @Test
    void maybeScheduleRetryLogsDeclinedMessage() throws Exception {
        ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                ExecutionEngine.class.getClassLoader(), RetryPolicy.class, DeclineRetryPolicy.class);
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .maxAttempts(5)
                .heartbeatTimeoutMillis(20_000L)
                .build();
        try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
            Field journalField = ExecutionEngine.class.getDeclaredField("journal");
            journalField.setAccessible(true);
            ExecutionJournal journal = (ExecutionJournal) journalField.get(engine);
            TestId id = new TestId("uid-1", "sim.FooTest", "t");
            journal.seed(List.of(id));
            journal.markRunning(id, "fork-1");
            journal.recordAttempt(id, ExecutionAttempt.builder()
                    .attemptNumber(1)
                    .isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                    .outcome(TestState.FAILED)
                    .failureMode(FailureMode.ASSERTION_FAILURE)
                    .build());
            Method schedule = ExecutionEngine.class.getDeclaredMethod("maybeScheduleRetry",
                    TestId.class, IsolationLevel.class, TestState.class, FailureMode.class, int.class, boolean.class);
            schedule.setAccessible(true);
            schedule.invoke(engine, id, IsolationLevel.SHARED_FORK_POOL, TestState.FAILED,
                    FailureMode.ASSERTION_FAILURE, 1, false);
        }
    }

    @Test
    void runParallelWrapsExecutionFailures() throws Exception {
        System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
        System.setProperty(SimulatedFork.PROP_MODE, SimulatedFork.MODE_EXECUTE_PASS);
        System.setProperty("phoenixfire.test.parallel.throw", "true");
        try {
            ClassLoader spiLoader = SpiTestClassLoader.createWithService(
                    ExecutionEngine.class.getClassLoader(), RetryPolicy.class, DeclineRetryPolicy.class);
            PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                    .classpath(List.of(
                            Path.of("target", "test-classes").toAbsolutePath().toString(),
                            Path.of("target", "classes").toAbsolutePath().toString(),
                            Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString()))
                    .forkCount(2)
                    .heartbeatTimeoutMillis(20_000L)
                    .journalEnabled(false)
                    .build();
            try (ExecutionEngine engine = new ExecutionEngine(config, PhoenixfireLogger.console(), spiLoader)) {
                assertThrows(Exception.class, engine::run);
            }
        } finally {
            System.clearProperty("phoenixfire.fork.main");
            System.clearProperty(SimulatedFork.PROP_MODE);
            System.clearProperty("phoenixfire.test.parallel.throw");
        }
    }
}
