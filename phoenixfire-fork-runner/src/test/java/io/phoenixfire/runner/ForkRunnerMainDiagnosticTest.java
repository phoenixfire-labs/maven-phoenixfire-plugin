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
}
