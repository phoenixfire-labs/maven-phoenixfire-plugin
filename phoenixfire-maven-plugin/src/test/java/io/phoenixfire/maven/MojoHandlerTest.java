package io.phoenixfire.maven;



import io.phoenixfire.api.model.ExecutionAttempt;

import io.phoenixfire.api.model.TestId;

import io.phoenixfire.api.model.TestRecord;

import io.phoenixfire.api.model.TestState;

import io.phoenixfire.api.report.ReportModel;

import io.phoenixfire.core.engine.ExecutionSummary;

import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.plugin.logging.SystemStreamLog;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;



import java.io.File;

import java.nio.file.Files;

import java.util.List;

import java.util.Properties;



import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertTrue;



class MojoHandlerTest {



    @TempDir

    File tempDir;



    @Test

    void testMojoFailsOnHardFailures() throws Exception {

        PhoenixfireTestMojo mojo = new PhoenixfireTestMojo();

        MojoTestReflection.setField(mojo, "reportsDir", new File("target/phoenixfire-reports"));

        MojoTestReflection.setField(mojo, "failOnFlakyTests", false);

        MojoTestReflection.setField(mojo, "testFailureIgnore", false);



        TestRecord failed = new TestRecord(new TestId("u", "C", "t"));

        failed.internalSetState(TestState.FAILED);

        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(failed), 0, 1));



        assertThrows(MojoFailureException.class, () -> MojoTestReflection.invokeHandleResult(mojo, summary));

    }



    @Test

    void testMojoSucceedsWhenNoFailures() throws Exception {

        PhoenixfireTestMojo mojo = testMojo();

        TestRecord passed = new TestRecord(new TestId("u", "C", "ok"));

        passed.internalSetState(TestState.PASSED);

        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(passed), 0, 1));

        assertDoesNotThrow(() -> MojoTestReflection.invokeHandleResult(mojo, summary));

    }



    @Test

    void testMojoIgnoresFailuresWhenConfigured() throws Exception {

        PhoenixfireTestMojo mojo = testMojo();

        MojoTestReflection.setField(mojo, "testFailureIgnore", true);

        TestRecord failed = new TestRecord(new TestId("u", "C", "t"));

        failed.internalSetState(TestState.FAILED);

        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(failed), 0, 1));

        assertDoesNotThrow(() -> MojoTestReflection.invokeHandleResult(mojo, summary));

    }



    @Test

    void testMojoWarnsOnFlakyWithoutFailingByDefault() throws Exception {

        PhoenixfireTestMojo mojo = testMojo();

        MojoTestReflection.setField(mojo, "failOnFlakyTests", false);

        ExecutionSummary summary = flakySummary();

        assertDoesNotThrow(() -> MojoTestReflection.invokeHandleResult(mojo, summary));

    }



    @Test

    void testMojoFailsOnFlakyWhenFailOnFlakyTests() throws Exception {

        PhoenixfireTestMojo mojo = testMojo();

        MojoTestReflection.setField(mojo, "failOnFlakyTests", true);

        ExecutionSummary summary = flakySummary();

        assertThrows(MojoFailureException.class, () -> MojoTestReflection.invokeHandleResult(mojo, summary));

    }



    @Test

    void testMojoProtectedMethods() throws Exception {

        PhoenixfireTestMojo mojo = testMojo();

        assertFalse((Boolean) MojoTestReflection.invokeProtected(mojo, "isSkipped"));

        MojoTestReflection.setField(mojo, "skip", true);

        assertTrue((Boolean) MojoTestReflection.invokeProtected(mojo, "isSkipped"));

        MojoTestReflection.setField(mojo, "skip", false);

        MojoTestReflection.setField(mojo, "skipTests", true);

        assertTrue((Boolean) MojoTestReflection.invokeProtected(mojo, "isSkipped"));



        @SuppressWarnings("unchecked")

        List<String> defaults = (List<String>) MojoTestReflection.invokeProtected(mojo, "defaultIncludes");

        assertTrue(defaults.stream().anyMatch(p -> p.contains("Test")));



        MojoTestReflection.setField(mojo, "test", "FooTest#bar");

        assertEquals("FooTest#bar", MojoTestReflection.invokeProtected(mojo, "testFilterExpression"));



        File reports = (File) MojoTestReflection.invokeProtected(mojo, "reportsDirectory");

        assertEquals(new File("target/phoenixfire-reports"), reports);

    }



    @Test

    void integrationTestMojoWritesSummary() throws Exception {

        PhoenixfireIntegrationTestMojo mojo = new PhoenixfireIntegrationTestMojo();

        mojo.setLog(new SystemStreamLog());

        File reports = new File(tempDir, "it-reports");

        MojoTestReflection.setField(mojo, "reportsDir", reports);



        TestRecord passed = new TestRecord(new TestId("u", "C", "t"));

        passed.internalSetState(TestState.PASSED);

        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(passed), 0, 1));

        assertDoesNotThrow(() -> MojoTestReflection.invokeHandleResult(mojo, summary));

        assertTrue(new File(reports, SummaryFile.FILE_NAME).isFile());

    }



    @Test

    void integrationTestMojoIsSkippedFlags() throws Exception {

        PhoenixfireIntegrationTestMojo mojo = new PhoenixfireIntegrationTestMojo();

        assertFalse((Boolean) MojoTestReflection.invokeProtected(mojo, "isSkipped"));

        MojoTestReflection.setField(mojo, "skipITs", true);

        assertTrue((Boolean) MojoTestReflection.invokeProtected(mojo, "isSkipped"));

        MojoTestReflection.setField(mojo, "skipITs", false);
        MojoTestReflection.setField(mojo, "skipTests", true);
        assertTrue((Boolean) MojoTestReflection.invokeProtected(mojo, "isSkipped"));

    }



    @Test
    void integrationTestMojoExecuteSkipsWhenFlagged() throws Exception {
        PhoenixfireIntegrationTestMojo mojo = new PhoenixfireIntegrationTestMojo();
        mojo.setLog(new SystemStreamLog());
        MojoTestReflection.setField(mojo, "skipITs", true);
        assertDoesNotThrow(mojo::execute);
    }

    @Test
    void integrationTestMojoFailsWhenSummaryCannotBeWritten() throws Exception {
        PhoenixfireIntegrationTestMojo mojo = new PhoenixfireIntegrationTestMojo();
        mojo.setLog(new SystemStreamLog());
        File reportsFile = new File(tempDir, "not-a-directory");
        Files.writeString(reportsFile.toPath(), "blocker");
        MojoTestReflection.setField(mojo, "reportsDir", reportsFile);

        TestRecord passed = new TestRecord(new TestId("u", "C", "t"));
        passed.internalSetState(TestState.PASSED);
        ExecutionSummary summary = new ExecutionSummary(new ReportModel(List.of(passed), 0, 1));
        assertThrows(MojoFailureException.class, () -> MojoTestReflection.invokeHandleResult(mojo, summary));
    }

    @Test

    void integrationTestMojoProtectedDefaults() throws Exception {

        PhoenixfireIntegrationTestMojo mojo = new PhoenixfireIntegrationTestMojo();

        @SuppressWarnings("unchecked")

        List<String> includes = (List<String>) MojoTestReflection.invokeProtected(mojo, "defaultIncludes");

        assertTrue(includes.stream().anyMatch(p -> p.contains("IT")));

        MojoTestReflection.setField(mojo, "itTest", "FooIT");

        assertEquals("FooIT", MojoTestReflection.invokeProtected(mojo, "testFilterExpression"));

        File reports = new File(tempDir, "it-reports");
        MojoTestReflection.setField(mojo, "reportsDir", reports);
        assertEquals(reports, MojoTestReflection.invokeProtected(mojo, "reportsDirectory"));

    }



    @Test

    void verifyMojoSkipsWhenFlagged() throws Exception {

        PhoenixfireVerifyMojo mojo = new PhoenixfireVerifyMojo();

        mojo.setLog(new SystemStreamLog());

        MojoTestReflection.setField(mojo, "skip", true);

        assertDoesNotThrow(mojo::execute);

    }



    @Test

    void verifyMojoSkipsWhenSkipTestsOrSkipITs() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        MojoTestReflection.setField(mojo, "skipTests", true);

        assertDoesNotThrow(mojo::execute);



        PhoenixfireVerifyMojo mojo2 = verifyMojo();

        MojoTestReflection.setField(mojo2, "skipITs", true);

        assertDoesNotThrow(mojo2::execute);

    }



    @Test

    void verifyMojoNoSummaryIsNoOp() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        assertDoesNotThrow(mojo::execute);

    }



    @Test

    void verifyMojoFailsOnIntegrationFailures() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        writeSummary(1, 1, 0, 0);

        assertThrows(MojoFailureException.class, mojo::execute);

    }



    @Test

    void verifyMojoIgnoresFailuresWhenConfigured() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        MojoTestReflection.setField(mojo, "testFailureIgnore", true);

        writeSummary(1, 1, 0, 0);

        assertDoesNotThrow(mojo::execute);

    }



    @Test

    void verifyMojoWarnsOnFlakyWithoutFailingByDefault() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        writeSummary(1, 0, 0, 1);

        assertDoesNotThrow(mojo::execute);

    }



    @Test

    void verifyMojoFailsOnFlakyWhenFailOnFlakyTests() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        MojoTestReflection.setField(mojo, "failOnFlakyTests", true);

        writeSummary(1, 0, 0, 1);

        assertThrows(MojoFailureException.class, mojo::execute);

    }



    @Test

    void verifyMojoIOExceptionOnRead() throws Exception {

        PhoenixfireVerifyMojo mojo = verifyMojo();

        Files.createDirectory(tempDir.toPath().resolve(SummaryFile.FILE_NAME));

        assertThrows(MojoExecutionException.class, mojo::execute);

    }



    private PhoenixfireTestMojo testMojo() throws Exception {

        PhoenixfireTestMojo mojo = new PhoenixfireTestMojo();

        mojo.setLog(new SystemStreamLog());

        MojoTestReflection.setField(mojo, "reportsDir", new File("target/phoenixfire-reports"));

        MojoTestReflection.setField(mojo, "failOnFlakyTests", false);

        MojoTestReflection.setField(mojo, "testFailureIgnore", false);

        return mojo;

    }



    private PhoenixfireVerifyMojo verifyMojo() throws Exception {

        PhoenixfireVerifyMojo mojo = new PhoenixfireVerifyMojo();

        mojo.setLog(new SystemStreamLog());

        MojoTestReflection.setField(mojo, "reportsDir", tempDir);

        return mojo;

    }



    private void writeSummary(long total, long failed, long crashed, long flaky) throws Exception {

        Properties props = new Properties();

        props.setProperty("total", Long.toString(total));

        props.setProperty("failed", Long.toString(failed));

        props.setProperty("crashed", Long.toString(crashed));

        props.setProperty("flaky", Long.toString(flaky));

        try (var out = Files.newOutputStream(tempDir.toPath().resolve(SummaryFile.FILE_NAME))) {

            props.store(out, "test");

        }

    }



    private static ExecutionSummary flakySummary() {

        TestRecord flaky = new TestRecord(new TestId("u", "C", "flaky"));

        flaky.internalAddAttempt(ExecutionAttempt.builder().attemptNumber(1).outcome(TestState.FAILED).build());

        flaky.internalAddAttempt(ExecutionAttempt.builder().attemptNumber(2).outcome(TestState.PASSED).build());

        flaky.internalSetState(TestState.PASSED);

        return new ExecutionSummary(new ReportModel(List.of(flaky), 0, 1));

    }

}

