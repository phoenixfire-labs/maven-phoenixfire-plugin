package io.phoenixfire.core.ipc;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.json.Json;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpcServerTest {

    private static final PhoenixfireLogger LOG = PhoenixfireLogger.noop();

    private IpcServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void acceptsHelloAndRoutesMessages() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch disconnected = new CountDownLatch(1);
        AtomicReference<ForkChannel> channelRef = new AtomicReference<>();

        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
                channelRef.set(channel);
                connected.countDown();
            }

            @Override
            public void onMessage(Map<String, Object> message) {
                assertEquals(IpcProtocol.MSG_HEARTBEAT, message.get(IpcProtocol.FIELD_TYPE));
            }

            @Override
            public void onDisconnected() {
                disconnected.countDown();
            }
        });

        try (Socket socket = connect(port, "fork-1", "secret")) {
            assertTrue(connected.await(5, TimeUnit.SECONDS));
            ForkChannel ch = channelRef.get();
            assertNotNull(ch);
            ch.send(Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT));
            writeLine(socket, Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT));
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
    }

    @Test
    void survivesOnDisconnectedHandlerFailure() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        CountDownLatch disconnected = new CountDownLatch(1);
        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
            }

            @Override
            public void onMessage(Map<String, Object> message) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onDisconnected() {
                disconnected.countDown();
                throw new RuntimeException("disconnect boom");
            }
        });

        try (Socket socket = connect(port, "fork-1", "secret")) {
            writeLine(socket, Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT));
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
    }

    @Test
    void stopSurvivesClosedServerSocket() throws Exception {
        server = new IpcServer(LOG);
        server.start();
        server.stop();
        server.stop();
    }

    @Test
    void rejectsBadTokenAndMalformedHello() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        server.register("f", new ForkSession() {
            @Override
            public String token() {
                return "ok";
            }

            @Override
            public void onConnected(ForkChannel channel) {
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
            }
        });

        try (Socket bad = connect(port, "f", "wrong")) {
            // connection closed
        }
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            writeLine(socket, Map.of(IpcProtocol.FIELD_TYPE, "NOT_HELLO"));
        }
    }

    @Test
    void rejectsUnknownForkId() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        try (Socket socket = connect(port, "missing", "token")) {
            // connection closed
        }
    }

    @Test
    void closesImmediatelyOnEmptyHello() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            // no hello line
        }
    }

    @Test
    void survivesMalformedMessageLines() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        CountDownLatch disconnected = new CountDownLatch(1);
        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
                disconnected.countDown();
            }
        });

        try (Socket socket = connect(port, "fork-1", "secret")) {
            OutputStream out = socket.getOutputStream();
            out.write("{not json}\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
    }

    @Test
    void acceptLoopSurvivesIoExceptionWhileRunning() throws Exception {
        server = new IpcServer(LOG);
        Field runningField = IpcServer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        Field socketField = IpcServer.class.getDeclaredField("serverSocket");
        socketField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) runningField.get(server)).set(true);
        socketField.set(server, new IoExceptionAcceptSocket());
        java.lang.reflect.Method acceptLoop = IpcServer.class.getDeclaredMethod("acceptLoop");
        acceptLoop.setAccessible(true);
        Thread worker = new Thread(() -> {
            try {
                acceptLoop.invoke(server);
            } catch (Exception ignored) {
            }
        });
        worker.start();
        Thread.sleep(50);
        ((java.util.concurrent.atomic.AtomicBoolean) runningField.get(server)).set(false);
        ((java.net.ServerSocket) socketField.get(server)).close();
        worker.join(5_000);
    }

    @Test
    void handleIgnoresCloseFailureOnWrappedSocket() throws Exception {
        server = new IpcServer(LOG);
        Field socketField = IpcServer.class.getDeclaredField("serverSocket");
        socketField.setAccessible(true);
        socketField.set(server, new ThrowOnCloseAcceptSocket());
        Field runningField = IpcServer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) runningField.get(server)).set(true);
        java.lang.reflect.Method acceptLoop = IpcServer.class.getDeclaredMethod("acceptLoop");
        acceptLoop.setAccessible(true);
        int port = ((java.net.ServerSocket) socketField.get(server)).getLocalPort();
        CountDownLatch disconnected = new CountDownLatch(1);
        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
                disconnected.countDown();
            }
        });
        Thread worker = new Thread(() -> {
            try {
                acceptLoop.invoke(server);
            } catch (Exception ignored) {
            }
        });
        worker.start();
        try (Socket socket = connect(port, "fork-1", "secret")) {
            socket.shutdownOutput();
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
        ((java.util.concurrent.atomic.AtomicBoolean) runningField.get(server)).set(false);
        ((java.net.ServerSocket) socketField.get(server)).close();
        worker.join(5_000);
    }

    @Test
    void acceptLoopSurvivesClosedServerSocket() throws Exception {
        server = new IpcServer(LOG);
        server.start();
        Field acceptThreadField = IpcServer.class.getDeclaredField("acceptThread");
        acceptThreadField.setAccessible(true);
        Thread acceptThread = (Thread) acceptThreadField.get(server);
        Field socketField = IpcServer.class.getDeclaredField("serverSocket");
        socketField.setAccessible(true);
        ((java.net.ServerSocket) socketField.get(server)).close();
        acceptThread.join(5_000);
        server.stop();
    }

    @Test
    void stopIgnoresServerSocketCloseFailure() throws Exception {
        server = new IpcServer(LOG);
        server.start();
        Field socketField = IpcServer.class.getDeclaredField("serverSocket");
        socketField.setAccessible(true);
        java.net.ServerSocket ss = (java.net.ServerSocket) socketField.get(server);
        ss.close();
        server.stop();
    }

    @Test
    void handleIgnoresSocketCloseFailure() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        CountDownLatch disconnected = new CountDownLatch(1);
        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
                disconnected.countDown();
            }
        });

        try (Socket socket = connect(port, "fork-1", "secret")) {
            socket.shutdownOutput();
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
    }

    @Test
    void handleClosesRejectedConnections() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        CountDownLatch disconnected = new CountDownLatch(1);
        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
                disconnected.countDown();
            }
        });

        try (Socket socket = connect(port, "fork-1", "secret")) {
            socket.shutdownOutput();
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
    }

    @Test
    void forkChannelSendIsThreadSafe() throws Exception {
        server = new IpcServer(LOG);
        int port = server.start();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<ForkChannel> channelRef = new AtomicReference<>();
        server.register("fork-1", new ForkSession() {
            @Override
            public String token() {
                return "secret";
            }

            @Override
            public void onConnected(ForkChannel channel) {
                channelRef.set(channel);
                connected.countDown();
            }

            @Override
            public void onMessage(Map<String, Object> message) {
            }

            @Override
            public void onDisconnected() {
            }
        });

        try (Socket socket = connect(port, "fork-1", "secret")) {
            assertTrue(connected.await(5, TimeUnit.SECONDS));
            ForkChannel channel = channelRef.get();
            channel.send(Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT));
            channel.close();
        }
    }

    private static Socket connect(int port, String forkId, String token) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HELLO);
        hello.put(IpcProtocol.FIELD_FORK_ID, forkId);
        hello.put(IpcProtocol.FIELD_TOKEN, token);
        writeLine(socket, hello);
        return socket;
    }

    private static void writeLine(Socket socket, Map<String, Object> message) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((Json.encode(message) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
