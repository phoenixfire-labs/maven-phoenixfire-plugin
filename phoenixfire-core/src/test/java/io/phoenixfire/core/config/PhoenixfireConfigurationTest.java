package io.phoenixfire.core.config;

import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
