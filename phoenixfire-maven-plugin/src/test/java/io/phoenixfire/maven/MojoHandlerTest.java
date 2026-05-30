package io.phoenixfire.maven;

import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.core.engine.ExecutionSummary;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MojoHandlerTest {

    @Test
    void testMojoFailsOnHardFailures() throws Exception {
        PhoenixfireTestMojo mojo = new PhoenixfireTestMojo();
        setField(mojo, "reportsDir", new File("target/phoenixfire-reports"));
        setField(mojo, "failOnFlakyTests", false);
        setField(mojo, "testFailureIgnore", false);

        TestRecord failed = new TestRecord(new TestId("u", "C", "t"));
        failed.internalSetState(TestState.FAILED);
        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(failed), 0, 1));

        assertThrows(MojoFailureException.class, () -> invokeHandleResult(mojo, summary));
    }

    @Test
    void verifyMojoSkipsWhenFlagged() throws Exception {
        PhoenixfireVerifyMojo mojo = new PhoenixfireVerifyMojo();
        setField(mojo, "skip", true);
        assertDoesNotThrow(mojo::execute);
    }

    private static void invokeHandleResult(PhoenixfireTestMojo mojo, ExecutionSummary summary) throws Exception {
        var m = PhoenixfireTestMojo.class.getDeclaredMethod("handleResult", ExecutionSummary.class);
        m.setAccessible(true);
        try {
            m.invoke(mojo, summary);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
