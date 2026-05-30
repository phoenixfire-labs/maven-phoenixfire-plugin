package it;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hard-crashes the forked JVM on the first attempt, then passes. With
 * {@code sharedForkPoolMaxPasses=2}, Phoenixfire must retry this test in a fresh SHARED_FORK_POOL
 * fork (not escalate to FRESH_FORK); on the retry the marker exists, so it passes at the shared level.
 */
class ResumeTest {

    @Test
    void recoversInSharedPool() throws IOException {
        Path marker = Paths.get("target", "phoenixfire-shared-resume.marker");
        if (!Files.exists(marker)) {
            Files.createDirectories(marker.getParent());
            Files.write(marker, new byte[0]);
            Runtime.getRuntime().halt(137);
        }
    }
}
