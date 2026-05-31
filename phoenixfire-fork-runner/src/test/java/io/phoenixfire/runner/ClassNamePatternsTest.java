package io.phoenixfire.runner;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassNamePatternsTest {

    @Test
    void convertsSurefireGlobsToRegex() {
        assertEquals(".*Foo", ClassNamePatterns.toRegex("**/Foo.java"));
        assertEquals("com\\.example\\.Foo", ClassNamePatterns.toRegex("com/example/Foo.class"));
        assertTrue(ClassNamePatterns.toRegex("**/*Test").contains(".*"));
    }

    @Test
    void toRegexesSkipsNullAndBlank() {
        List<String> regexes = ClassNamePatterns.toRegexes(
                Arrays.asList(null, "  ", "**/A.java", ""));
        assertEquals(1, regexes.size());
    }

    @Test
    void toRegexesReturnsEmptyForNullList() {
        assertTrue(ClassNamePatterns.toRegexes(null).isEmpty());
    }

    @Test
    void convertsClassFileGlobAndSpecialCharacters() {
        assertEquals("com\\.example\\.Foo", ClassNamePatterns.toRegex("com/example/Foo.class"));
        assertEquals(".*Foo", ClassNamePatterns.toRegex("**Foo"));
        assertEquals("[^.]*", ClassNamePatterns.toRegex("*"));
        assertEquals(".", ClassNamePatterns.toRegex("?"));
        assertEquals("com\\.example\\.Foo\\$Inner", ClassNamePatterns.toRegex("com/example/Foo$Inner.java"));
        assertEquals("com\\.example\\.Foo\\-Bar", ClassNamePatterns.toRegex("com/example/Foo-Bar.java"));
        assertEquals("Test123", ClassNamePatterns.toRegex("Test123.java"));
        assertEquals("com_example", ClassNamePatterns.toRegex("com_example.java"));
    }
}
