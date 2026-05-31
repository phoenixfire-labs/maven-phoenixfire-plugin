package io.phoenixfire.core;

import io.phoenixfire.api.model.TestId;
import io.phoenixfire.core.select.TestSelector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void skipsBlankCommaSeparatedTokens() {
        List<TestId> out = TestSelector.parse("FooTest,, ,!").filter(all);
        assertEquals(List.of("FooTest#alpha", "FooTest#beta"), classesMethods(out));
    }

    @Test
    void stripsClassSuffix() {
        List<TestId> out = TestSelector.parse("BarTest.class").filter(all);
        assertEquals(List.of("BarTest#gamma"), classesMethods(out));
    }

    @Test
    void matchesMethodViaDisplayName() {
        TestId viaDisplay = new TestId("x", "com.acme.FooTest", "alphaDisplay()");
        List<TestId> out = TestSelector.parse("FooTest#alphaDisplay").filter(List.of(viaDisplay));
        assertEquals(1, out.size());
    }

    @Test
    void matchesTemplateMethodUniqueId() {
        TestId template = new TestId(
                "[engine:junit-jupiter]/[class:com.acme.FooTest]/[test-template:case()]",
                "com.acme.FooTest", "case()");
        List<TestId> out = TestSelector.parse("FooTest#case").filter(List.of(template));
        assertEquals(1, out.size());
    }

    @Test
    void selectsMethodOnlyExpression() {
        TestId methodOnly = new TestId("x", "com.acme.FooTest", "alpha()");
        List<TestId> out = TestSelector.parse("#alpha").filter(List.of(methodOnly));
        assertEquals(1, out.size());
    }

    @Test
    void matchesQuestionMarkWildcard() {
        List<TestId> out = TestSelector.parse("F?oTest").filter(all);
        assertEquals(List.of("FooTest#alpha", "FooTest#beta"), classesMethods(out));
    }

    @Test
    void methodNameOfReturnsNullForPlainUniqueId() throws Exception {
        var method = TestSelector.class.getDeclaredMethod("methodNameOf", TestId.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, new TestId("plain-id", "com.acme.FooTest", "alpha()")));
    }

    @Test
    void matchesTestsWithEmptyClassName() {
        TestId emptyClass = new TestId("u", "", "alpha()");
        List<TestId> out = TestSelector.parse("*").filter(List.of(emptyClass));
        assertEquals(1, out.size());
    }

    @Test
    void matchesClassWithSpecialGlobCharacters() {
        TestId dotted = new TestId("x", "com.acme.FooTest", "alpha()");
        List<TestId> out = TestSelector.parse("com.acme.FooTest").filter(List.of(dotted));
        assertEquals(1, out.size());
    }

    @Test
    void methodOnlyExpressionUsesWildcardClass() {
        TestId methodOnly = new TestId("x", "com.acme.FooTest", "alpha()");
        List<TestId> out = TestSelector.parse("#beta").filter(List.of(methodOnly));
        assertTrue(out.isEmpty());
        List<TestId> match = TestSelector.parse("#alpha").filter(List.of(methodOnly));
        assertEquals(1, match.size());
    }

    @Test
    void methodNameOfReturnsNullForNullId() throws Exception {
        var method = TestSelector.class.getDeclaredMethod("methodNameOf", TestId.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, new Object[] {null}));
    }

    @Test
    void displayMethodOfReturnsNullForNullId() throws Exception {
        var method = TestSelector.class.getDeclaredMethod("displayMethodOf", TestId.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, new Object[] {null}));
    }

    @Test
    void displayMethodOfReturnsNameWithoutParentheses() throws Exception {
        var method = TestSelector.class.getDeclaredMethod("displayMethodOf", TestId.class);
        method.setAccessible(true);
        assertEquals("alpha", method.invoke(null, new TestId("u", "C", "alpha")));
    }

    @Test
    void matchesGlobWithSpecialCharacters() {
        TestId dotted = new TestId("x", "com.acme.Foo+Test", "alpha()");
        List<TestId> out = TestSelector.parse("com.acme.Foo+Test").filter(List.of(dotted));
        assertEquals(1, out.size());
    }

    @Test
    void onlyExclusionsKeepEverythingElse() {
        List<TestId> out = TestSelector.parse("!FooTest").filter(all);
        assertFalse(classesMethods(out).contains("FooTest#alpha"));
        assertTrue(classesMethods(out).contains("BarTest#gamma"));
        assertTrue(classesMethods(out).contains("OddballChecks#epsilon"));
    }

    @Test
    void methodNameOfReturnsNullForNullUniqueId() throws Exception {
        TestId id = new TestId("u", "com.acme.FooTest", "alpha()");
        java.lang.reflect.Field uid = TestId.class.getDeclaredField("uniqueId");
        uid.setAccessible(true);
        uid.set(id, null);
        var method = TestSelector.class.getDeclaredMethod("methodNameOf", TestId.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, id));
    }

    @Test
    void displayMethodOfReturnsNullForNullDisplayName() throws Exception {
        TestId id = new TestId("u", "com.acme.FooTest", "alpha()");
        java.lang.reflect.Field display = TestId.class.getDeclaredField("displayName");
        display.setAccessible(true);
        display.set(id, null);
        var method = TestSelector.class.getDeclaredMethod("displayMethodOf", TestId.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, id));
    }

    @Test
    void matchesFactoryMethodUniqueId() {
        TestId factory = new TestId(
                "[engine:junit-jupiter]/[class:com.acme.FooTest]/[test-factory:factory()]",
                "com.acme.FooTest", "factory()");
        List<TestId> out = TestSelector.parse("FooTest#factory").filter(List.of(factory));
        assertEquals(1, out.size());
    }

    @Test
    void skipsBlankMethodTokensInPlusExpression() {
        TestId methodOnly = new TestId("x", "com.acme.FooTest", "alpha()");
        List<TestId> out = TestSelector.parse("FooTest#alpha++beta").filter(List.of(methodOnly, test("com.acme.FooTest", "beta")));
        assertEquals(2, out.size());
    }

    @Test
    void matchesViaMethodNameWhenDisplayNameAbsent() {
        TestId id = new TestId(
                "[engine:junit-jupiter]/[class:com.acme.FooTest]/[method:alpha()]",
                "com.acme.FooTest", null);
        List<TestId> out = TestSelector.parse("FooTest#alpha").filter(List.of(id));
        assertEquals(1, out.size());
    }
}
