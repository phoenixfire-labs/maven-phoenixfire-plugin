package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.spi.WorkUnit;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.ipc.IpcServer;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.testsupport.FailingForkLauncher;
import io.phoenixfire.core.testsupport.SimulatedFork;
import io.phoenixfire.core.testsupport.SimulatedForkLauncher;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkSupervisorTest {

    @TempDir
    Path tempDir;

    private IpcServer ipcServer;
    private ForkSupervisor supervisor;

    @BeforeEach
    @SuppressWarnings("unused") // invoked by JUnit, not by direct calls
    void setUp() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .heartbeatTimeoutMillis(15_000L)
                .reportsDirectory(tempDir.toFile())
                .build();
        ipcServer = new IpcServer(PhoenixfireLogger.console());
        int port = ipcServer.start();
        SimulatedForkLauncher launcher = new SimulatedForkLauncher(config, port, SimulatedFork.MODE_DISCOVER);
        supervisor = new ForkSupervisor(config, ipcServer, launcher, new ExecutionJournal(PhoenixfireLogger.console()),
                new DefaultFailureClassifier(), PhoenixfireLogger.console(), tempDir.resolve("forks"));
    }

    @AfterEach
    @SuppressWarnings("unused") // invoked by JUnit, not by direct calls
    void tearDown() {
        if (ipcServer != null) {
            ipcServer.close();
        }
    }

    @Test
    void discoversTestsViaSimulatedFork() {
        ForkDiscoveryResult result = supervisor.runDiscovery();
        assertTrue(result.clean());
        assertEquals(2, result.discovered().size());
    }

    @Test
    void executesTestsViaSimulatedFork() throws Exception {
        SimulatedForkLauncher execLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_EXECUTE_PASS);
        ForkSupervisor execSupervisor = newSupervisor(execLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertTrue(result.clean());
        assertEquals(TestState.PASSED, result.outcomes().get(id).state());
    }

    @Test
    void classifiesFailedExecution() throws Exception {
        SimulatedForkLauncher failLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_EXECUTE_FAIL);
        ForkSupervisor failSupervisor = newSupervisor(failLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = failSupervisor.runExecution(unit);
        assertTrue(result.clean());
        assertEquals(TestState.FAILED, result.outcomes().get(id).state());
    }

    @Test
    void recordsSkippedExecution() throws Exception {
        SimulatedForkLauncher skipLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_EXECUTE_SKIP);
        ForkSupervisor skipSupervisor = newSupervisor(skipLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = skipSupervisor.runExecution(unit);
        assertTrue(result.clean());
        assertEquals(TestState.SKIPPED, result.outcomes().get(id).state());
    }

    @Test
    void terminatesHungForkOnHeartbeatTimeout() throws Exception {
        SimulatedForkLauncher hangLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).heartbeatTimeoutMillis(500L).build(),
                ipcServerPort(), SimulatedFork.MODE_HANG);
        ForkSupervisor hangSupervisor = newSupervisor(hangLauncher, 500L);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = hangSupervisor.runExecution(unit);
        assertFalse(result.clean());
        assertEquals(FailureMode.HEARTBEAT_TIMEOUT, result.failureMode());
        assertNotNull(result.diagnostic());
        assertTrue(result.diagnostic().contains("hang diagnostic"));
    }

    @Test
    void marksDirtyForkWithoutBye() throws Exception {
        SimulatedForkLauncher dirtyLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_NO_BYE);
        ForkSupervisor dirtySupervisor = newSupervisor(dirtyLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = dirtySupervisor.runExecution(unit);
        assertFalse(result.clean());
        assertNotNull(result.diagnostic());
        assertTrue(result.diagnostic().contains("diagnostic tail"));
    }

    @Test
    void discoveryResultExposesFields() {
        ForkDiscoveryResult result = supervisor.runDiscovery();
        assertNotNull(result.discovered());
        assertNotNull(result.failureMode());
        assertEquals(0, result.exitCode());
    }

    @Test
    void ignoresMessagesWithoutUniqueId() throws Exception {
        SimulatedForkLauncher nullIdLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_NULL_UNIQUE_ID);
        ForkSupervisor nullIdSupervisor = newSupervisor(nullIdLauncher);

        ForkDiscoveryResult discovery = nullIdSupervisor.runDiscovery();
        assertEquals(2, discovery.discovered().size());

        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = nullIdSupervisor.runExecution(unit);
        assertTrue(result.clean());
        assertTrue(result.outcomes().isEmpty());
        assertEquals(1, result.notStarted().size());
        assertTrue(result.notStarted().contains(id));
    }

    @Test
    void executionResultExposesNotStartedSet() throws Exception {
        SimulatedForkLauncher passLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_EXECUTE_PASS);
        ForkSupervisor execSupervisor = newSupervisor(passLauncher);
        TestId first = new TestId("uid-1", "sim.FooTest", "t1");
        TestId second = new TestId("uid-2", "sim.BarTest", "t2");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(first, second),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertNotNull(result.forkId());
        assertTrue(result.notStarted().isEmpty());
        assertEquals(2, result.outcomes().size());
    }

    @Test
    void handlesLaunchFailure() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .heartbeatTimeoutMillis(15_000L)
                .reportsDirectory(tempDir.toFile())
                .build();
        ipcServer = new IpcServer(PhoenixfireLogger.console());
        int port = ipcServer.start();
        FailingForkLauncher launcher = new FailingForkLauncher(config, port);
        supervisor = new ForkSupervisor(config, ipcServer, launcher, new ExecutionJournal(PhoenixfireLogger.console()),
                new DefaultFailureClassifier(), PhoenixfireLogger.console(), tempDir.resolve("forks"));

        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = supervisor.runExecution(unit);
        assertFalse(result.clean());
        assertTrue(result.diagnostic().contains("Launch failed"));
    }

    @Test
    void handlesSupervisorInterrupt() throws Exception {
        SimulatedForkLauncher hangLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_HANG);
        ForkSupervisor hangSupervisor = newSupervisor(hangLauncher, 60_000L);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());

        Thread worker = new Thread(() -> hangSupervisor.runExecution(unit));
        worker.start();
        Thread.sleep(500);
        worker.interrupt();
        worker.join(15_000);
        assertFalse(worker.isAlive());
        Thread.sleep(500);
    }

    @Test
    void exposesInFlightWhenTestNeverFinishes() throws Exception {
        SimulatedForkLauncher hangLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).heartbeatTimeoutMillis(500L).build(),
                ipcServerPort(), SimulatedFork.MODE_EXECUTE_START_HANG);
        ForkSupervisor hangSupervisor = newSupervisor(hangLauncher, 500L);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = hangSupervisor.runExecution(unit);
        assertNotNull(result.inFlight());
        assertEquals(id, result.inFlight());
    }

    @Test
    void clearsInFlightWhenOutcomeAlreadyRecorded() throws Exception {
        SimulatedForkLauncher staleLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_STALE_INFLIGHT);
        ForkSupervisor execSupervisor = newSupervisor(staleLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertNull(result.inFlight());
        assertEquals(TestState.PASSED, result.outcomes().get(id).state());
    }

    @Test
    void parsesNumericDurationAsNumber() throws Exception {
        SimulatedForkLauncher numericLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_NUMERIC_DURATION);
        ForkSupervisor execSupervisor = newSupervisor(numericLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertEquals(TestState.PASSED, result.outcomes().get(id).state());
    }

    @Test
    void readTailReturnsNullWhenLogPathIsDirectory(@TempDir Path logDir) throws Exception {
        ForkSupervisor sup = newSupervisor(new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_DISCOVER));
        java.lang.reflect.Method readTail = ForkSupervisor.class.getDeclaredMethod("readTail", Path.class, int.class);
        readTail.setAccessible(true);
        Path dir = logDir.resolve("not-a-file");
        Files.createDirectory(dir);
        assertNull(readTail.invoke(sup, dir, 60));
    }

    @Test
    void sendCommandSurvivesIoFailure() throws Exception {
        SimulatedForkLauncher launcher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_CLOSE_AFTER_HELLO);
        ForkSupervisor execSupervisor = newSupervisor(launcher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertFalse(result.clean());
    }

    @Test
    void parsesMissingDurationAsZero() throws Exception {
        SimulatedForkLauncher launcher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_NO_DURATION);
        ForkSupervisor execSupervisor = newSupervisor(launcher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertEquals(TestState.PASSED, result.outcomes().get(id).state());
    }

    @Test
    void parsesNonNumericDuration() throws Exception {
        SimulatedForkLauncher badDurationLauncher = new SimulatedForkLauncher(
                PhoenixfireConfiguration.builder().classpath(List.of()).build(),
                ipcServerPort(), SimulatedFork.MODE_BAD_DURATION);
        ForkSupervisor execSupervisor = newSupervisor(badDurationLauncher);
        TestId id = new TestId("uid-1", "sim.FooTest", "t");
        WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                io.phoenixfire.api.spi.ForkConfig.empty());
        ForkExecutionResult result = execSupervisor.runExecution(unit);
        assertEquals(TestState.PASSED, result.outcomes().get(id).state());
        assertNull(result.inFlight());
    }

    @Test
    void readTailReturnsNullWhenLogMissing() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .heartbeatTimeoutMillis(15_000L)
                .reportsDirectory(tempDir.toFile())
                .build();
        IpcServer localServer = new IpcServer(PhoenixfireLogger.console());
        int port = localServer.start();
        try {
            SimulatedForkLauncher launcher = new SimulatedForkLauncher(config, port, SimulatedFork.MODE_NO_BYE);
            ForkSupervisor dirtySupervisor = new ForkSupervisor(config, localServer, launcher,
                    new ExecutionJournal(PhoenixfireLogger.console()), new DefaultFailureClassifier(),
                    PhoenixfireLogger.console(), null);
            TestId id = new TestId("uid-1", "sim.FooTest", "t");
            WorkUnit unit = new WorkUnit("u1", IsolationLevel.FRESH_FORK, List.of(id),
                    io.phoenixfire.api.spi.ForkConfig.empty());
            ForkExecutionResult result = dirtySupervisor.runExecution(unit);
            assertFalse(result.clean());
            assertNull(result.diagnostic());
        } finally {
            localServer.close();
        }
    }

    private ForkSupervisor newSupervisor(ForkLauncher launcher) {
        return newSupervisor(launcher, 15_000L);
    }

    private ForkSupervisor newSupervisor(ForkLauncher launcher, long heartbeatTimeoutMillis) {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .heartbeatTimeoutMillis(heartbeatTimeoutMillis)
                .reportsDirectory(tempDir.toFile())
                .build();
        return new ForkSupervisor(config, ipcServer, launcher, new ExecutionJournal(PhoenixfireLogger.console()),
                new DefaultFailureClassifier(), PhoenixfireLogger.console(), tempDir.resolve("forks"));
    }

    private int ipcServerPort() throws Exception {
        var f = IpcServer.class.getDeclaredField("serverSocket");
        f.setAccessible(true);
        return ((java.net.ServerSocket) f.get(ipcServer)).getLocalPort();
    }
}
