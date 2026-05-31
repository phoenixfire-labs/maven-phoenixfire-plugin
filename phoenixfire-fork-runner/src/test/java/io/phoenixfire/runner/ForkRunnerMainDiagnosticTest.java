package io.phoenixfire.runner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkRunnerMainDiagnosticTest {

    @Test
    void printForkDiagnosticStopsBeforeJunitFrames() {
        Throwable error = new IllegalStateException("simulated fork failure");
        StackTraceElement[] trace = {
                new StackTraceElement("io.phoenixfire.runner.ForkRunnerMain", "main", "ForkRunnerMain.java", 10),
                new StackTraceElement("io.phoenixfire.runner.ForkRunnerTestSupport", "runMain", "ForkRunnerTestSupport.java", 69),
                new StackTraceElement("io.phoenixfire.runner.ForkRunnerMainTest", "missingPort", "ForkRunnerMainTest.java", 1),
                new StackTraceElement("jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", 0),
                new StackTraceElement("org.junit.jupiter.engine.execution.MethodInvocation", "proceed", "MethodInvocation.java", 60),
        };
        error.setStackTrace(trace);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ForkRunnerMain.printForkDiagnostic(error, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);

        assertTrue(out.contains("IllegalStateException: simulated fork failure"));
        assertTrue(out.contains("ForkRunnerMain.main"));
        assertFalse(out.contains("NativeMethodAccessorImpl"));
        assertFalse(out.contains("MethodInvocation.proceed"));
    }

    @Test
    void printForkDiagnosticIncludesCauseUntilNoise() {
        IllegalStateException root = new IllegalStateException("root cause");
        IllegalStateException error = new IllegalStateException("wrapper", root);
        StackTraceElement[] trace = {
                new StackTraceElement("io.phoenixfire.runner.ForkRunnerMain", "main", "ForkRunnerMain.java", 10),
                new StackTraceElement("org.junit.platform.engine.support.hierarchical.NodeTestTask", "execute",
                        "NodeTestTask.java", 100),
        };
        error.setStackTrace(trace);
        root.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("io.phoenixfire.runner.ForkIpcClient", "readMessage", "ForkIpcClient.java", 20),
                new StackTraceElement("org.junit.jupiter.api.Assert", "assertTrue", "Assert.java", 1),
        });

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ForkRunnerMain.printForkDiagnostic(error, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);

        assertTrue(out.contains("IllegalStateException: wrapper"));
        assertTrue(out.contains("Caused by: java.lang.IllegalStateException: root cause"));
        assertTrue(out.contains("ForkIpcClient.readMessage"));
        assertFalse(out.contains("NodeTestTask.execute"));
        assertFalse(out.contains("Assert.assertTrue"));
    }
}
