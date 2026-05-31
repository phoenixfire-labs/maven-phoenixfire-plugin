package io.phoenixfire.core.config;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoenixfireConfigurationTest {

    @Test
    void builderClampsAndNormalizes() {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .forkCount(0)
                .maxAttempts(0)
                .rerunFailingTestsCount(-1)
                .sharedForkPoolMaxPasses(0)
                .testFilter("  ")
                .escalationLadder(null)
                .build();
        assertEquals(1, config.forkCount());
        assertEquals(1, config.maxAttempts());
        assertEquals(0, config.rerunFailingTestsCount());
        assertEquals(1, config.sharedForkPoolMaxPasses());
        assertNull(config.testFilter());
        assertEquals(3, config.escalationLadder().size());
        assertTrue(config.runMetadata().labels().isEmpty());
    }

    @Test
    void copiesCollections() {
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of("a"))
                .runMetadata(RunMetadata.builder().gitSha("sha").build())
                .escalationLadder(List.of(IsolationLevel.FRESH_FORK))
                .build();
        assertEquals("sha", config.runMetadata().gitSha());
        assertEquals(IsolationLevel.FRESH_FORK, config.escalationLadder().get(0));
    }

    @Test
    void builderSettersPopulateAllFields(@TempDir Path tempDir) {
        File java = new File(System.getProperty("java.home"), "bin/java");
        RunMetadata metadata = RunMetadata.builder().gitSha("abc123").build();
        PhoenixfireConfiguration config = PhoenixfireConfiguration.builder()
                .classpath(List.of("cp1", "cp2"))
                .scanRoots(List.of("root"))
                .javaExecutable(java)
                .includes(List.of("**/*Test"))
                .excludes(List.of("**/*IT"))
                .baseJvmArgs(List.of("-Xmx512m"))
                .systemProperties(Map.of("k", "v"))
                .environment(Map.of("ENV", "test"))
                .forkCount(4)
                .maxAttempts(5)
                .rerunFailingTestsCount(2)
                .sharedForkPoolMaxPasses(3)
                .heartbeatIntervalMillis(1_000L)
                .heartbeatTimeoutMillis(60_000L)
                .backoffMillis(250L)
                .escalationLadder(List.of(IsolationLevel.ONE_FORK_PER_CLASS))
                .journalEnabled(false)
                .reportsDirectory(tempDir.toFile())
                .journalFile(tempDir.resolve("journal.ndjson").toFile())
                .testFailureIgnore(true)
                .workingDirectory(tempDir.toString())
                .runMetadata(metadata)
                .testFilter("  FooTest  ")
                .shardIndex(2)
                .shardCount(4)
                .build();

        assertEquals(List.of("cp1", "cp2"), config.classpath());
        assertEquals(List.of("root"), config.scanRoots());
        assertEquals(java, config.javaExecutable());
        assertEquals(List.of("**/*Test"), config.includes());
        assertEquals(List.of("**/*IT"), config.excludes());
        assertEquals(List.of("-Xmx512m"), config.baseJvmArgs());
        assertEquals("v", config.systemProperties().get("k"));
        assertEquals("test", config.environment().get("ENV"));
        assertEquals(4, config.forkCount());
        assertEquals(5, config.maxAttempts());
        assertEquals(2, config.rerunFailingTestsCount());
        assertEquals(3, config.sharedForkPoolMaxPasses());
        assertEquals(1_000L, config.heartbeatIntervalMillis());
        assertEquals(60_000L, config.heartbeatTimeoutMillis());
        assertEquals(250L, config.backoffMillis());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, config.escalationLadder().get(0));
        assertFalse(config.journalEnabled());
        assertEquals(tempDir.toFile(), config.reportsDirectory());
        assertEquals(tempDir.resolve("journal.ndjson").toFile(), config.journalFile());
        assertTrue(config.testFailureIgnore());
        assertEquals(tempDir.toString(), config.workingDirectory());
        assertEquals("abc123", config.runMetadata().gitSha());
        assertEquals("FooTest", config.testFilter());
        assertEquals(2, config.shardIndex());
        assertEquals(4, config.shardCount());
    }
}
