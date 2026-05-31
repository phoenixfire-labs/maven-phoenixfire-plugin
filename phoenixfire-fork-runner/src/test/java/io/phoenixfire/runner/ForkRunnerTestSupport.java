package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.core.ipc.ForkChannel;
import io.phoenixfire.core.ipc.ForkSession;
import io.phoenixfire.core.ipc.IpcServer;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * In-process harness for {@link ForkRunnerMain}: replaces {@link ForkRunnerMain#exitAction}
 * to capture exit codes, spins up {@link IpcServer}, and restores system properties afterward.
 */
final class ForkRunnerTestSupport {

    static final String FORK_ID = "test-fork";
    static final String TOKEN = "test-token";

    private ForkRunnerTestSupport() {
    }

    static Path testClassesRoot() throws Exception {
        return Path.of(ForkRunnerTestSupport.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    static Map<String, String> baseProps(int port) throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(IpcProtocol.PROP_PORT, String.valueOf(port));
        props.put(IpcProtocol.PROP_TOKEN, TOKEN);
        props.put(IpcProtocol.PROP_FORK_ID, FORK_ID);
        props.put(IpcProtocol.PROP_SCAN_ROOTS, testClassesRoot().toString());
        props.put("phoenixfire.heartbeat.interval", "250");
        return props;
    }

    private static final List<String> MANAGED_PROPS = List.of(
            IpcProtocol.PROP_PORT,
            IpcProtocol.PROP_TOKEN,
            IpcProtocol.PROP_FORK_ID,
            IpcProtocol.PROP_SCAN_ROOTS,
            "phoenixfire.heartbeat.interval");

    /** {@code null} when {@code main} returned without calling {@code shutdown}. */
    static Integer runMain(Map<String, String> props) {
        Map<String, String> saved = snapshotProps(MANAGED_PROPS);
        IntConsumer previousExit = ForkRunnerMain.exitAction;
        AtomicReference<Integer> captured = new AtomicReference<>();
        ForkRunnerMain.exitAction = captured::set;
        try {
            for (String key : MANAGED_PROPS) {
                if (props.containsKey(key)) {
                    System.setProperty(key, props.get(key));
                } else {
                    System.clearProperty(key);
                }
            }
            ForkRunnerMain.main(new String[0]);
            return captured.get();
        } finally {
            ForkRunnerMain.exitAction = previousExit;
            restoreProps(saved);
        }
    }

    static IpcSession startServer(Consumer<ForkChannel> onConnected) throws Exception {
        IpcServer server = new IpcServer(PhoenixfireLogger.console());
        int port = server.start();
        server.register(FORK_ID, new ForkSession() {
            @Override
            public String token() {
                return TOKEN;
            }

            @Override
            public void onConnected(ForkChannel channel) {
                onConnected.accept(channel);
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
            }
        });
        return new IpcSession(server, port);
    }

    static IpcSession startCollectingServer(Consumer<ForkChannel> onConnected,
            CopyOnWriteArrayList<Map<String, Object>> inbound) throws Exception {
        IpcServer server = new IpcServer(PhoenixfireLogger.console());
        int port = server.start();
        server.register(FORK_ID, new ForkSession() {
            @Override
            public String token() {
                return TOKEN;
            }

            @Override
            public void onConnected(ForkChannel channel) {
                onConnected.accept(channel);
            }

            @Override
            public void onMessage(Map<String, Object> message) {
                inbound.add(message);
            }

            @Override
            public void onDisconnected() {
            }
        });
        return new IpcSession(server, port);
    }

    static Map<String, Object> discoverCommand(List<String> includes, List<String> excludes) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_DISCOVER);
        if (includes != null) {
            cmd.put(IpcProtocol.FIELD_INCLUDES, includes);
        }
        if (excludes != null) {
            cmd.put(IpcProtocol.FIELD_EXCLUDES, excludes);
        }
        return cmd;
    }

    static Map<String, Object> executeCommand(List<String> testIds) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_EXECUTE);
        cmd.put(IpcProtocol.FIELD_TEST_IDS, testIds);
        return cmd;
    }

    @SuppressWarnings("unchecked")
    static List<String> invokeAsStringList(Object value) throws Exception {
        Method method = ForkRunnerMain.class.getDeclaredMethod("asStringList", Object.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(null, value);
    }

    static void invokeSendBye(ForkIpcClient client) throws Exception {
        Method method = ForkRunnerMain.class.getDeclaredMethod("sendBye", ForkIpcClient.class);
        method.setAccessible(true);
        method.invoke(null, client);
    }

    @SuppressWarnings("unchecked")
    static List<Path> invokeScanRoots() throws Exception {
        Method method = ForkRunnerMain.class.getDeclaredMethod("scanRoots");
        method.setAccessible(true);
        return new ArrayList<>((java.util.Set<Path>) method.invoke(null));
    }

    static final class IpcSession implements AutoCloseable {
        private final IpcServer server;
        final int port;

        IpcSession(IpcServer server, int port) {
            this.server = server;
            this.port = port;
        }

        @Override
        public void close() {
            server.close();
        }
    }

    static void sendCommand(ForkChannel channel, Map<String, Object> command) throws Exception {
        channel.send(command);
    }

    static void awaitBye(CopyOnWriteArrayList<Map<String, Object>> inbound) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            for (Map<String, Object> msg : inbound) {
                if (IpcProtocol.MSG_BYE.equals(String.valueOf(msg.get(IpcProtocol.FIELD_TYPE)))) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("BYE not received");
    }

    static void awaitMessage(CopyOnWriteArrayList<Map<String, Object>> inbound, String type)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            for (Map<String, Object> msg : inbound) {
                if (type.equals(String.valueOf(msg.get(IpcProtocol.FIELD_TYPE)))) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError(type + " not received");
    }

    static void sendAndAwaitBye(ForkChannel channel, Map<String, Object> command,
            CopyOnWriteArrayList<Map<String, Object>> inbound) throws Exception {
        channel.send(command);
        awaitBye(inbound);
    }

    static String discoverUniqueId() throws Exception {
        org.junit.platform.launcher.LauncherDiscoveryRequest request =
                org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request()
                        .selectors(org.junit.platform.engine.discovery.DiscoverySelectors
                                .selectClass(ForkExecutionFixture.class))
                        .build();
        org.junit.platform.launcher.TestPlan plan =
                org.junit.platform.launcher.core.LauncherFactory.create().discover(request);
        for (org.junit.platform.launcher.TestIdentifier root : plan.getRoots()) {
            String id = findTestUniqueId(plan, root);
            if (id != null) {
                return id;
            }
        }
        throw new IllegalStateException("no test discovered in ForkExecutionFixture");
    }

    private static String findTestUniqueId(org.junit.platform.launcher.TestPlan plan,
            org.junit.platform.launcher.TestIdentifier id) {
        if (id.isTest()) {
            return id.getUniqueId();
        }
        for (org.junit.platform.launcher.TestIdentifier child : plan.getChildren(id)) {
            String found = findTestUniqueId(plan, child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Map<String, String> snapshotProps(Iterable<String> keys) {
        Map<String, String> saved = new HashMap<>();
        for (String key : keys) {
            saved.put(key, System.getProperty(key));
        }
        return saved;
    }

    private static void restoreProps(Map<String, String> saved) {
        saved.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }
}
