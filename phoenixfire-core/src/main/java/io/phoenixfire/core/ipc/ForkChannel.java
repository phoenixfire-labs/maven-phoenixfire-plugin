package io.phoenixfire.core.ipc;

import io.phoenixfire.api.json.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controller-side handle to one connected fork. Thread-safe for sending NDJSON messages.
 */
public final class ForkChannel {

    private final Socket socket;
    private final OutputStream out;
    private final Object writeLock = new Object();

    ForkChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.out = socket.getOutputStream();
    }

    public void send(Map<String, Object> message) throws IOException {
        byte[] bytes = (Json.encode(message) + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            out.write(bytes);
            out.flush();
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
