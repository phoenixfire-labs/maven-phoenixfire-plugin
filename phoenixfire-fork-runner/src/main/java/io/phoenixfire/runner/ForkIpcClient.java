package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.json.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fork-side IPC client. Connects to the controller on the loopback interface, performs the HELLO
 * handshake, and provides thread-safe sending of NDJSON messages plus blocking reads of inbound
 * commands.
 */
final class ForkIpcClient implements AutoCloseable {

    private final Socket socket;
    private final OutputStream out;
    private final BufferedReader in;
    private final Object writeLock = new Object();

    ForkIpcClient(int port, String forkId, String token) throws IOException {
        this.socket = new Socket(InetAddress.getLoopbackAddress(), port);
        this.socket.setTcpNoDelay(true);
        this.out = socket.getOutputStream();
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HELLO);
        hello.put(IpcProtocol.FIELD_FORK_ID, forkId);
        hello.put(IpcProtocol.FIELD_TOKEN, token);
        send(hello);
    }

    void send(Map<String, Object> message) throws IOException {
        String line = Json.encode(message) + "\n";
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            out.write(bytes);
            out.flush();
        }
    }

    /** Block until the controller sends a command (or the connection closes). */
    Map<String, Object> readMessage() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        return Json.parseObject(line);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
