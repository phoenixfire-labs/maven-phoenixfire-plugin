package io.phoenixfire.core.select;

import io.phoenixfire.api.model.TestId;

import java.util.regex.Pattern;

/**
 * Surefire-style glob and JUnit unique-id parsing used by {@link TestSelector}.
 *
 * <p>Kept as a small instance type so {@link TestSelector} can accept a collaborator in tests or
 * future wiring without static helpers on the selector itself.
 */
public final class SelectorMatching {

    public static final SelectorMatching DEFAULT = new SelectorMatching();

    public String globToRegex(String glob) {
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

    public Pattern compileGlob(String glob) {
        return Pattern.compile(globToRegex(glob));
    }

    public String simpleName(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return fqcn;
        }
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    public String stripClassSuffix(String token) {
        if (token.endsWith(".java")) {
            return token.substring(0, token.length() - ".java".length());
        }
        if (token.endsWith(".class")) {
            return token.substring(0, token.length() - ".class".length());
        }
        return token;
    }

    /** Extract the method name from a JUnit Platform unique id, e.g. {@code [method:bar()]} -> {@code bar}. */
    public String methodNameOf(TestId id) {
        if (id == null) {
            return null;
        }
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

    public String displayMethodOf(TestId id) {
        if (id == null) {
            return null;
        }
        String dn = id.displayName();
        if (dn == null) {
            return null;
        }
        int paren = dn.indexOf('(');
        return paren >= 0 ? dn.substring(0, paren) : dn;
    }
}
