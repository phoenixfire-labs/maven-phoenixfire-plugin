package io.phoenixfire.core;

import io.phoenixfire.core.util.ArgLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgLineTest {

    @Test
    void nullOrBlankYieldsNoTokens() {
        assertTrue(ArgLine.tokenize(null).isEmpty());
        assertTrue(ArgLine.tokenize("   ").isEmpty());
    }

    @Test
    void splitsOnWhitespace() {
        assertEquals(List.of("-Xmx1g", "-XX:+UseG1GC", "-Dfile.encoding=UTF-8"),
                ArgLine.tokenize("-Xmx1g  -XX:+UseG1GC\t-Dfile.encoding=UTF-8"));
    }

    @Test
    void keepsDoubleQuotedValueWithSpacesAsOneToken() {
        assertEquals(List.of("-Dmsg=hello world", "-Xmx1g"),
                ArgLine.tokenize("-Dmsg=\"hello world\" -Xmx1g"));
    }

    @Test
    void keepsSingleQuotedValueWithSpacesAsOneToken() {
        assertEquals(List.of("-Dmsg=hello world"),
                ArgLine.tokenize("-Dmsg='hello world'"));
    }

    @Test
    void quotesInsideAToken() {
        assertEquals(List.of("-Dpath=/a b/c", "-Dx=1"),
                ArgLine.tokenize("-Dpath=\"/a b/c\" -Dx=1"));
    }

    @Test
    void emptyQuotedValueProducesEmptyAssignment() {
        assertEquals(List.of("-Dempty="), ArgLine.tokenize("-Dempty=\"\""));
    }
}
