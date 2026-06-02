package io.phoenixfire.api.junit;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherCompatibilityDiagnosticsTest {

    @Test
    void detectsNoSuchMethodOnPlatformClass() {
        NoSuchMethodError error = new NoSuchMethodError(
                "'void org.junit.platform.engine.TestDescriptor.getDisplayName()'");
        StackTraceElement[] trace = {
                new StackTraceElement("org.junit.platform.launcher.core.EngineDiscoveryOrchestrator",
                        "discover", "EngineDiscoveryOrchestrator.java", 1)
        };
        error.setStackTrace(trace);
        assertTrue(LauncherCompatibilityDiagnostics.isLikelyLauncherIncompatibility(error));
        assertTrue(LauncherCompatibilityDiagnostics.suggestRemediation(error).orElse("").contains("junit-platform-launcher"));
    }

    @Test
    void detectsFromForkLogTail() {
        String log = """
                [phoenixfire-fork] uncaught NoSuchMethodError: 'void org.junit.platform...
                java.lang.NoSuchMethodError: 'void org.junit.platform.engine.support.descriptor.ClassSource.<init>(...)
                    at org.junit.platform.launcher.core.LauncherFactory.create(LauncherFactory.java:1)
                """;
        assertTrue(LauncherCompatibilityDiagnostics.isLikelyLauncherIncompatibility(log));
        assertTrue(LauncherCompatibilityDiagnostics.suggestRemediation(log).orElse("").contains("COMPATIBILITY.md"));
    }

    @Test
    void detectsForkMarker() {
        assertTrue(LauncherCompatibilityDiagnostics.isLikelyLauncherIncompatibility(
                LauncherCompatibilityDiagnostics.FORK_MARKER + System.lineSeparator() + "details"));
    }

    @Test
    void ignoresUnrelatedErrors() {
        assertFalse(LauncherCompatibilityDiagnostics.isLikelyLauncherIncompatibility(
                new IllegalStateException("database down")));
        assertFalse(LauncherCompatibilityDiagnostics.isLikelyLauncherIncompatibility(
                "java.lang.NullPointerException at com.acme.FooTest"));
        assertFalse(LauncherCompatibilityDiagnostics.isLikelyLauncherIncompatibility(
                "Error: Could not find or load main class io.phoenixfire.runner.ForkRunnerMain"
                        + System.lineSeparator()
                        + "Caused by: java.lang.ClassNotFoundException: io.phoenixfire.runner.ForkRunnerMain"));
    }

    @Test
    void printForkGuidanceWritesMarker() {
        NoSuchMethodError error = new NoSuchMethodError("org.junit.platform.launcher.Launcher");
        StackTraceElement[] trace = {
                new StackTraceElement("org.junit.platform.launcher.core.LauncherFactory", "create",
                        "LauncherFactory.java", 1)
        };
        error.setStackTrace(trace);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        LauncherCompatibilityDiagnostics.printForkGuidance(error, new PrintStream(buf, true, StandardCharsets.UTF_8),
                "1.10.2");
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains(LauncherCompatibilityDiagnostics.FORK_MARKER));
        assertTrue(out.contains("1.10.2"));
        assertTrue(out.contains("junit-platform-launcher"));
    }
}
