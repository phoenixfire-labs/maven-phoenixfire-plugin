package io.phoenixfire.core.ipc;

import io.phoenixfire.api.ipc.IpcProtocol;
import io.phoenixfire.api.json.Json;
import io.phoenixfire.core.util.PhoenixfireLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller-side IPC server. Listens on the loopback interface; each connecting fork performs a
 * HELLO handshake identifying itself by fork id and one-time token. The server routes the
 * connection to the matching pre-registered {@link ForkSession} and pumps subsequent messages to it.
 *
 * <p>Using a dedicated socket channel (instead of parsing the fork's stdout) keeps control signals
 * immune to test code that writes to {@code System.out}/{@code System.err}, and lets the controller
 * cleanly distinguish a hang from a crash.
 */
public final class IpcServer implements AutoCloseable {

    /** Backoff after a transient accept failure so the accept loop does not spin and flood logs. */
    private static final long ACCEPT_RETRY_BACKOFF_MILLIS = 25L;

    private final PhoenixfireLogger log;
    private final ConcurrentHashMap<String, ForkSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public IpcServer(PhoenixfireLogger log) {
        this.log = log;
    }

    /** Bind to an ephemeral loopback port and start accepting connections. Returns the port. */
    public int start() throws IOException {
        serverSocket = new ServerSocket(0, 64, InetAddress.getLoopbackAddress());
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "phoenixfire-ipc-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return serverSocket.getLocalPort();
    }

    /** Register the session that will handle the fork identified by {@code forkId}. */
    public void register(String forkId, ForkSession session) {
        sessions.put(forkId, session);
    }

    public void unregister(String forkId) {
        sessions.remove(forkId);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                Thread handler = new Thread(() -> handle(socket), "phoenixfire-ipc-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (SocketException e) {
                if (running.get()) {
                    log.warn("IPC accept failed: " + e.getMessage());
                }
                return;
            } catch (IOException e) {
                if (running.get()) {
                    log.warn("IPC accept error: " + e.getMessage());
                    try {
                        Thread.sleep(ACCEPT_RETRY_BACKOFF_MILLIS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void handle(Socket socket) {
        ForkSession session = null;
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String helloLine = in.readLine();
            if (helloLine == null) {
                socket.close();
                return;
            }
            Map<String, Object> hello = Json.parseObject(helloLine);
            if (!IpcProtocol.MSG_HELLO.equals(String.valueOf(hello.get(IpcProtocol.FIELD_TYPE)))) {
                socket.close();
                return;
            }
            String forkId = String.valueOf(hello.get(IpcProtocol.FIELD_FORK_ID));
            String token = String.valueOf(hello.get(IpcProtocol.FIELD_TOKEN));

            session = sessions.get(forkId);
            if (session == null || !session.token().equals(token)) {
                log.warn("Rejected fork connection (unknown id or bad token): " + forkId);
                socket.close();
                return;
            }

            ForkChannel channel = new ForkChannel(socket);
            session.onConnected(channel);

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    session.onMessage(Json.parseObject(line));
                } catch (RuntimeException e) {
                    log.warn("Failed to handle fork message: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // Connection ended abruptly; treated as disconnect below.
        } finally {
            if (session != null) {
                try {
                    session.onDisconnected();
                } catch (RuntimeException e) {
                    log.warn("Error in onDisconnected: " + e.getMessage());
                }
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    public void stop() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}
