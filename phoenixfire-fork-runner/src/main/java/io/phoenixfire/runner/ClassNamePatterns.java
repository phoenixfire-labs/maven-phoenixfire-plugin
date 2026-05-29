package io.phoenixfire.runner;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Surefire-style path globs (for example {@code **}{@code /*Test.java}) into JUnit Platform
 * class-name regex patterns matched against fully qualified class names.
 *
 * <p>This is intentionally a pragmatic subset rather than a complete glob engine: it covers the
 * common include/exclude conventions used with Surefire.
 */
final class ClassNamePatterns {

    private ClassNamePatterns() {
    }

    static List<String> toRegexes(List<String> globs) {
        List<String> result = new ArrayList<>();
        if (globs != null) {
            for (String glob : globs) {
                if (glob != null && !glob.isBlank()) {
                    result.add(toRegex(glob.trim()));
                }
            }
        }
        return result;
    }

    static String toRegex(String glob) {
        String p = glob;
        if (p.endsWith(".java")) {
            p = p.substring(0, p.length() - ".java".length());
        } else if (p.endsWith(".class")) {
            p = p.substring(0, p.length() - ".class".length());
        }

        StringBuilder re = new StringBuilder();
        int i = 0;
        while (i < p.length()) {
            if (p.startsWith("**/", i)) {
                re.append(".*");
                i += 3;
                continue;
            }
            if (p.startsWith("**", i)) {
                re.append(".*");
                i += 2;
                continue;
            }
            char c = p.charAt(i);
            switch (c) {
                case '*':
                    re.append("[^.]*");
                    break;
                case '?':
                    re.append('.');
                    break;
                case '/':
                case '.':
                    re.append("\\.");
                    break;
                case '$':
                    re.append("\\$");
                    break;
                default:
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        re.append(c);
                    } else {
                        re.append('\\').append(c);
                    }
            }
            i++;
        }
        return re.toString();
    }
}
