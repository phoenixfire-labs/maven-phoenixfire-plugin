package io.phoenixfire.core.engine;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.report.ReportWriter;
import io.phoenixfire.api.spi.FailureClassifier;
import io.phoenixfire.api.spi.FailureContext;
import io.phoenixfire.api.spi.IsolationContext;
import io.phoenixfire.api.spi.IsolationStrategy;
import io.phoenixfire.api.spi.RetryDecision;
import io.phoenixfire.api.spi.RetryPolicy;
import io.phoenixfire.api.spi.WorkUnit;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.discovery.DiscoveryService;
import io.phoenixfire.core.ipc.IpcServer;
import io.phoenixfire.core.isolation.ConfigIsolationContext;
import io.phoenixfire.core.isolation.IsolationStrategyRegistry;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.report.JUnitXmlReportWriter;
import io.phoenixfire.core.report.NativeJsonReportWriter;
import io.phoenixfire.core.retry.RetryPolicies;
import io.phoenixfire.core.supervisor.AttemptOutcome;
import io.phoenixfire.core.supervisor.DefaultFailureClassifier;
import io.phoenixfire.core.supervisor.ForkExecutionResult;
import io.phoenixfire.core.supervisor.ForkLauncher;
import io.phoenixfire.core.supervisor.ForkSupervisor;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The orchestrator that guarantees complete, accountable test execution under fork failures.
 *
 * <p>Lifecycle: start IPC server, discover tests, seed the journal, then drive rounds of execution
 * grouped by isolation level - running the shared pool concurrently and escalated levels serially.
 * After each fork completes, outcomes are recorded and the retry policy decides escalation. Any test
 * that can never complete is forced to a terminal state so every discovered test is accounted for.
 */
public final class ExecutionEngine implements AutoCloseable {

    private final PhoenixfireConfiguration config;
    private final PhoenixfireLogger log;
    private final ClassLoader spiLoader;

    private final ExecutionJournal journal;
    private final IpcServer ipcServer;
    private final ForkSupervisor supervisor;
    private final DiscoveryService discoveryService;
    private final RetryPolicy retryPolicy;
    private final IsolationStrategyRegistry strategyRegistry;
    private final IsolationContext isolationContext;
    private final List<ReportWriter> reportWriters;

    private int port;

    public ExecutionEngine(PhoenixfireConfiguration config, PhoenixfireLogger log, ClassLoader spiLoader)
            throws IOException {
        this.config = config;
        this.log = log;
        this.spiLoader = spiLoader != null ? spiLoader : ExecutionEngine.class.getClassLoader();

        this.journal = new ExecutionJournal(log);
        if (config.journalEnabled() && config.journalFile() != null) {
            journal.enableJournalFile(config.journalFile().toPath());
        }

        this.ipcServer = new IpcServer(log);
        this.port = ipcServer.start();

        ForkLauncher launcher = new ForkLauncher(config, port);
        FailureClassifier classifier = resolveFailureClassifier();
        Path forkLogDir = config.reportsDirectory() != null
                ? config.reportsDirectory().toPath().resolve("forks") : null;
        this.supervisor = new ForkSupervisor(config, ipcServer, launcher, journal, classifier, log, forkLogDir);
        this.discoveryService = new DiscoveryService(supervisor, log);
        this.retryPolicy = RetryPolicies.resolve(config, log, this.spiLoader);
        this.strategyRegistry = IsolationStrategyRegistry.createDefault(log, this.spiLoader);
        this.isolationContext = new ConfigIsolationContext(config);
        this.reportWriters = resolveReportWriters();
    }

    public ExecutionSummary run() {
        List<TestId> discovered = discoveryService.discover();
        journal.seed(discovered);

        if (discovered.isEmpty()) {
            log.info("No tests discovered.");
            ReportModel model = journal.snapshot();
            writeReports(model);
            return new ExecutionSummary(model);
        }

        int maxIterations = (config.maxAttempts() + config.escalationLadder().size() + 2) * discovered.size() + 100;
        int iteration = 0;
        while (!journal.allTerminal() && iteration++ < maxIterations) {
            List<TestRecord> notRun = journal.testsInState(TestState.NOT_RUN);
            if (notRun.isEmpty()) {
                break;
            }
            IsolationLevel level = lowestLevel(notRun);
            List<TestId> testsAtLevel = new ArrayList<>();
            for (TestRecord r : notRun) {
                if (r.targetLevel() == level) {
                    testsAtLevel.add(r.testId());
                }
            }
            runLevel(level, testsAtLevel);
        }

        finalizeIncompleteTests();

        ReportModel model = journal.snapshot();
        writeReports(model);
        log.info(new ExecutionSummary(model).describe());
        return new ExecutionSummary(model);
    }

    private void runLevel(IsolationLevel level, List<TestId> tests) {
        IsolationStrategy strategy = strategyRegistry.forLevel(level);
        List<WorkUnit> units = strategy.plan(tests, isolationContext);
        if (units.isEmpty()) {
            return;
        }
        log.info("Running " + tests.size() + " test(s) at isolation level " + level
                + " across " + units.size() + " fork(s).");

        List<ForkExecutionResult> results = new ArrayList<>();
        if (level == IsolationLevel.SHARED_FORK_POOL && units.size() > 1) {
            results.addAll(runParallel(units));
        } else {
            for (WorkUnit unit : units) {
                results.add(supervisor.runExecution(unit));
            }
        }

        long maxBackoff = 0L;
        for (int i = 0; i < units.size(); i++) {
            maxBackoff = Math.max(maxBackoff, processResult(units.get(i), results.get(i)));
        }
        if (maxBackoff > 0L) {
            sleep(maxBackoff);
        }
    }

