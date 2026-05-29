package io.phoenixfire.core.select;

import io.phoenixfire.api.model.TestId;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Surefire-style {@code -Dtest} / Failsafe {@code -Dit.test} selection expression.
 *
 * <p>Grammar (comma-separated entries):
 * <ul>
 *   <li>{@code FooTest} - a class by simple or fully-qualified name (wildcards {@code *} and {@code ?}).</li>
 *   <li>{@code FooTest#bar} - a single method; {@code FooTest#bar+baz} or {@code FooTest#test*} for several.</li>
 *   <li>{@code !FooTest} - a negation (exclude), with the same class/method grammar.</li>
 * </ul>
 *
 * <p>Selection is applied as a precise post-discovery filter on the leaf {@link TestId}s (so method
 * selection works), while {@link #discoveryIncludeGlobs()} lets the Maven layer widen discovery so a
 * named class outside the default {@code *Test}/{@code *IT} globs is still found - matching Surefire's
 * "{@code -Dtest} overrides includes" behaviour.
 */
public final class TestSelector {

    private final List<Entry> includes;
    private final List<Entry> excludes;

    private TestSelector(List<Entry> includes, List<Entry> excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public static TestSelector parse(String expression) {
        List<Entry> includes = new ArrayList<>();
        List<Entry> excludes = new ArrayList<>();
        if (expression != null && !expression.isBlank()) {
            for (String raw : expression.split(",")) {
                String token = raw.trim();
                boolean negate = token.startsWith("!");
                if (negate) {
                    token = token.substring(1).trim();
                }
                if (token.isEmpty()) {
                    continue;
                }
                (negate ? excludes : includes).add(Entry.of(token));
            }
        }
        return new TestSelector(includes, excludes);
    }

    public boolean isEmpty() {
        return includes.isEmpty() && excludes.isEmpty();
    }

    /** Class-level globs for the inclusion entries, so discovery is permissive enough to find them. */
    public List<String> discoveryIncludeGlobs() {
        List<String> globs = new ArrayList<>();
        for (Entry e : includes) {
            globs.add("**/" + e.classGlob);
        }
        return globs;
    }

    /** Keep only tests matching the inclusion entries (if any) and not matching any exclusion. */
    public List<TestId> filter(List<TestId> tests) {
        if (isEmpty()) {
            return tests;
        }
        List<TestId> result = new ArrayList<>();
        for (TestId id : tests) {
            boolean included = includes.isEmpty() || matchesAny(includes, id);
            if (included && !matchesAny(excludes, id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static boolean matchesAny(List<Entry> entries, TestId id) {
        for (Entry e : entries) {
            if (e.matches(id)) {
                return true;
            }
        }
        return false;
    }

    /** A single class[#method[+method...]] term. */
    private static final class Entry {
        final String classGlob;
        final Pattern classPattern;
        final List<Pattern> methodPatterns;

        private Entry(String classGlob, Pattern classPattern, List<Pattern> methodPatterns) {
            this.classGlob = classGlob;
            this.classPattern = classPattern;
            this.methodPatterns = methodPatterns;
        }

        static Entry of(String token) {
            String classPart = token;
            String methodPart = null;
            int hash = token.indexOf('#');
            if (hash >= 0) {
                classPart = token.substring(0, hash).trim();
                methodPart = token.substring(hash + 1).trim();
            }
            classPart = stripSuffix(classPart).replace('/', '.');
            if (classPart.isEmpty()) {
                classPart = "*";
            }
            List<Pattern> methodPatterns = new ArrayList<>();
            if (methodPart != null && !methodPart.isEmpty()) {
                for (String m : methodPart.split("\\+")) {
                    String trimmed = m.trim();
                    if (!trimmed.isEmpty()) {
                        methodPatterns.add(Pattern.compile(globToRegex(trimmed)));
                    }
                }
            }
            return new Entry(classPart, Pattern.compile(globToRegex(classPart)), methodPatterns);
        }

        boolean matches(TestId id) {
            if (!classMatches(id)) {
                return false;
            }
            if (methodPatterns.isEmpty()) {
                return true;
            }
            String method = methodNameOf(id);
            String display = displayMethodOf(id);
            for (Pattern p : methodPatterns) {
                if ((method != null && p.matcher(method).matches())
                        || (display != null && p.matcher(display).matches())) {
                    return true;
                }
            }
            return false;
        }

        private boolean classMatches(TestId id) {
            String fqcn = id.className();
            if (fqcn != null && !fqcn.isEmpty() && classPattern.matcher(fqcn).matches()) {
                return true;
            }
            String simple = simpleName(fqcn);
            return simple != null && classPattern.matcher(simple).matches();
        }
    }

    private static String simpleName(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return fqcn;
        }
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    private static String stripSuffix(String s) {
        if (s.endsWith(".java")) {
            return s.substring(0, s.length() - ".java".length());
        }
        if (s.endsWith(".class")) {
            return s.substring(0, s.length() - ".class".length());
        }
        return s;
    }

    /** Extract the method name from a JUnit Platform unique id, e.g. {@code [method:bar()]} -> {@code bar}. */
    static String methodNameOf(TestId id) {
        String uid = id.uniqueId();
        if (uid == null) {
            return null;
        }
        String[] keys = {"[method:", "[test-template:", "[test-factory:", "[test-template-invocation:"};
        int best = -1;
        int keyLen = 0;
        for (String k : keys) {
            int idx = uid.lastIndexOf(k);
            if (idx > best) {
                best = idx;
                keyLen = k.length();
            }
        }
        if (best < 0) {
            return null;
        }
        int start = best + keyLen;
        int end = uid.indexOf(']', start);
        String segment = end < 0 ? uid.substring(start) : uid.substring(start, end);
        int paren = segment.indexOf('(');
        return paren >= 0 ? segment.substring(0, paren) : segment;
    }

    private static String displayMethodOf(TestId id) {
        String dn = id.displayName();
        if (dn == null) {
            return null;
        }
        int paren = dn.indexOf('(');
        return paren >= 0 ? dn.substring(0, paren) : dn;
    }

    static String globToRegex(String glob) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    re.append(".*");
                    break;
                case '?':
                    re.append('.');
                    break;
                default:
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        re.append(c);
                    } else {
                        re.append('\\').append(c);
                    }
            }
        }
        return re.toString();
    }
}
