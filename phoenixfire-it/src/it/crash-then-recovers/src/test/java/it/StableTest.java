package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A well-behaved test that shares the fork with the recovering test. */
class StableTest {

    @Test
    void alwaysPasses() {
        assertEquals(4, 2 + 2);
    }
}
