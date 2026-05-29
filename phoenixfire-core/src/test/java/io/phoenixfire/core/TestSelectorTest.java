package io.phoenixfire.core;

import io.phoenixfire.api.model.TestId;
import io.phoenixfire.core.select.TestSelector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSelectorTest {

    private static TestId test(String fqcn, String method) {
        String uid = "[engine:junit-jupiter]/[class:" + fqcn + "]/[method:" + method + "()]";
        return new TestId(uid, fqcn, method + "()");
    }

    private final List<TestId> all = List.of(
            test("com.acme.FooTest", "alpha"),
            test("com.acme.FooTest", "beta"),
            test("com.acme.BarTest", "gamma"),
            test("com.acme.slow.SlowTest", "delta"),
            test("com.acme.OddballChecks", "epsilon"));

    private List<String> classesMethods(List<TestId> tests) {
        return tests.stream()
                .map(t -> simple(t.className()) + "#" + t.displayName().replace("()", ""))
                .collect(Collectors.toList());
    }

    private static String simple(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    @Test
    void emptyExpressionSelectsEverything() {
        assertTrue(TestSelector.parse(null).isEmpty());
        assertTrue(TestSelector.parse("  ").isEmpty());
        assertEquals(all, TestSelector.parse("").filter(all));
    }

    @Test
    void selectsBySimpleClassName() {
        List<TestId> out = TestSelector.parse("FooTest").filter(all);
        assertEquals(List.of("FooTest#alpha", "FooTest#beta"), classesMethods(out));
    }

    @Test
    void selectsByFullyQualifiedClassName() {
        List<TestId> out = TestSelector.parse("com.acme.BarTest").filter(all);
        assertEquals(List.of("BarTest#gamma"), classesMethods(out));
    }

    @Test
    void selectsBySingleMethod() {
        List<TestId> out = TestSelector.parse("FooTest#beta").filter(all);
        assertEquals(List.of("FooTest#beta"), classesMethods(out));
    }

    @Test
    void selectsMultipleMethodsWithPlus() {
        List<TestId> out = TestSelector.parse("FooTest#alpha+beta").filter(all);
        assertEquals(List.of("FooTest#alpha", "FooTest#beta"), classesMethods(out));
    }

    @Test
    void supportsClassWildcards() {
        List<TestId> out = TestSelector.parse("*Test").filter(all);
        assertEquals(List.of("FooTest#alpha", "FooTest#beta", "BarTest#gamma", "SlowTest#delta"),
                classesMethods(out));
    }

    @Test
    void supportsMethodWildcards() {
        List<TestId> out = TestSelector.parse("FooTest#a*").filter(all);
        assertEquals(List.of("FooTest#alpha"), classesMethods(out));
    }

    @Test
    void supportsNegation() {
        List<TestId> out = TestSelector.parse("*Test,!SlowTest").filter(all);
        assertEquals(List.of("FooTest#alpha", "FooTest#beta", "BarTest#gamma"), classesMethods(out));
    }

    @Test
    void commaSeparatesMultipleClasses() {
        List<TestId> out = TestSelector.parse("BarTest,SlowTest").filter(all);
        assertEquals(List.of("BarTest#gamma", "SlowTest#delta"), classesMethods(out));
    }

    @Test
    void stripsJavaSuffix() {
        List<TestId> out = TestSelector.parse("BarTest.java").filter(all);
        assertEquals(List.of("BarTest#gamma"), classesMethods(out));
    }

    @Test
    void discoveryGlobsWidenForNamedClassesOnly() {
        // Inclusions produce widening globs; pure exclusions do not (defaults stay in effect).
        assertEquals(List.of("**/FooTest"), TestSelector.parse("FooTest").discoveryIncludeGlobs());
        assertTrue(TestSelector.parse("!FooTest").discoveryIncludeGlobs().isEmpty());
    }

    @Test
    void onlyExclusionsKeepEverythingElse() {
        List<TestId> out = TestSelector.parse("!FooTest").filter(all);
        assertFalse(classesMethods(out).contains("FooTest#alpha"));
        assertTrue(classesMethods(out).contains("BarTest#gamma"));
        assertTrue(classesMethods(out).contains("OddballChecks#epsilon"));
    }
}
