package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;

import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Entry point executed inside every forked JVM.
 *
 * <p>Connects to the controller, performs the HELLO handshake, then acts on a single command:
 * <ul>
 *   <li>{@code DISCOVER} - discover tests under the configured classpath roots and stream a
 *       {@code DISCOVERED} message per leaf test;</li>
 *   <li>{@code EXECUTE} - run the assigned set of unique ids, streaming start/finish events and
 *       periodic heartbeats.</li>
 * </ul>
 *
 * <p>The process always attempts to send {@code BYE} and exit 0 on clean completion; any uncaught
 * failure results in a non-zero exit which the controller interprets as a fork failure.
 */
public final class ForkRunnerMain {

    public static void main(String[] args) {
        int exitCode = 1;
        ForkIpcClient client = null;
        try {
            Integer port = readRequiredPort();
            if (port == null) {
                return;
            }
            String token = System.getProperty(IpcProtocol.PROP_TOKEN, "");
            String forkId = System.getProperty(IpcProtocol.PROP_FORK_ID, "unknown");

            client = new ForkIpcClient(port, forkId, token);

            Map<String, Object> command = client.readMessage();
            if (command == null) {
                return;
            }
            String type = String.valueOf(command.get(IpcProtocol.FIELD_TYPE));
            if (IpcProtocol.MSG_DISCOVER.equals(type)) {
                runDiscovery(client, command);
                exitCode = 0;
            } else if (IpcProtocol.MSG_EXECUTE.equals(type)) {
                runExecution(client, command);
                exitCode = 0;
            } else {
                exitCode = 2;
            }

            sendBye(client);
        } catch (Throwable t) {
            reportUncaught(t);
            exitCode = 1;
        } finally {
            if (client != null) {
                client.close();
            }
        }
        shutdown(exitCode);
    }

    static IntConsumer exitAction = System::exit;

    static void shutdown(int exitCode) {
        // Explicitly flush streams before exiting.
        System.out.flush();
        System.err.flush();
        exitAction.accept(exitCode);
    }

    /**
     * Returns {@code null} after calling {@link #failStartup(String)} when the port property is
     * missing or invalid.
     */
    private static Integer readRequiredPort() {
        String raw = System.getProperty(IpcProtocol.PROP_PORT);
        if (raw == null || raw.isBlank()) {
            failStartup("missing required system property: " + IpcProtocol.PROP_PORT);
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            failStartup("invalid " + IpcProtocol.PROP_PORT + " (not an integer): " + raw);
            return null;
        }
    }

    /** Logs a single-line startup failure and exits; no stack trace (misconfiguration). */
    private static void failStartup(String message) {
        reportLine("startup failed", message);
        shutdown(1);
    }

    private static void reportLine(String kind, String detail) {
        System.err.println("[phoenixfire-fork] " + kind + ": " + detail);
    }

    private static void reportUncaught(Throwable t) {
        String summary = t.getClass().getSimpleName()
                + (t.getMessage() != null && !t.getMessage().isEmpty() ? ": " + t.getMessage() : "");
        System.err.println("[phoenixfire-fork] uncaught " + summary);
        t.printStackTrace(System.err);
    }

    @SuppressWarnings("unchecked")
    private static void runDiscovery(ForkIpcClient client, Map<String, Object> command) throws Exception {
        List<String> includes = asStringList(command.get(IpcProtocol.FIELD_INCLUDES));
        List<String> excludes = asStringList(command.get(IpcProtocol.FIELD_EXCLUDES));

        Set<Path> roots = scanRoots();
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClasspathRoots(roots));

        List<String> includeRegexes = ClassNamePatterns.toRegexes(includes);
        if (includeRegexes.isEmpty()) {
            includeRegexes = List.of(".*");
        }
        builder.filters(ClassNameFilter.includeClassNamePatterns(includeRegexes.toArray(new String[0])));

        List<String> excludeRegexes = ClassNamePatterns.toRegexes(excludes);
        if (!excludeRegexes.isEmpty()) {
            builder.filters(ClassNameFilter.excludeClassNamePatterns(excludeRegexes.toArray(new String[0])));
        }

        LauncherDiscoveryRequest request = builder.build();
        Launcher launcher = LauncherFactory.create();

        long interval = Long.getLong("phoenixfire.heartbeat.interval", 2_000L);
        try (HeartbeatSender heartbeat = new HeartbeatSender(client, interval)) {
            heartbeat.start();
            TestPlan plan = launcher.discover(request);
            for (TestIdentifier root : plan.getRoots()) {
                emitLeafTests(client, plan, root);
            }
        }
    }

    private static void emitLeafTests(ForkIpcClient client, TestPlan plan, TestIdentifier identifier) throws Exception {
        if (identifier.isTest()) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_DISCOVERED);
            msg.put(IpcProtocol.FIELD_UNIQUE_ID, identifier.getUniqueId());
            msg.put(IpcProtocol.FIELD_DISPLAY_NAME, identifier.getDisplayName());
            msg.put(IpcProtocol.FIELD_CLASS_NAME, StreamingExecutionListener.classNameOf(identifier));
            client.send(msg);
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            emitLeafTests(client, plan, child);
        }
    }

    private static void runExecution(ForkIpcClient client, Map<String, Object> command) throws Exception {
        List<String> testIds = asStringList(command.get(IpcProtocol.FIELD_TEST_IDS));
        List<org.junit.platform.engine.DiscoverySelector> selectors = new ArrayList<>();
        for (String id : testIds) {
            selectors.add(DiscoverySelectors.selectUniqueId(id));
        }

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();

        Launcher launcher = LauncherFactory.create();
        StreamingExecutionListener listener = new StreamingExecutionListener(client);

        long interval = Long.getLong("phoenixfire.heartbeat.interval", 2_000L);
        try (HeartbeatSender heartbeat = new HeartbeatSender(client, interval)) {
            heartbeat.start();
            launcher.execute(request, listener);
        }
    }

    private static Set<Path> scanRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        String configured = System.getProperty(IpcProtocol.PROP_SCAN_ROOTS, "");
        if (!configured.isBlank()) {
            for (String entry : configured.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    roots.add(Paths.get(entry.trim()));
                }
            }
        }
        if (roots.isEmpty()) {
            // Fall back to classpath directories so discovery still works if roots were not supplied.
            String cp = System.getProperty("java.class.path", "");
            for (String entry : cp.split(File.pathSeparator)) {
                File f = new File(entry);
                if (f.isDirectory()) {
                    roots.add(f.toPath());
                }
            }
        }
        return roots;
    }

    private static void sendBye(ForkIpcClient client) {
        try {
            Map<String, Object> bye = new LinkedHashMap<>();
            bye.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_BYE);
            client.send(bye);
        } catch (Exception ignored) {
            // best effort
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        }
        return result;
    }

    private ForkRunnerMain() {
    }
}
