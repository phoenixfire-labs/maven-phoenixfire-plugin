package io.phoenixfire.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Minimal test class discovered and executed by {@link ForkRunnerMainTest}. */
class ForkExecutionFixture {

    @Test
    void passes() {
        assertEquals(1, 1);
    }
}
