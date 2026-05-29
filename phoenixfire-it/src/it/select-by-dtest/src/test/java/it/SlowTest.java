package it;

import org.junit.jupiter.api.Test;

/**
 * Matches the default {@code *Test} globs and would fail if executed. A passing build proves that
 * {@code -Dtest=Checks#fast*} overrode the defaults and this class was never discovered/run.
 */
class SlowTest {

    @Test
    void mustNotRun() {
        throw new AssertionError("SlowTest should have been excluded by -Dtest");
    }
}
