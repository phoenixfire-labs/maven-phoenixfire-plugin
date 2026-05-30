package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkRunnerMainTest {

    @Test
    void exitsNonZeroWhenPortMissing() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(javaBinary(), "-cp", runnerClasspath(),
                ForkRunnerMain.class.getName());
        pb.environment().remove(IpcProtocol.PROP_PORT);
        Process process = pb.start();
        assertTrue(process.waitFor(15, TimeUnit.SECONDS));
        assertTrue(process.exitValue() != 0);
    }

    private static String javaBinary() {
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(new File(javaHome, "bin"), windows ? "java.exe" : "java").getAbsolutePath();
    }

    private static String runnerClasspath() {
        String sep = File.pathSeparator;
        return String.join(sep, List.of(
                Path.of("target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-fork-runner", "target", "classes").toAbsolutePath().toString()));
    }
}
