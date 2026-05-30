package io.phoenixfire.core.testsupport;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.supervisor.ForkLauncher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Launches {@link SimulatedFork} instead of the real fork runner for deterministic unit tests. */
public final class SimulatedForkLauncher extends ForkLauncher {

    private final PhoenixfireConfiguration cfg;
    private final int port;
    private final String mode;

    public SimulatedForkLauncher(PhoenixfireConfiguration config, int controllerPort, String mode) {
        super(config, controllerPort);
        this.cfg = config;
        this.port = controllerPort;
        this.mode = mode;
    }

    @Override
    public Process launch(String forkId, String token, ForkConfig forkConfig, Path logFile) throws IOException {
        String java = cfg.javaExecutable() != null
                ? cfg.javaExecutable().getAbsolutePath()
                : javaBinary();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-D" + IpcProtocol.PROP_PORT + "=" + port);
        command.add("-D" + IpcProtocol.PROP_TOKEN + "=" + token);
        command.add("-D" + IpcProtocol.PROP_FORK_ID + "=" + forkId);
        command.add("-D" + SimulatedFork.PROP_MODE + "=" + mode);
        command.add("-cp", testClasspath());
        command.add(SimulatedFork.class.getName());

        ProcessBuilder pb = new ProcessBuilder(command);
        if (cfg.workingDirectory() != null) {
            pb.directory(new File(cfg.workingDirectory()));
        }
        pb.redirectErrorStream(true);
        if (logFile != null) {
            pb.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
        }
        return pb.start();
    }

    private static String javaBinary() {
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(new File(javaHome, "bin"), windows ? "java.exe" : "java").getAbsolutePath();
    }

    private static String testClasspath() {
        String cp = System.getProperty("java.class.path");
        String testClasses = Path.of("target", "test-classes").toAbsolutePath().toString();
        String mainClasses = Path.of("target", "classes").toAbsolutePath().toString();
        String apiClasses = Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString();
        return testClasses + File.pathSeparator + mainClasses + File.pathSeparator + apiClasses
                + File.pathSeparator + cp;
    }
}
