package io.phoenixfire.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenises a Surefire-style {@code argLine} into individual JVM arguments, honouring single and
 * double quotes so that an argument containing spaces (for example {@code -Dmsg="a b"}) survives as
 * one token. Quotes are removed from the resulting tokens, matching shell/Surefire behaviour.
 */
public final class ArgLine {

    private ArgLine() {
    }

    public static List<String> tokenize(String argLine) {
        List<String> tokens = new ArrayList<>();
        if (argLine == null || argLine.isBlank()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;
        for (int i = 0; i < argLine.length(); i++) {
            char c = argLine.charAt(i);
            if (quote != 0) {
                inToken = true;
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inToken = true;
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                inToken = true;
                current.append(c);
            }
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