    private List<ForkExecutionResult> runParallel(List<WorkUnit> units) {
        int threads = Math.max(1, Math.min(config.forkCount(), units.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ForkExecutionResult>> futures = new ArrayList<>();
            for (WorkUnit unit : units) {
                Callable<ForkExecutionResult> task = () -> supervisor.runExecution(unit);
                futures.add(pool.submit(task));
            }
            List<ForkExecutionResult> results = new ArrayList<>();
            for (Future<ForkExecutionResult> f : futures) {
                try {
                    results.add(f.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for forks", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Fork execution failed", e.getCause());
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Record outcomes for a finished unit and decide retries. Returns the max backoff requested. */
    private long processResult(WorkUnit unit, ForkExecutionResult result) {
        long backoff = 0L;
        for (TestId testId : unit.tests()) {
            AttemptOutcome outcome = result.outcomes().get(testId);
            TestRecord record = journal.record(testId);
            int attemptNumber = record.attemptCount() + 1;
            long now = System.currentTimeMillis();

            TestState outcomeState;
            FailureMode failureMode;
            String message;
            String stack;
            long duration;
            if (outcome != null) {
                outcomeState = outcome.state();
                failureMode = outcomeState == TestState.FAILED ? FailureMode.ASSERTION_FAILURE : FailureMode.NONE;
                message = outcome.failureMessage();
                stack = outcome.stackTrace();
                duration = outcome.durationMillis();
            } else {
                // Test was assigned but the fork never reported a result: a victim of fork failure.
                outcomeState = TestState.CRASHED;
                failureMode = result.failureMode() == FailureMode.NONE ? FailureMode.UNKNOWN : result.failureMode();
                message = result.clean()
                        ? "Fork finished without reporting this test"
                        : "Fork failed (" + failureMode + ", exit=" + result.exitCode() + ") before completing this test";
                stack = result.diagnostic();
                duration = 0L;
            }

            ExecutionAttempt attempt = ExecutionAttempt.builder()
                    .attemptNumber(attemptNumber)
                    .isolationLevel(unit.isolationLevel())
                    .outcome(outcomeState)
                    .failureMode(failureMode)
                    .forkId(result.forkId())
                    .startMillis(now - duration)
                    .endMillis(now)
                    .throwableMessage(message)
                    .throwableStackTrace(stack)
                    .build();
            journal.recordAttempt(testId, attempt);

            if (!outcomeState.isSuccessful()) {
                backoff = Math.max(backoff, maybeScheduleRetry(testId, unit.isolationLevel(),
                        outcomeState, failureMode, result.exitCode()));
            }
        }
        return backoff;
    }

    private long maybeScheduleRetry(TestId testId, IsolationLevel currentLevel, TestState outcome,
                                    FailureMode failureMode, int exitCode) {
        TestRecord record = journal.record(testId);
        // Hard global cap guarantees termination regardless of the (possibly custom) policy.
        if (record.attemptCount() >= config.maxAttempts()) {
            log.debug("Test " + testId + " reached maxAttempts (" + config.maxAttempts() + "); no further retries.");
            return 0L;
        }
        FailureContext context = new FailureContext(outcome, failureMode, currentLevel,
                record.attemptCount(), exitCode);
        RetryDecision decision = retryPolicy.decide(record, context);
        if (decision.shouldRetry()) {
            journal.scheduleRetry(testId, decision.nextLevel());
            log.debug("Scheduling retry of " + testId + " at level " + decision.nextLevel());
            return decision.backoffMillis();
        }
        return 0L;
    }

    private void finalizeIncompleteTests() {
        for (TestRecord r : journal.testsInState(TestState.NOT_RUN)) {
            journal.forceTerminal(r.testId(), FailureMode.UNKNOWN,
                    "Retry budget exhausted or fork unreachable; never reached a terminal outcome");
        }
        for (TestRecord r : journal.testsInState(TestState.RUNNING)) {
            journal.forceTerminal(r.testId(), FailureMode.UNKNOWN,
                    "Fork died while this test was running and no recovery was possible");
        }
    }

    private void writeReports(ReportModel model) {
        Path dir = config.reportsDirectory() != null
                ? config.reportsDirectory().toPath() : Path.of("target", "phoenixfire-reports");
        for (ReportWriter writer : reportWriters) {
            try {
                writer.write(model, dir);
            } catch (IOException e) {
                log.error("Report writer " + writer.getClass().getName() + " failed: " + e.getMessage());
            }
        }
        log.info("Reports written to " + dir);
    }

    private IsolationLevel lowestLevel(List<TestRecord> notRun) {
        return notRun.stream()
                .map(TestRecord::targetLevel)
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElse(IsolationLevel.SHARED_FORK_POOL);
    }

    private FailureClassifier resolveFailureClassifier() {
        for (FailureClassifier c : ServiceLoader.load(FailureClassifier.class, spiLoader)) {
            log.info("Using custom failure classifier: " + c.getClass().getName());
            return c;
        }
        return new DefaultFailureClassifier();
    }

    private List<ReportWriter> resolveReportWriters() {
        List<ReportWriter> writers = new ArrayList<>();
        writers.add(new JUnitXmlReportWriter());
        writers.add(new NativeJsonReportWriter());
        for (ReportWriter w : ServiceLoader.load(ReportWriter.class, spiLoader)) {
            log.info("Registering custom report writer: " + w.getClass().getName());
            writers.add(w);
        }
        return writers;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        ipcServer.stop();
        journal.close();
    }
}
