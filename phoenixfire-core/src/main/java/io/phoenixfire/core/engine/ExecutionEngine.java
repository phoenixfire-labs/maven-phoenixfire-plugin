package io.phoenixfire.core.engine;

import io.phoenixfire.api.model.ExecutionAttempt;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.IsolationLevel;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestRecord;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.report.ReportModel;
import io.phoenixfire.api.report.ReportWriter;
import io.phoenixfire.api.run.RunEnvelope;
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
import io.phoenixfire.core.report.JsonLinesReportWriter;
import io.phoenixfire.core.report.NativeJsonReportWriter;
import io.phoenixfire.core.retry.RetryPolicies;
import io.phoenixfire.core.supervisor.AttemptOutcome;
import io.phoenixfire.core.supervisor.DefaultFailureClassifier;
import io.phoenixfire.core.supervisor.ForkExecutionResult;
import io.phoenixfire.core.supervisor.ForkLauncher;
import io.phoenixfire.core.supervisor.ForkSupervisor;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;
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
    private final String runId = UUID.randomUUID().toString();

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
            ReportModel model = snapshotWithEnvelope();
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

        ReportModel model = snapshotWithEnvelope();
        writeReports(model);
        logRetrySummary(model);
        log.info(new ExecutionSummary(model).describe());
        return new ExecutionSummary(model);
    }

    /** Snapshot the journal and attach this run's identity/context envelope. */
    private ReportModel snapshotWithEnvelope() {
        ReportModel base = journal.snapshot();
        return new ReportModel(base.records(), base.startMillis(), base.endMillis(), buildEnvelope());
    }

    /** Assemble the run envelope from engine-known facts plus the externally-supplied metadata. */
    private RunEnvelope buildEnvelope() {
        List<String> ladder = new ArrayList<>();
        for (IsolationLevel level : config.escalationLadder()) {
            ladder.add(level.name());
        }
        String jvm = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor");
        if (jvm != null && vendor != null) {
            jvm = jvm + " (" + vendor + ")";
        }
        return RunEnvelope.builder()
                .runId(runId)
                .host(detectHost())
                .osName(System.getProperty("os.name"))
                .osArch(System.getProperty("os.arch"))
                .jvm(jvm)
                .maxAttempts(config.maxAttempts())
                .escalationLadder(ladder)
                .forkCount(config.forkCount())
                .metadata(config.runMetadata())
                .build();
    }

    private static String detectHost() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (Exception ignored) {
            // fall through to environment variables
        }
        String env = System.getenv("HOSTNAME");
        if (env == null || env.isBlank()) {
            env = System.getenv("COMPUTERNAME");
        }
        return env == null || env.isBlank() ? null : env;
    }

    /**
     * Logs a consolidated table of every test that needed more than one attempt: the conditions that
     * forced each retry, the isolation level it ran at, the fork that ran it, and whether it ultimately
     * recovered or stayed broken. This is the primary signal for teams triaging flaky/crashing tests.
     */
    private void logRetrySummary(ReportModel model) {
        List<TestRecord> retried = new ArrayList<>();
        for (TestRecord r : model.records()) {
            if (r.attemptCount() > 1) {
                retried.add(r);
            }
        }
        if (retried.isEmpty()) {
            return;
        }
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder(nl);
        sb.append("=== Phoenixfire retry summary: ").append(retried.size())
                .append(retried.size() == 1 ? " test required retries ===" : " tests required retries ===").append(nl);
        for (TestRecord r : retried) {
            String status = r.recovered() ? "RECOVERED" : r.state().name();
            sb.append("  ").append(String.format("%-10s", status)).append(label(r.testId())).append(nl);
            for (ExecutionAttempt a : r.attempts()) {
                sb.append(String.format("      attempt %-2d  %-20s %-28s %s%s",
                        a.attemptNumber(),
                        a.isolationLevel(),
                        condition(a.outcome(), a.failureMode(), 0),
                        a.forkId() == null ? "" : a.forkId(),
                        nl));
            }
        }
        log.info(sb.toString());
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
                    .exitCode(result.exitCode())
                    .startMillis(now - duration)
                    .endMillis(now)
                    .throwableMessage(message)
                    .throwableStackTrace(stack)
                    .build();
            journal.recordAttempt(testId, attempt);

            if (!outcomeState.isSuccessful()) {
                log.warn(label(testId) + " " + condition(outcomeState, failureMode, result.exitCode())
                        + " in " + result.forkId() + " at " + unit.isolationLevel()
                        + " (attempt " + attemptNumber + "/" + config.maxAttempts() + ")");
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
            log.warn(label(testId) + " exhausted its retry budget (maxAttempts=" + config.maxAttempts()
                    + ") at " + currentLevel + "; recording terminal " + outcome + ".");
            return 0L;
        }
        FailureContext context = new FailureContext(outcome, failureMode, currentLevel,
                record.attemptCount(), exitCode);
        RetryDecision decision = retryPolicy.decide(record, context);
        if (decision.shouldRetry()) {
            journal.scheduleRetry(testId, decision.nextLevel());
            String escalation = decision.nextLevel() == currentLevel
                    ? "re-running at " + currentLevel
                    : "escalating " + currentLevel + " -> " + decision.nextLevel();
            String backoffNote = decision.backoffMillis() > 0
                    ? " after " + decision.backoffMillis() + "ms backoff" : "";
            log.info("RETRY " + label(testId) + ": " + escalation + backoffNote
                    + " (next attempt " + (record.attemptCount() + 1) + "/" + config.maxAttempts()
                    + ", reason=" + condition(outcome, failureMode, exitCode) + ")");
            return decision.backoffMillis();
        }
        log.warn(label(testId) + ": retry policy declined further retries after attempt "
                + record.attemptCount() + " at " + currentLevel + "; recording terminal " + outcome + ".");
        return 0L;
    }

    /** Human-readable "Class#method" label for logs. */
    private static String label(TestId id) {
        String display = id.displayName() != null ? id.displayName() : id.uniqueId();
        return id.className() + "#" + display;
    }

    /** Compact description of why an attempt did not succeed, e.g. {@code CRASHED (SIGKILL, exit=137)}. */
    private static String condition(TestState outcome, FailureMode mode, int exitCode) {
        if (outcome == TestState.CRASHED) {
            String m = (mode == null || mode == FailureMode.NONE) ? "UNKNOWN" : mode.name();
            return "CRASHED (" + m + (exitCode != 0 ? ", exit=" + exitCode : "") + ")";
        }
        if (outcome == TestState.FAILED) {
            String m = (mode == null || mode == FailureMode.NONE) ? "ASSERTION_FAILURE" : mode.name();
            return "FAILED (" + m + ")";
        }
        return outcome.name();
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
        writers.add(new JsonLinesReportWriter());
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
