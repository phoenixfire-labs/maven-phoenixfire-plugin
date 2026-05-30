package io.phoenixfire.runner;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingExecutionListenerTest {

    @Test
    void sampleTestForDiscovery() {
        assertTrue(true);
    }

    @Test
    void classNameFromUniqueId() {
        TestIdentifier id = discoverSelfTest();
        assertEquals(StreamingExecutionListenerTest.class.getName(), StreamingExecutionListener.classNameOf(id));
        assertEquals("com.example.DemoTest",
                StreamingExecutionListener.parseClassNameFromUniqueId(
                        "[engine:junit-jupiter]/[class:com.example.DemoTest]/[method:demo()]"));
        assertEquals("com.example.Fallback",
                StreamingExecutionListener.parseClassNameFromUniqueId(
                        "[engine:junit-jupiter]/[class:com.example.Fallback]/[method:x()]"));
        assertEquals("UnknownClass",
                StreamingExecutionListener.parseClassNameFromUniqueId("no-class-here"));
    }

    @Test
    void streamsLifecycleEvents() throws Exception {
        TestIdentifier testId = discoverSelfTest();

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread accept = new Thread(() -> drain(server), "drain");
            accept.setDaemon(true);
            accept.start();

            try (ForkIpcClient client = new ForkIpcClient(port, "f", "t")) {
                StreamingExecutionListener listener = new StreamingExecutionListener(client);
                listener.executionStarted(testId);
                listener.executionFinished(testId, TestExecutionResult.successful());
                listener.executionSkipped(testId, "assumption");
                listener.executionFinished(testId, TestExecutionResult.failed(new AssertionError("fail")));
                listener.executionFinished(testId, TestExecutionResult.aborted(null));
            }
        }
    }

    private static TestIdentifier discoverSelfTest() {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(StreamingExecutionListenerTest.class))
                .build();
        TestPlan plan = LauncherFactory.create().discover(request);
        for (TestIdentifier root : plan.getRoots()) {
            TestIdentifier found = findTest(plan, root);
            if (found != null) {
                return found;
            }
        }
        throw new IllegalStateException("no test discovered");
    }

    private static TestIdentifier findTest(TestPlan plan, TestIdentifier id) {
        if (id.isTest()) {
            return id;
        }
        for (TestIdentifier child : plan.getChildren(id)) {
            TestIdentifier found = findTest(plan, child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void drain(ServerSocket server) {
        try (Socket socket = server.accept()) {
            Thread.sleep(500);
        } catch (Exception ignored) {
        }
    }
}
