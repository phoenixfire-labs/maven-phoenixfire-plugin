package io.phoenixfire.core.testsupport;

import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.supervisor.ForkLauncher;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Launches {@link SimulatedFork} via {@link ForkLauncher} for deterministic unit tests. */
public final class SimulatedForkLauncher extends ForkLauncher {

    private final String mode;

    public SimulatedForkLauncher(PhoenixfireConfiguration config, int controllerPort, String mode) {
        super(ensureClasspath(config), controllerPort);
        this.mode = mode;
    }

    @Override
    public Process launch(String forkId, String token, ForkConfig forkConfig, Path logFile) throws IOException {
        String prevMode = System.getProperty("phoenixfire.sim.mode");
        String prevMain = System.getProperty("phoenixfire.fork.main");
        try {
            System.setProperty("phoenixfire.sim.mode", mode);
            System.setProperty("phoenixfire.fork.main", SimulatedFork.class.getName());
            return super.launch(forkId, token, forkConfig, logFile);
        } finally {
            restoreProperty("phoenixfire.sim.mode", prevMode);
            restoreProperty("phoenixfire.fork.main", prevMain);
        }
    }

    private static void restoreProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static PhoenixfireConfiguration ensureClasspath(PhoenixfireConfiguration config) {
        if (!config.classpath().isEmpty()) {
            return config;
        }
        List<String> cp = new ArrayList<>(simulatedForkClasspath());
        return PhoenixfireConfiguration.builder()
                .classpath(cp)
                .reportsDirectory(config.reportsDirectory())
                .heartbeatTimeoutMillis(config.heartbeatTimeoutMillis())
                .workingDirectory(config.workingDirectory())
                .build();
    }

    static List<String> simulatedForkClasspath() {
        return List.of(
                Path.of("target", "test-classes").toAbsolutePath().toString(),
                Path.of("target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString());
    }
}
