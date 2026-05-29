package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A normal, well-behaved test that must still be recorded as PASSED even though a sibling crashes. */
class PassingTest {

    @Test
    void addition() {
        assertEquals(4, 2 + 2);
    }
}
