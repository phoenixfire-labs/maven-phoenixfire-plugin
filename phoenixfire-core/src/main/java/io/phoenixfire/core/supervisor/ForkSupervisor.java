package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.model.TestId;
import io.phoenixfire.api.model.TestState;
import io.phoenixfire.api.spi.FailureClassifier;
import io.phoenixfire.api.spi.WorkUnit;
import io.phoenixfire.core.config.PhoenixfireConfiguration;
import io.phoenixfire.core.ipc.ForkChannel;
import io.phoenixfire.core.ipc.ForkSession;
import io.phoenixfire.core.ipc.IpcServer;
import io.phoenixfire.core.journal.ExecutionJournal;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Launches and supervises a single forked JVM for one work unit (or a discovery pass).
 *
 * <p>Responsibilities: spawn the fork, send it its command over IPC, monitor liveness via the
 * heartbeat channel and process exit code, classify the failure mode if it dies, and attribute
 * which test was in-flight versus which were never started.
 */
public final class ForkSupervisor {

    private final PhoenixfireConfiguration config;
    private final IpcServer ipcServer;
    private final ForkLauncher launcher;
    private final ExecutionJournal journal;
    private final FailureClassifier failureClassifier;
    private final PhoenixfireLogger log;
    private final Path forkLogDir;
    private final AtomicInteger forkCounter = new AtomicInteger();

    private static final long POLL_INTERVAL_MILLIS = 200L;
    private static final long DISCONNECT_GRACE_MILLIS = 2_000L;

    public ForkSupervisor(PhoenixfireConfiguration config,
                          IpcServer ipcServer,
                          ForkLauncher launcher,
                          ExecutionJournal journal,
                          FailureClassifier failureClassifier,
                          PhoenixfireLogger log,
                          Path forkLogDir) {
        this.config = config;
        this.ipcServer = ipcServer;
        this.launcher = launcher;
        this.journal = journal;
        this.failureClassifier = failureClassifier;
        this.log = log;
        this.forkLogDir = forkLogDir;
    }

    /** Discover tests by launching a fork in DISCOVER mode. */
    public ForkDiscoveryResult runDiscovery() {
        String forkId = "discover-" + forkCounter.incrementAndGet();
        String token = UUID.randomUUID().toString();

        Map<String, Object> command = new LinkedHashMap<>();
        command.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_DISCOVER);
        command.put(IpcProtocol.FIELD_INCLUDES, new ArrayList<Object>(config.includes()));
        command.put(IpcProtocol.FIELD_EXCLUDES, new ArrayList<Object>(config.excludes()));

        Session session = new Session(forkId, token, command, Set.of());
        RunOutcome outcome = runFork(session, null);

