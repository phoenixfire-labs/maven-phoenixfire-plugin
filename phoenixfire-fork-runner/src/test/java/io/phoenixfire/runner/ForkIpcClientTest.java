package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.json.Json;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkIpcClientTest {

    @Test
    void helloHandshakeAndMessaging() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            CountDownLatch hello = new CountDownLatch(1);
            Thread accept = new Thread(() -> acceptHello(serverSocket, hello), "accept");
            accept.setDaemon(true);
            accept.start();

            try (ForkIpcClient client = new ForkIpcClient(port, "fork-a", "tok")) {
                assertTrue(hello.await(5, TimeUnit.SECONDS));
                client.send(Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT));
            }
        }
    }

    @Test
    void readMessageParsesNdjson() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            Thread accept = new Thread(() -> acceptAndSendCommand(serverSocket), "accept");
            accept.setDaemon(true);
            accept.start();

            try (ForkIpcClient client = new ForkIpcClient(port, "f", "t")) {
                Map<String, Object> cmd = client.readMessage();
                assertNotNull(cmd);
                assertEquals(IpcProtocol.MSG_EXECUTE, cmd.get(IpcProtocol.FIELD_TYPE));
            }
        }
    }

    private static void acceptHello(ServerSocket serverSocket, CountDownLatch hello) {
        try (Socket socket = serverSocket.accept()) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Map<String, Object> helloMsg = Json.parseObject(in.readLine());
            assertEquals(IpcProtocol.MSG_HELLO, helloMsg.get(IpcProtocol.FIELD_TYPE));
            hello.countDown();
            Thread.sleep(100);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void acceptAndSendCommand(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            in.readLine();
            Map<String, Object> cmd = new LinkedHashMap<>();
            cmd.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_EXECUTE);
            cmd.put(IpcProtocol.FIELD_TEST_IDS, List.of("id1"));
            writeLine(socket, cmd);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeLine(Socket socket, Map<String, Object> message) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((Json.encode(message) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
