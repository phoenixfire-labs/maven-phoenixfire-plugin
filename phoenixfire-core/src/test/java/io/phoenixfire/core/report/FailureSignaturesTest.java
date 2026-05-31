package io.phoenixfire.core.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FailureSignaturesTest {

    @Test
    void hashUsesSha256Digest() {
        String sig = FailureSignatures.hash("normalized", "SHA-256");
        assertNotNull(sig);
        assertEquals(12, sig.length());
    }

    @Test
    void hashFallsBackWhenAlgorithmMissing() {
        assertEquals(Integer.toHexString("boom".hashCode()), FailureSignatures.hash("boom", "NO-SUCH-ALGO"));
    }

    @Test
    void returnsNullWhenNoText() {
        assertNull(FailureSignatures.of(null, null));
        assertNull(FailureSignatures.of("  ", ""));
    }

    @Test
    void prefersStackTraceAndNormalizes() {
        String sig1 = FailureSignatures.of("at Foo.bar(Foo.java:10) id=0xabc", null);
        String sig2 = FailureSignatures.of("at Foo.bar(Foo.java:99) id=0xdef", null);
        assertNotNull(sig1);
        assertEquals(sig1, sig2);
        assertEquals(12, sig1.length());
    }

    @Test
    void fallsBackToMessage() {
        assertNotNull(FailureSignatures.of(null, "assertion failed"));
    }
}
