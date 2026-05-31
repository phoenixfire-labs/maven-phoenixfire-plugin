package io.phoenixfire.core.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a stable, short fingerprint of a failure from its stack trace or message, so that "the
 * same crash" can be clustered across attempts, tests, and runs by a downstream tool.
 *
 * <p>The text is normalised before hashing - hex addresses and numeric runs (line numbers, object
 * ids, timestamps) are collapsed - so that incidental variation does not produce different
 * signatures for what is really the same failure.
 */
public final class FailureSignatures {

    private FailureSignatures() {
    }

    /**
     * @param stackTrace the captured stack trace (preferred), may be {@code null}
     * @param message    a fallback failure message, may be {@code null}
     * @return a 12-char hex signature, or {@code null} if there is no failure text
     */
    public static String of(String stackTrace, String message) {
        String source = stackTrace != null && !stackTrace.isBlank() ? stackTrace : message;
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = normalize(source);
        return hash(normalized);
    }

    private static String normalize(String text) {
        String t = text;
        t = t.replaceAll("0x[0-9a-fA-F]+", "0xADDR");
        t = t.replaceAll("@[0-9a-fA-F]{4,}", "@HASH");
        t = t.replaceAll("\\d+", "N");
        t = t.replaceAll("\\s+", " ");
        return t.trim();
    }

    private static String hash(String normalized) {
        return hash(normalized, "SHA-256");
    }

    static String hash(String normalized, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }
}
