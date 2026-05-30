package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.spi.ForkConfig;
import io.phoenixfire.core.config.PhoenixfireConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JVM command line for a fork and launches it via {@link ProcessBuilder}, wiring in the
 * IPC port, one-time token, fork id, scan roots and classpath. The fork's stdout/stderr are merged
 * into a per-fork log file so the controller can read a diagnostic tail after a crash.
 *
 * <p>The classpath - the only realistically unbounded argument, since a large project may put
 * thousands of jars on it - is written to a JVM {@code @argfile} rather than the command line, so the
 * launch never hits the operating system's command-line length limit. The set of tests to run is
 * never placed on the command line at all; it is sent to the fork over the IPC socket.
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

        // Unsupported in production: unit tests may set these on the controller JVM only.
        String simMode = System.getProperty("phoenixfire.sim.mode");
        if (simMode != null && !simMode.isBlank()) {
            command.add("-Dphoenixfire.sim.mode=" + simMode);
        }

        // Classpath goes in an @argfile (not on argv) to stay under the OS command-line length limit.
        Path argFile = writeClasspathArgFile(forkId, logFile);
        command.add("@" + argFile);
        command.add(System.getProperty("phoenixfire.fork.main", "io.phoenixfire.runner.ForkRunnerMain"));

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

    /**
     * Writes the classpath into a JVM argument file ({@code java @file}). Entries are written with
     * forward slashes because the launcher treats backslashes as escape characters inside argfiles;
     * forward slashes are accepted in classpath entries on every platform including Windows. The
     * whole classpath is quoted so entries containing spaces survive tokenisation.
     */
    private Path writeClasspathArgFile(String forkId, Path logFile) throws IOException {
        StringBuilder cp = new StringBuilder();
        List<String> entries = config.classpath();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(entries.get(i).replace('\\', '/'));
        }
        String content = "-classpath" + System.lineSeparator() + "\"" + cp + "\"" + System.lineSeparator();

        Path file;
        if (logFile != null) {
            Files.createDirectories(logFile.toAbsolutePath().getParent());
            file = logFile.resolveSibling(forkId + ".args");
        } else {
            file = Files.createTempFile("phoenixfire-fork-" + forkId + "-", ".args");
            file.toFile().deleteOnExit();
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
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