        FailureMode failureMode = outcome.clean ? FailureMode.NONE
                : failureClassifier.classify(outcome.exitCode, outcome.heartbeatTimedOut,
                outcome.handshakeCompleted, outcome.diagnostic);
        return new ForkDiscoveryResult(new ArrayList<>(session.discovered.values()),
                outcome.clean, failureMode, outcome.exitCode, outcome.diagnostic);
    }

    /** Execute a work unit by launching a fork in EXECUTE mode. */
    public ForkExecutionResult runExecution(WorkUnit unit) {
        String forkId = "fork-" + forkCounter.incrementAndGet();
        String token = UUID.randomUUID().toString();

        List<Object> ids = new ArrayList<>();
        Set<TestId> assigned = new LinkedHashSet<>();
        for (TestId t : unit.tests()) {
            ids.add(t.uniqueId());
            assigned.add(t);
        }

        Map<String, Object> command = new LinkedHashMap<>();
        command.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_EXECUTE);
        command.put(IpcProtocol.FIELD_TEST_IDS, ids);

        Session session = new Session(forkId, token, command, assigned);
        RunOutcome outcome = runFork(session, unit.forkConfig());

        boolean clean = outcome.clean;
        FailureMode failureMode = clean ? FailureMode.NONE
                : failureClassifier.classify(outcome.exitCode, outcome.heartbeatTimedOut,
                outcome.handshakeCompleted, outcome.diagnostic);

        Set<TestId> notStarted = new LinkedHashSet<>(assigned);
        notStarted.removeAll(session.started);

        TestId inFlight = session.inFlight;
        if (inFlight != null && session.outcomes.containsKey(inFlight)) {
            inFlight = null;
        }

        return new ForkExecutionResult(forkId, new LinkedHashMap<>(session.outcomes), inFlight, notStarted,
                failureMode, outcome.exitCode, clean, outcome.diagnostic);
    }

    private RunOutcome runFork(Session session, io.phoenixfire.api.spi.ForkConfig forkConfig) {
        Path logFile = forkLogDir == null ? null : forkLogDir.resolve(session.forkId + ".log");
        ipcServer.register(session.forkId, session);

        Process process = null;
        boolean heartbeatTimedOut = false;
        try {
            if (logFile != null) {
                Files.createDirectories(logFile.getParent());
            }
            io.phoenixfire.api.spi.ForkConfig fc =
                    forkConfig == null ? io.phoenixfire.api.spi.ForkConfig.empty() : forkConfig;
            process = launcher.launch(session.forkId, session.token, fc, logFile);

            long timeout = config.heartbeatTimeoutMillis();
            while (process.isAlive()) {
                if (process.waitFor(POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)) {
                    break;
                }
                long idle = System.currentTimeMillis() - session.lastActivityMillis;
                if (idle > timeout) {
                    heartbeatTimedOut = true;
                    log.warn("Fork " + session.forkId + " exceeded heartbeat/connect timeout (" + timeout
                            + "ms idle); terminating.");
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                    break;
                }
            }

            int exitCode = process.isAlive() ? DefaultFailureClassifier.UNKNOWN_EXIT : process.exitValue();

            // Give the connection thread a moment to flush any final messages.
            session.disconnected.await(DISCONNECT_GRACE_MILLIS, TimeUnit.MILLISECONDS);

            boolean clean = !heartbeatTimedOut && exitCode == 0 && session.byeReceived;
            String diagnostic = clean ? null : readTail(logFile, 60);

            return new RunOutcome(clean, exitCode, heartbeatTimedOut, session.connected, diagnostic);
        } catch (IOException e) {
            log.error("Failed to launch fork " + session.forkId + ": " + e.getMessage());
            return new RunOutcome(false, DefaultFailureClassifier.UNKNOWN_EXIT, false, false,
                    "Launch failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new RunOutcome(false, DefaultFailureClassifier.UNKNOWN_EXIT, false, session.connected,
                    "Interrupted while supervising fork");
        } finally {
            ipcServer.unregister(session.forkId);
        }
    }

    private String readTail(Path logFile, int maxLines) {
        if (logFile == null || !Files.exists(logFile)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - maxLines);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException e) {
            return null;
        }
    }

    private static final class RunOutcome {
        final boolean clean;
        final int exitCode;
        final boolean heartbeatTimedOut;
        final boolean handshakeCompleted;
        final String diagnostic;

        RunOutcome(boolean clean, int exitCode, boolean heartbeatTimedOut, boolean handshakeCompleted,
                   String diagnostic) {
            this.clean = clean;
            this.exitCode = exitCode;
            this.heartbeatTimedOut = heartbeatTimedOut;
            this.handshakeCompleted = handshakeCompleted;
            this.diagnostic = diagnostic;
        }
    }

    /** Per-fork IPC session: collects events and tracks in-flight progress. */
    private final class Session implements ForkSession {
        private final String forkId;
        private final String token;
        private final Map<String, Object> command;
        private final Set<TestId> assigned;

        private final Map<TestId, AttemptOutcome> outcomes = new ConcurrentHashMap<>();
        private final Map<String, TestId> discovered = new ConcurrentHashMap<>();
        private final Set<TestId> started = ConcurrentHashMap.newKeySet();
        private final CountDownLatch disconnected = new CountDownLatch(1);

        private volatile long lastActivityMillis = System.currentTimeMillis();
        private volatile boolean connected = false;
        private volatile boolean byeReceived = false;
        private volatile TestId inFlight = null;

        Session(String forkId, String token, Map<String, Object> command, Set<TestId> assigned) {
            this.forkId = forkId;
            this.token = token;
            this.command = command;
            this.assigned = assigned;
        }

        @Override
        public String token() {
            return token;
        }

        @Override
        public void onConnected(ForkChannel channel) {
            connected = true;
            touch();
            try {
                channel.send(command);
            } catch (IOException e) {
                log.warn("Failed to send command to fork " + forkId + ": " + e.getMessage());
            }
        }

        @Override
        public void onMessage(Map<String, Object> message) {
            touch();
            String type = String.valueOf(message.get(IpcProtocol.FIELD_TYPE));
            switch (type) {
                case IpcProtocol.MSG_HEARTBEAT:
                    break;
                case IpcProtocol.MSG_DISCOVERED:
                    handleDiscovered(message);
                    break;
                case IpcProtocol.MSG_TEST_STARTED:
                    handleStarted(message);
                    break;
                case IpcProtocol.MSG_TEST_FINISHED:
                    handleFinished(message);
                    break;
                case IpcProtocol.MSG_BYE:
                    byeReceived = true;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onDisconnected() {
            disconnected.countDown();
        }

        private void handleDiscovered(Map<String, Object> m) {
            String uniqueId = str(m, IpcProtocol.FIELD_UNIQUE_ID);
            if (uniqueId == null) {
                return;
            }
            discovered.put(uniqueId, new TestId(uniqueId,
                    str(m, IpcProtocol.FIELD_CLASS_NAME), str(m, IpcProtocol.FIELD_DISPLAY_NAME)));
        }

        private void handleStarted(Map<String, Object> m) {
            TestId id = toTestId(m);
            if (id == null) {
                return;
            }
            started.add(id);
            inFlight = id;
            // Live progress in the in-memory journal (and audit log when enabled).
            journal.markRunning(id, forkId);
        }

        private void handleFinished(Map<String, Object> m) {
            TestId id = toTestId(m);
            if (id == null) {
                return;
            }
            String status = str(m, IpcProtocol.FIELD_STATUS);
            TestState state;
            if (IpcProtocol.STATUS_PASSED.equals(status)) {
                state = TestState.PASSED;
            } else if (IpcProtocol.STATUS_SKIPPED.equals(status)) {
                state = TestState.SKIPPED;
            } else {
                state = TestState.FAILED;
            }
            long duration = longVal(m.get(IpcProtocol.FIELD_DURATION_MILLIS));
            outcomes.put(id, new AttemptOutcome(state,
                    str(m, IpcProtocol.FIELD_FAILURE_MESSAGE), str(m, IpcProtocol.FIELD_STACK_TRACE), duration));
            if (id.equals(inFlight)) {
                inFlight = null;
            }
        }

        private TestId toTestId(Map<String, Object> m) {
            String uniqueId = str(m, IpcProtocol.FIELD_UNIQUE_ID);
            if (uniqueId == null) {
                return null;
            }
            return new TestId(uniqueId, str(m, IpcProtocol.FIELD_CLASS_NAME), str(m, IpcProtocol.FIELD_DISPLAY_NAME));
        }

        private void touch() {
            lastActivityMillis = System.currentTimeMillis();
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static long longVal(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(v.toString());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
