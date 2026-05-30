package io.phoenixfire.core.discovery;

import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.ipc.IpcServer;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.supervisor.DefaultFailureClassifier;
import io.phoenixfire.core.supervisor.ForkSupervisor;
import io.phoenixfire.core.testsupport.SimulatedForkLauncher;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryServiceTest {

    @TempDir
    Path tempDir;

    private IpcServer ipcServer;

    @BeforeEach
    void setUp() throws Exception {
        ipcServer = new IpcServer(PhoenixfireLogger.console());
        ipcServer.start();
    }

    @AfterEach
    void tearDown() {
        if (ipcServer != null) {
            ipcServer.close();
        }
    }

    @Test
    void logsCleanDiscovery() throws Exception {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of())
                .reportsDirectory(tempDir.toFile())
                .build();
        int port = ipcServerPort();
        ForkSupervisor supervisor = new ForkSupervisor(config, ipcServer,
                new SimulatedForkLauncher(config, port, io.phoenixfire.core.testsupport.SimulatedFork.MODE_DISCOVER),
                new ExecutionJournal(PhoenixfireLogger.console()),
                new DefaultFailureClassifier(), PhoenixfireLogger.console(), tempDir.resolve("forks"));
        DiscoveryService service = new DiscoveryService(supervisor, PhoenixfireLogger.console());
        assertEquals(2, service.discover().size());
    }

    private int ipcServerPort() throws Exception {
        var f = IpcServer.class.getDeclaredField("serverSocket");
        f.setAccessible(true);
        return ((java.net.ServerSocket) f.get(ipcServer)).getLocalPort();
    }
}
