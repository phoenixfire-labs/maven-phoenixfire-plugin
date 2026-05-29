package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Matches the default include patterns and is not excluded, so it must run and pass. */
class IncludedTest {

    @Test
    void runs() {
        assertTrue(true);
    }
}
