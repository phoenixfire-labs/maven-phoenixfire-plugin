package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deliberately does NOT match the default {@code *Test} include globs. It is selected only because
 * {@code -Dtest=Checks#fast*} overrides the includes and widens discovery to find it.
 */
class Checks {

    @Test
    void fastOne() {
        assertTrue(true);
    }

    @Test
    void fastTwo() {
        assertTrue(true);
    }

    @Test
    void slowExcludedByMethodFilter() {
        // Would fail if it were run, but the #fast* method filter must exclude it.
        throw new AssertionError("this method should have been filtered out by -Dtest=Checks#fast*");
    }
}
