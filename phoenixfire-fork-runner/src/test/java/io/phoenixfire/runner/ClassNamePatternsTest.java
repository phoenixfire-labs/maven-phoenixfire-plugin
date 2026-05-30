package io.phoenixfire.runner;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassNamePatternsTest {

    @Test
    void convertsSurefireGlobsToRegex() {
        assertEquals(".*Foo.*", ClassNamePatterns.toRegex("**/Foo.java"));
        assertEquals("com\\.example\\.Foo", ClassNamePatterns.toRegex("com/example/Foo.class"));
        assertTrue(ClassNamePatterns.toRegex("**/*Test").contains(".*"));
    }

    @Test
    void toRegexesSkipsNullAndBlank() {
        List<String> regexes = ClassNamePatterns.toRegexes(
                Arrays.asList(null, "  ", "**/A.java", ""));
        assertEquals(1, regexes.size());
    }
}
