package it;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hard-crashes the forked JVM on the very first attempt, then passes on every subsequent attempt.
 *
 * <p>Forks run with the project basedir as their working directory, so a marker file under
 * {@code target/} persists across fork attempts. The first attempt has no marker: it writes one and
 * halts the JVM (mimicking a SIGKILL). Phoenixfire detects the fork death, escalates isolation and
 * retries; on the retry the marker exists, so the test passes. The test therefore "recovers" and,
 * with the default {@code failOnFlakyTests=false}, must not fail the build.
 */
class RecoversTest {

    @Test
    void recoversAfterFirstCrash() throws IOException {
        Path marker = Paths.get("target", "phoenixfire-crash-once.marker");
        if (!Files.exists(marker)) {
            Files.createDirectories(marker.getParent());
            Files.write(marker, new byte[0]);
            // Crash on the first attempt only; bypasses shutdown hooks like a real JVM kill.
            Runtime.getRuntime().halt(137);
        }
        // Subsequent attempts: marker present -> succeed.
    }
}
