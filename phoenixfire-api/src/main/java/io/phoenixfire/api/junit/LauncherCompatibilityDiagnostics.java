package io.phoenixfire.api.junit;

import java.io.PrintStream;
import java.util.Optional;

/**
 * Detects JUnit Platform launcher/engine version skew on the test classpath and formats guidance
 * for users (instead of a raw {@link NoSuchMethodError} stack trace alone).
 */
public final class LauncherCompatibilityDiagnostics {

    /** Printed by the fork when discovery fails due to launcher skew; matched in fork log tails. */
    public static final String FORK_MARKER =
            "[phoenixfire-fork] JUnit Platform launcher version mismatch";

    private static final String JUNIT_PLATFORM = "org.junit.platform";
    private static final String JUNIT_JUPITER = "org.junit.jupiter";
    private static final String JUNIT_VINTAGE = "org.junit.vintage";

    private LauncherCompatibilityDiagnostics() {
    }

    public static boolean isLikelyLauncherIncompatibility(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (isLauncherLinkageFailure(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLikelyLauncherIncompatibility(String diagnosticText) {
        if (diagnosticText == null || diagnosticText.isBlank()) {
            return false;
        }
        if (diagnosticText.contains(FORK_MARKER)) {
            return true;
        }
        for (String line : diagnosticText.split("\\R")) {
            if (lineIndicatesLauncherSkew(line.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * User-facing remediation text for the Maven/controller log.
     */
    public static Optional<String> suggestRemediation(Throwable throwable) {
        if (!isLikelyLauncherIncompatibility(throwable)) {
            return Optional.empty();
        }
        return Optional.of(buildRemediationMessage(summarize(throwable)));
    }

    public static Optional<String> suggestRemediation(String diagnosticText) {
        if (!isLikelyLauncherIncompatibility(diagnosticText)) {
            return Optional.empty();
        }
        return Optional.of(buildRemediationMessage(extractSummaryLine(diagnosticText)));
    }

    /**
     * Writes a concise fork-side explanation to stderr (captured in the fork log tail).
     */
    public static void printForkGuidance(Throwable throwable, PrintStream err, String launcherImplementationVersion) {
        if (!isLikelyLauncherIncompatibility(throwable)) {
            return;
        }
        err.println(FORK_MARKER);
        err.println(buildRemediationMessage(summarize(throwable)));
        if (launcherImplementationVersion != null && !launcherImplementationVersion.isBlank()) {
            err.println("  junit-platform-launcher implementation version on this fork: "
                    + launcherImplementationVersion);
        }
    }

    private static String buildRemediationMessage(String technicalSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Test discovery failed because junit-platform-launcher does not match the JUnit ");
        sb.append("Platform engines on the test classpath (common when mixing JUnit 5.10 engines with ");
        sb.append("a JUnit 6 launcher, or when Surefire used to supply a launcher but Phoenixfire does not).");
        if (technicalSummary != null && !technicalSummary.isBlank()) {
            sb.append(System.lineSeparator()).append("  Cause: ").append(technicalSummary);
        }
        sb.append(System.lineSeparator()).append("  Fix: add an explicit test-scoped dependency on ");
        sb.append("junit-platform-launcher at the same version as your junit-jupiter / BOM ");
        sb.append("(see Phoenixfire docs/COMPATIBILITY.md).");
        sb.append(System.lineSeparator()).append("  Example (JUnit 5.10):");
        sb.append(System.lineSeparator()).append("    <dependency>");
        sb.append(System.lineSeparator()).append("      <groupId>org.junit.platform</groupId>");
        sb.append(System.lineSeparator()).append("      <artifactId>junit-platform-launcher</artifactId>");
        sb.append(System.lineSeparator()).append("      <version>1.10.2</version>");
        sb.append(System.lineSeparator()).append("      <scope>test</scope>");
        sb.append(System.lineSeparator()).append("    </dependency>");
        return sb.toString();
    }

    private static boolean isLauncherLinkageFailure(Throwable t) {
        if (!(t instanceof LinkageError) && !(t instanceof ReflectiveOperationException)) {
            return false;
        }
        String message = t.getMessage();
        if (message != null && mentionsJUnit(message.toLowerCase())) {
            return true;
        }
        for (StackTraceElement frame : t.getStackTrace()) {
            String className = frame.getClassName();
            if (className.startsWith(JUNIT_PLATFORM)
                    || className.startsWith(JUNIT_JUPITER)
                    || className.startsWith(JUNIT_VINTAGE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lineIndicatesLauncherSkew(String lower) {
        if (!mentionsJUnit(lower)) {
            return false;
        }
        if (lower.contains("nosuchmethoderror")
                || lower.contains("abstractmethoderror")
                || lower.contains("incompatibleclasschangeerror")
                || lower.contains("nosuchfielderror")
                || lower.contains("illegalaccesserror")
                || lower.contains("verifyerror")) {
            return true;
        }
        return (lower.contains("noclassdeffounderror") || lower.contains("classnotfoundexception"))
                && (lower.contains(JUNIT_PLATFORM) || lower.contains(JUNIT_JUPITER) || lower.contains(JUNIT_VINTAGE));
    }

    private static boolean mentionsJUnit(String lower) {
        return lower.contains("junit.platform")
                || lower.contains(JUNIT_PLATFORM)
                || lower.contains(JUNIT_JUPITER)
                || lower.contains(JUNIT_VINTAGE);
    }

    private static String summarize(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String name = root.getClass().getSimpleName();
        String message = root.getMessage();
        if (message != null && !message.isBlank()) {
            return name + ": " + message;
        }
        return name;
    }

    private static String extractSummaryLine(String diagnosticText) {
        for (String line : diagnosticText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Cause:")) {
                return trimmed.substring("Cause:".length()).trim();
            }
            if (trimmed.contains("NoSuchMethodError")
                    || trimmed.contains("AbstractMethodError")
                    || trimmed.contains("IncompatibleClassChangeError")) {
                return trimmed;
            }
        }
        return null;
    }
}
