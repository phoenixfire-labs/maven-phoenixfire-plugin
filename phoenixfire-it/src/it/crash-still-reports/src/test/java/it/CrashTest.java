package it;

import org.junit.jupiter.api.Test;

/**
 * Simulates a non-recoverable forked JVM failure by halting the JVM mid-test. Surefire would lose
 * accountability for this test (and possibly its siblings); Phoenixfire must detect the fork death,
 * escalate isolation, and ultimately record this test in a terminal CRASHED state.
 */
class CrashTest {

    @Test
    void hardCrashesTheJvm() {
        // Runtime.halt bypasses shutdown hooks and kills the JVM immediately, mimicking a SIGKILL.
        Runtime.getRuntime().halt(137);
    }
}
