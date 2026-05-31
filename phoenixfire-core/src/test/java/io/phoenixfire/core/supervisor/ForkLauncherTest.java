package io.phoenixfire.core.supervisor;

import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.testsupport.ExitZeroMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void writesClasspathArgFileAndStartsProcess() throws Exception {
        Path cpEntry = tempDir.resolve("classes");
        Files.createDirectories(cpEntry);
        String testClasses = Path.of("target", "test-classes").toAbsolutePath().toString();
        String mainClasses = Path.of("target", "classes").toAbsolutePath().toString();
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of(testClasses, mainClasses, cpEntry.toString()))
                .javaExecutable(new File(System.getProperty("java.home"), "bin/java"))
                .workingDirectory(tempDir.toString())
                .build();
        ForkLauncher launcher = new ForkLauncher(config, 1);
        System.setProperty("phoenixfire.fork.main", ExitZeroMain.class.getName());
        try {
            Process process = launcher.launch("fork-test", "token",
                    io.phoenixfire.api.spi.ForkConfig.empty(), tempDir.resolve("fork.log"));
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(Files.exists(tempDir.resolve("fork-test.args")));
        } finally {
            System.clearProperty("phoenixfire.fork.main");
        }
    }

    @Test
    void launchesWithoutLogFileUsesTempArgFile() throws Exception {
        String testClasses = Path.of("target", "test-classes").toAbsolutePath().toString();
        String mainClasses = Path.of("target", "classes").toAbsolutePath().toString();
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of(testClasses, mainClasses))
                .javaExecutable(new File(System.getProperty("java.home"), "bin/java"))
                .systemProperties(Map.of("extra", "prop"))
                .environment(Map.of("PHOENIX", "1"))
                .baseJvmArgs(List.of("-Dtest.launcher=true"))
                .build();
        ForkLauncher launcher = new ForkLauncher(config, 42);
        System.setProperty("phoenixfire.fork.main", ExitZeroMain.class.getName());
        try {
            Process process = launcher.launch("fork-temp", "token",
                    new io.phoenixfire.api.spi.ForkConfig(
                            List.of("-Xms16m"),
                            Map.of("fork", "yes"),
                            Map.of("FORK_ENV", "1")),
                    null);
            assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            System.clearProperty("phoenixfire.fork.main");
        }
    }
}
