package io.phoenixfire.api.model;

import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.run.RunEnvelope;
import io.phoenixfire.api.run.RunMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiModelTest {

    @Test
    void testIdEqualsAndHashCode() {
        TestId id = new TestId("uid", "Cls", "disp");
        assertEquals("uid", id.uniqueId());
        assertEquals("Cls", id.className());
        assertEquals("disp", id.displayName());
        assertEquals("uid".hashCode(), id.hashCode());
        assertTrue(id.equals(id));
        assertFalse(id.equals("not-a-test-id"));
        assertFalse(id.equals(new TestId("other", "Cls", "disp")));
        assertEquals(id, new TestId("uid", "other", "other"));
    }

    @Test
    void executionAttemptExposesStartMillis() {
        ExecutionAttempt a = ExecutionAttempt.builder()
                .attemptNumber(1)
                .startMillis(42)
                .endMillis(50)
                .outcome(TestState.PASSED)
                .build();
        assertEquals(1, a.attemptNumber());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, a.isolationLevel());
        assertEquals(TestState.PASSED, a.outcome());
        assertEquals(FailureMode.NONE, a.failureMode());
        assertEquals(0, a.exitCode());
        assertEquals(42, a.startMillis());
        assertEquals(50, a.endMillis());
        assertEquals(8L, a.durationMillis());
        assertNull(a.forkId());
        assertNull(a.throwableMessage());
        assertNull(a.throwableStackTrace());
    }

    @Test
    void executionAttemptDurationZeroWhenEndBeforeStart() {
        ExecutionAttempt a = ExecutionAttempt.builder()
                .startMillis(100)
                .endMillis(50)
                .outcome(TestState.PASSED)
                .build();
        assertEquals(0L, a.durationMillis());
    }

    @Test
    void testIdHandlesNullDisplayAndClassName() {
        TestId id = new TestId("uid", null, null);
        assertEquals("", id.className());
        assertEquals("uid", id.displayName());
        assertEquals("uid", id.toString());

        TestId same = new TestId("uid", "Cls", "disp");
        assertEquals(id, same);
        assertEquals(0, id.compareTo(same));
        assertTrue(id.compareTo(new TestId("zzz", "C", "d")) < 0);
    }

    @Test
    void testStateTerminalAndSuccessful() {
        assertFalse(TestState.NOT_RUN.isTerminal());
        assertTrue(TestState.PASSED.isTerminal());
        assertTrue(TestState.PASSED.isSuccessful());
        assertTrue(TestState.SKIPPED.isSuccessful());
        assertFalse(TestState.FAILED.isSuccessful());
    }

    @Test
    void failureModeInfrastructure() {
        assertFalse(FailureMode.NONE.isInfrastructureFailure());
        assertFalse(FailureMode.ASSERTION_FAILURE.isInfrastructureFailure());
        assertTrue(FailureMode.SIGKILL.isInfrastructureFailure());
    }

    @Test
    void isolationLevelNextAndMaximum() {
        assertEquals(IsolationLevel.FRESH_FORK, IsolationLevel.SHARED_FORK_POOL.next());
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, IsolationLevel.ONE_FORK_PER_CLASS.next());
        assertTrue(IsolationLevel.ONE_FORK_PER_CLASS.isMaximum());
        assertFalse(IsolationLevel.SHARED_FORK_POOL.isMaximum());
    }

    @Test
    void executionAttemptFullBuilder() {
        ExecutionAttempt a = ExecutionAttempt.builder()
                .attemptNumber(2)
                .isolationLevel(IsolationLevel.FRESH_FORK)
                .outcome(TestState.FAILED)
                .failureMode(FailureMode.ASSERTION_FAILURE)
                .forkId("fork-1")
                .exitCode(1)
                .startMillis(100)
                .endMillis(250)
                .throwableMessage("msg")
                .throwableStackTrace("stack")
                .build();
        assertEquals(FailureMode.ASSERTION_FAILURE, a.failureMode());
        assertEquals(150L, a.durationMillis());
        assertEquals("fork-1", a.forkId());
        assertEquals("msg", a.throwableMessage());
    }

    @Test
    void testRecordRecoveryForkReuseAndPackageMutators() {
        TestId id = new TestId("u1", "Cls", "m");
        TestRecord record = new TestRecord(id);
        record.setTargetLevel(IsolationLevel.ONE_FORK_PER_CLASS);
        assertEquals(IsolationLevel.ONE_FORK_PER_CLASS, record.targetLevel());

        record.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.FAILED).failureMode(FailureMode.ASSERTION_FAILURE).build());
        record.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(2).isolationLevel(IsolationLevel.FRESH_FORK)
                .outcome(TestState.PASSED).build());
        record.internalSetState(TestState.PASSED);
        record.internalSetLastFailureMode(FailureMode.ASSERTION_FAILURE);

        assertTrue(record.recovered());
        assertFalse(record.everCrashed());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, record.firstFailLevel());
        assertEquals(IsolationLevel.FRESH_FORK, record.recoveryLevel());
        assertTrue(record.forkReuseSensitive());
        assertEquals(FailureMode.ASSERTION_FAILURE, record.lastFailureMode());
        assertEquals(2, record.attemptCount());
        assertEquals(2, record.attempts().size());

        TestRecord crashed = new TestRecord(new TestId("u2", "C", "d"));
        crashed.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).outcome(TestState.CRASHED).build());
        assertTrue(crashed.everCrashed());
        assertFalse(crashed.recovered());
        assertNull(crashed.recoveryLevel());
        assertEquals(IsolationLevel.SHARED_FORK_POOL, crashed.firstFailLevel());

        TestRecord notReuseSensitive = new TestRecord(new TestId("u3", "C", "d"));
        notReuseSensitive.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.FRESH_FORK)
                .outcome(TestState.FAILED).build());
        notReuseSensitive.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(2).isolationLevel(IsolationLevel.FRESH_FORK)
                .outcome(TestState.PASSED).build());
        notReuseSensitive.internalSetState(TestState.PASSED);
        assertFalse(notReuseSensitive.forkReuseSensitive());

        TestRecord viaPackage = new TestRecord(new TestId("u4", "C", "d"));
        viaPackage.setState(TestState.RUNNING);
        viaPackage.addAttempt(ExecutionAttempt.builder().attemptNumber(1).outcome(TestState.PASSED).build());
        viaPackage.setLastFailureMode(FailureMode.NONE);
        assertEquals(TestState.RUNNING, viaPackage.state());
        assertNull(new TestRecord(new TestId("u5", "C", "d")).lastAttempt());

        TestRecord notRecovered = new TestRecord(new TestId("u6", "C", "d"));
        notRecovered.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).outcome(TestState.FAILED).build());
        notRecovered.internalSetState(TestState.FAILED);
        assertFalse(notRecovered.recovered());

        TestRecord onlyPasses = new TestRecord(new TestId("u7", "C", "d"));
        onlyPasses.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).outcome(TestState.PASSED).build());
        onlyPasses.internalSetState(TestState.PASSED);
        assertFalse(onlyPasses.recovered());
        assertNull(onlyPasses.firstFailLevel());
        assertFalse(onlyPasses.everCrashed());

        TestRecord sharedRecovery = new TestRecord(new TestId("u8", "C", "d"));
        sharedRecovery.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(1).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.FAILED).build());
        sharedRecovery.internalAddAttempt(ExecutionAttempt.builder()
                .attemptNumber(2).isolationLevel(IsolationLevel.SHARED_FORK_POOL)
                .outcome(TestState.PASSED).build());
        sharedRecovery.internalSetState(TestState.PASSED);
        assertTrue(sharedRecovery.recovered());
        assertFalse(sharedRecovery.forkReuseSensitive());
    }

    @Test
    void runMetadataBuilderAndGetters() {
        RunMetadata meta = RunMetadata.builder()
                .gitSha("abc")
                .gitBranch("main")
                .gitDirty(true)
                .ciProvider("github")
                .ciBuildId("42")
                .ciBuildUrl("https://ci.example/run/42")
                .projectGroupId("g")
                .projectArtifactId("a")
                .projectVersion("1.0")
                .labels(Map.of("team", "core"))
                .build();
        assertEquals("abc", meta.gitSha());
        assertEquals("main", meta.gitBranch());
        assertTrue(meta.gitDirty());
        assertEquals("github", meta.ciProvider());
        assertEquals("42", meta.ciBuildId());
        assertEquals("https://ci.example/run/42", meta.ciBuildUrl());
        assertEquals("g", meta.projectGroupId());
        assertEquals("a", meta.projectArtifactId());
        assertEquals("1.0", meta.projectVersion());
        assertEquals("core", meta.labels().get("team"));
        assertNull(RunMetadata.builder().gitSha("  ").build().gitSha());
        assertTrue(RunMetadata.empty().labels().isEmpty());
        assertTrue(RunMetadata.builder().labels(null).build().labels().isEmpty());
    }

    @Test
    void runEnvelopeBuilderAndGetters() {
        RunMetadata meta = RunMetadata.builder().gitSha("sha").build();
        RunEnvelope envelope = RunEnvelope.builder()
                .runId("r1")
                .host("host")
                .osName("Linux")
                .osArch("amd64")
                .jvm("17")
                .maxAttempts(3)
                .escalationLadder(List.of("SHARED_FORK_POOL", "FRESH_FORK"))
                .forkCount(2)
                .shardIndex(1)
                .shardCount(4)
                .metadata(meta)
                .build();
        assertEquals("r1", envelope.runId());
        assertEquals("host", envelope.host());
        assertEquals("Linux", envelope.osName());
        assertEquals("amd64", envelope.osArch());
        assertEquals("17", envelope.jvm());
        assertEquals(3, envelope.maxAttempts());
        assertEquals(2, envelope.escalationLadder().size());
        assertEquals(2, envelope.forkCount());
        assertEquals(1, envelope.shardIndex());
        assertEquals(4, envelope.shardCount());
        assertEquals("sha", envelope.metadata().gitSha());

        assertTrue(RunEnvelope.builder().escalationLadder(null).build().escalationLadder().isEmpty());

        RunEnvelope defaults = RunEnvelope.builder().build();
        assertTrue(defaults.escalationLadder().isEmpty());
        assertTrue(defaults.metadata().labels().isEmpty());
        assertEquals(0, defaults.shardIndex());
        assertEquals(0, defaults.shardCount());
    }

    @Test
    void reportModelConstructorsAndAggregates() {
        RunEnvelope envelope = RunEnvelope.builder().runId("r1").build();
        TestRecord passed = new TestRecord(new TestId("u1", "C", "ok"));
        passed.internalSetState(TestState.PASSED);
        TestRecord flaky = new TestRecord(new TestId("u2", "C", "flaky"));
        flaky.internalAddAttempt(ExecutionAttempt.builder().attemptNumber(1).outcome(TestState.FAILED).build());
        flaky.internalAddAttempt(ExecutionAttempt.builder().attemptNumber(2).outcome(TestState.PASSED).build());
        flaky.internalSetState(TestState.PASSED);
        TestRecord failed = new TestRecord(new TestId("u3", "C", "bad"));
        failed.internalSetState(TestState.FAILED);

        ReportModel threeArg = new ReportModel(List.of(passed), 100, 50);
        assertEquals(0L, threeArg.durationMillis());
        assertNull(threeArg.envelope());

        ReportModel model = new ReportModel(List.of(passed, flaky, failed), 0, 10, envelope);
        assertEquals(10, model.durationMillis());
        assertEquals(3, model.total());
        assertEquals(2, model.count(TestState.PASSED));
        assertEquals(1, model.count(TestState.FAILED));
        assertEquals(1, model.flakyCount());
        assertTrue(model.hasFailures());
        assertEquals(envelope, model.envelope());
        assertEquals(0, model.startMillis());
        assertEquals(10, model.endMillis());
    }
}
