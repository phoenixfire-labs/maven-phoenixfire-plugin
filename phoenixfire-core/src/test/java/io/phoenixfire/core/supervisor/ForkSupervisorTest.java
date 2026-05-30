package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.spi.WorkUnit;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.ipc.IpcServer;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.testsupport.SimulatedFork;
import io.phoenixfire.core.testsupport.SimulatedForkLauncher;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkSupervisorTest {

    @TempDir
    Path tempDir;

    private IpcServer ipcServer;
    private ForkSupervisor supervisor;

    @BeforeEach
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

    private int ipcServerPort() throws Exception {
        var f = IpcServer.class.getDeclaredField("serverSocket");
        f.setAccessible(true);
        return ((java.net.ServerSocket) f.get(ipcServer)).getLocalPort();
    }

    private ForkSupervisor newSupervisor(ForkLauncher launcher) {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .heartbeatTimeoutMillis(15_000L)
                .reportsDirectory(tempDir.toFile())
                .build();
        return new ForkSupervisor(config, ipcServer, launcher, new ExecutionJournal(PhoenixfireLogger.console()),
                new DefaultFailureClassifier(), PhoenixfireLogger.console(), tempDir.resolve("forks"));
    }
}
