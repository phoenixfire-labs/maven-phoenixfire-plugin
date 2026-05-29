package it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringTest {

    @Test
    void concatenation() {
        assertEquals("ab", "a" + "b");
    }
}
