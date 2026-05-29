package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs inside the fork and asserts that the configured {@code argLine} arrived intact:
 *  - {@code -Dphoenixfire.demo="hello world"} survived tokenisation as a single, space-containing value;
 *  - {@code @{extra.args}} was late-expanded from the build-time {@code extra.args} property.
 */
class ArgLineTest {

    @Test
    void quotedArgIsOneSystemProperty() {
        assertEquals("hello world", System.getProperty("phoenixfire.demo"));
    }

    @Test
    void lateExpandedPropertyWasApplied() {
        assertEquals("present", System.getProperty("phoenixfire.extra"));
    }
}
