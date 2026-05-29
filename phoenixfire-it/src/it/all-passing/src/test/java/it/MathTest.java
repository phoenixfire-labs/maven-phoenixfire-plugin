package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathTest {

    @Test
    void addition() {
        assertEquals(4, 2 + 2);
    }

    @Test
    void comparison() {
        assertTrue(3 > 2);
    }
}
