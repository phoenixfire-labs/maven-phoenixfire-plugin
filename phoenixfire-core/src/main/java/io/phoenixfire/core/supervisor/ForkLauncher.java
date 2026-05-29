package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.core.config.PhoenixfireConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JVM command line for a fork and launches it via {@link ProcessBuilder}, wiring in the
 * IPC port, one-time token, fork id, scan roots and classpath. The fork's stdout/stderr are merged
 * into a per-fork log file so the controller can read a diagnostic tail after a crash.
 */
public final class ForkLauncher {

    private final PhoenixfireConfiguration config;
    private final int controllerPort;

    public ForkLauncher(PhoenixfireConfiguration config, int controllerPort) {
        this.config = config;
        this.controllerPort = controllerPort;
    }

    public Process launch(String forkId, String token, ForkConfig forkConfig, Path logFile) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());

        command.addAll(config.baseJvmArgs());
        command.addAll(forkConfig.jvmArgs());

        Map<String, String> sysProps = new LinkedHashMap<>(config.systemProperties());
        sysProps.putAll(forkConfig.systemProperties());
        for (Map.Entry<String, String> e : sysProps.entrySet()) {
            command.add("-D" + e.getKey() + "=" + e.getValue());
        }

        command.add("-D" + IpcProtocol.PROP_PORT + "=" + controllerPort);
        command.add("-D" + IpcProtocol.PROP_TOKEN + "=" + token);
        command.add("-D" + IpcProtocol.PROP_FORK_ID + "=" + forkId);
        command.add("-D" + IpcProtocol.PROP_SCAN_ROOTS + "=" + String.join(File.pathSeparator, config.scanRoots()));
        command.add("-Dphoenixfire.heartbeat.interval=" + config.heartbeatIntervalMillis());

        command.add("-cp");
        command.add(String.join(File.pathSeparator, config.classpath()));
        command.add("io.phoenixfire.runner.ForkRunnerMain");

        ProcessBuilder pb = new ProcessBuilder(command);
        if (config.workingDirectory() != null) {
            pb.directory(new File(config.workingDirectory()));
        }
        pb.environment().putAll(forkConfig.environment());
        pb.environment().putAll(config.environment());
        pb.redirectErrorStream(true);
        if (logFile != null) {
            pb.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
        }
        return pb.start();
    }

    private String javaExecutable() {
        if (config.javaExecutable() != null) {
            return config.javaExecutable().getAbsolutePath();
        }
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = windows ? "java.exe" : "java";
        return new File(new File(javaHome, "bin"), exe).getAbsolutePath();
    }
}
