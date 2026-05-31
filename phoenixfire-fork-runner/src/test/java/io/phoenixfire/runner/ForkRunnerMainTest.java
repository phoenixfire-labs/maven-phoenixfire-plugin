package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkRunnerMainTest {

    @Test
    void discoverPathCollectsDiscoveredMessages() throws Exception {
        CopyOnWriteArrayList<Map<String, Object>> inbound = new CopyOnWriteArrayList<>();
        try (ForkRunnerTestSupport.IpcSession session = ForkRunnerTestSupport.startCollectingServer(
                channel -> {
                    try {
                        ForkRunnerTestSupport.sendCommand(channel,
                                ForkRunnerTestSupport.discoverCommand(
                                        List.of("**/ForkExecutionFixture.java"),
                                        List.of("**/Skip*.java")));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                inbound)) {
            Map<String, String> props = ForkRunnerTestSupport.baseProps(session.port);
            Integer exit = ForkRunnerTestSupport.runMain(props);
            assertEquals(0, exit);
            ForkRunnerTestSupport.awaitBye(inbound);
        }

        boolean found = inbound.stream()
                .anyMatch(m -> IpcProtocol.MSG_DISCOVERED.equals(String.valueOf(m.get(IpcProtocol.FIELD_TYPE)))
                        && String.valueOf(m.get(IpcProtocol.FIELD_CLASS_NAME)).contains("ForkExecutionFixture"));
        assertTrue(found, "expected DISCOVERED for ForkExecutionFixture");
        assertTrue(inbound.stream()
                .anyMatch(m -> IpcProtocol.MSG_BYE.equals(String.valueOf(m.get(IpcProtocol.FIELD_TYPE)))));
    }

    @Test
    void discoverWithEmptyIncludesUsesDefaultPattern() throws Exception {
        CopyOnWriteArrayList<Map<String, Object>> inbound = new CopyOnWriteArrayList<>();
        try (ForkRunnerTestSupport.IpcSession session = ForkRunnerTestSupport.startCollectingServer(
                channel -> {
                    try {
                        ForkRunnerTestSupport.sendCommand(channel,
                                ForkRunnerTestSupport.discoverCommand(List.of(), null));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                inbound)) {
            Integer exit = ForkRunnerTestSupport.runMain(ForkRunnerTestSupport.baseProps(session.port));
            assertEquals(0, exit);
            ForkRunnerTestSupport.awaitBye(inbound);
        }
        assertTrue(inbound.stream()
                .anyMatch(m -> IpcProtocol.MSG_DISCOVERED.equals(String.valueOf(m.get(IpcProtocol.FIELD_TYPE)))));
    }

    @Test
    void executePathStreamsLifecycleEvents() throws Exception {
        String uniqueId = ForkRunnerTestSupport.discoverUniqueId();
        CopyOnWriteArrayList<Map<String, Object>> inbound = new CopyOnWriteArrayList<>();
        try (ForkRunnerTestSupport.IpcSession session = ForkRunnerTestSupport.startCollectingServer(
                channel -> {
                    try {
                        ForkRunnerTestSupport.sendCommand(channel,
                                ForkRunnerTestSupport.executeCommand(List.of(uniqueId)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                inbound)) {
            Integer exit = ForkRunnerTestSupport.runMain(ForkRunnerTestSupport.baseProps(session.port));
            assertEquals(0, exit);
            ForkRunnerTestSupport.awaitBye(inbound);
        }

        assertTrue(inbound.stream()
                .anyMatch(m -> IpcProtocol.MSG_TEST_STARTED.equals(String.valueOf(m.get(IpcProtocol.FIELD_TYPE)))));
        assertTrue(inbound.stream()
                .anyMatch(m -> IpcProtocol.MSG_TEST_FINISHED.equals(String.valueOf(m.get(IpcProtocol.FIELD_TYPE)))
                        && IpcProtocol.STATUS_PASSED.equals(String.valueOf(m.get(IpcProtocol.FIELD_STATUS)))));
    }

    @Test
    void unknownCommandExitsTwo() throws Exception {
        CopyOnWriteArrayList<Map<String, Object>> inbound = new CopyOnWriteArrayList<>();
        try (ForkRunnerTestSupport.IpcSession session = ForkRunnerTestSupport.startCollectingServer(
                channel -> {
                    try {
                        ForkRunnerTestSupport.sendCommand(channel,
                                Map.of(IpcProtocol.FIELD_TYPE, "UNKNOWN"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                inbound)) {
            Integer exit = ForkRunnerTestSupport.runMain(ForkRunnerTestSupport.baseProps(session.port));
            assertEquals(2, exit);
            ForkRunnerTestSupport.awaitBye(inbound);
        }
    }

    @Test
    void missingPortExitsOneWithExplicitStartupMessage() throws Exception {
        Map<String, String> props = ForkRunnerTestSupport.baseProps(9_999);
        props.remove(IpcProtocol.PROP_PORT);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            Integer exit = ForkRunnerTestSupport.runMain(props);
            assertEquals(1, exit);
            String diagnostic = err.toString(StandardCharsets.UTF_8);
            assertTrue(diagnostic.contains("[phoenixfire-fork] startup failed:"),
                    () -> "expected explicit startup failure, got: " + diagnostic);
            assertTrue(diagnostic.contains(IpcProtocol.PROP_PORT),
                    () -> "expected property name in message, got: " + diagnostic);
            assertFalse(diagnostic.contains("NumberFormatException"),
                    () -> "misconfiguration should not surface as parse exception: " + diagnostic);
            assertFalse(diagnostic.contains("\tat "),
                    () -> "startup misconfiguration should not print a stack trace: " + diagnostic);
        } finally {
            System.setErr(previous);
        }
    }

    @Test
    void invalidPortExitsOneWithExplicitStartupMessage() throws Exception {
        Map<String, String> props = ForkRunnerTestSupport.baseProps(9_999);
        props.put(IpcProtocol.PROP_PORT, "not-a-port");
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            assertEquals(1, ForkRunnerTestSupport.runMain(props));
            String diagnostic = err.toString(StandardCharsets.UTF_8);
            assertTrue(diagnostic.contains("startup failed"));
            assertTrue(diagnostic.contains("not an integer"));
            assertFalse(diagnostic.contains("\tat "));
        } finally {
            System.setErr(previous);
        }
    }

    @Test
    void nullCommandReturnsWithoutExit() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Integer> exit = new java.util.concurrent.atomic.AtomicReference<>();
        try (ForkRunnerTestSupport.IpcSession session = ForkRunnerTestSupport.startServer(channel -> {
            connected.countDown();
            channel.close();
        })) {
            Map<String, String> props = ForkRunnerTestSupport.baseProps(session.port);
            Thread runner = new Thread(
                    () -> exit.set(ForkRunnerTestSupport.runMain(props)),
                    "fork-runner-main");
            runner.start();
            assertTrue(connected.await(5, TimeUnit.SECONDS));
            runner.join(15_000);
            assertNull(exit.get());
        }
    }

    @Test
    void scanRootsFallsBackToClasspathDirectories() throws Exception {
        String previous = System.getProperty(IpcProtocol.PROP_SCAN_ROOTS);
        System.setProperty(IpcProtocol.PROP_SCAN_ROOTS, "");
        try {
            List<Path> roots = ForkRunnerTestSupport.invokeScanRoots();
            assertFalse(roots.isEmpty());
            assertTrue(roots.stream().anyMatch(p -> p.toString().contains("test-classes")
                    || p.toString().contains("classes")));
        } finally {
            if (previous == null) {
                System.clearProperty(IpcProtocol.PROP_SCAN_ROOTS);
            } else {
                System.setProperty(IpcProtocol.PROP_SCAN_ROOTS, previous);
            }
        }
    }

    @Test
    void scanRootsParsesConfiguredEntries() throws Exception {
        Path root = ForkRunnerTestSupport.testClassesRoot();
        System.setProperty(IpcProtocol.PROP_SCAN_ROOTS, "  " + root + File.pathSeparator + "  ");
        try {
            List<Path> roots = ForkRunnerTestSupport.invokeScanRoots();
            assertEquals(1, roots.size());
            assertEquals(root, roots.get(0));
        } finally {
            System.clearProperty(IpcProtocol.PROP_SCAN_ROOTS);
        }
    }

    @Test
    void sendByeSwallowsSendFailure() throws Exception {
        try (ForkRunnerTestSupport.IpcSession session = ForkRunnerTestSupport.startServer(channel -> {
        })) {
            ForkIpcClient client = new ForkIpcClient(session.port, ForkRunnerTestSupport.FORK_ID,
                    ForkRunnerTestSupport.TOKEN);
            client.close();
            ForkRunnerTestSupport.invokeSendBye(client);
        }
    }

    @Test
    void asStringListHandlesNonListAndNullItems() throws Exception {
        assertTrue(ForkRunnerTestSupport.invokeAsStringList("not-a-list").isEmpty());
        assertEquals(List.of("a", "b"),
                ForkRunnerTestSupport.invokeAsStringList(Arrays.asList("a", null, "b")));
    }

    @Test
    void exitsNonZeroWhenPortMissingInExternalProcess() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(javaBinary(), "-cp", runnerClasspath(),
                ForkRunnerMain.class.getName());
        pb.environment().remove(IpcProtocol.PROP_PORT);
        Process process = pb.start();
        assertTrue(process.waitFor(15, TimeUnit.SECONDS));
        assertTrue(process.exitValue() != 0);
    }

    private static String javaBinary() {
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(new File(javaHome, "bin"), windows ? "java.exe" : "java").getAbsolutePath();
    }

    private static String runnerClasspath() {
        String sep = File.pathSeparator;
        return String.join(sep, List.of(
                Path.of("target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-api", "target", "classes").toAbsolutePath().toString(),
                Path.of("..", "phoenixfire-fork-runner", "target", "classes").toAbsolutePath().toString()));
    }
}
