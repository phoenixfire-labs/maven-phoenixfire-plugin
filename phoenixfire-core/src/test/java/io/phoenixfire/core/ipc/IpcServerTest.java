package io.phoenixfire.core.ipc;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.json.Json;
import io.phoenixfire.core.util.PhoenixfireLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpcServerTest {

    private IpcServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void acceptsHelloAndRoutesMessages() throws Exception {
        server = new IpcServer(PhoenixfireLogger.console());
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
            sendLine(socket, Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT));
        }
        assertTrue(disconnected.await(5, TimeUnit.SECONDS));
    }

    @Test
    void rejectsBadTokenAndMalformedHello() throws Exception {
        server = new IpcServer(PhoenixfireLogger.console());
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
            sendLine(socket, Map.of(IpcProtocol.FIELD_TYPE, "NOT_HELLO"));
        }
    }

    private static Socket connect(int port, String forkId, String token) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HELLO);
        hello.put(IpcProtocol.FIELD_FORK_ID, forkId);
        hello.put(IpcProtocol.FIELD_TOKEN, token);
        sendLine(socket, hello);
        return socket;
    }

    private static void sendLine(Socket socket, Map<String, Object> message) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((Json.encode(message) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        in.readLine();
    }
}
