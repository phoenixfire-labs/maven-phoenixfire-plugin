package io.phoenixfire.core.testsupport;

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
import java.util.List;
import java.util.Map;

/**
 * Test harness entry point that mimics {@code ForkRunnerMain} over loopback IPC without JUnit discovery.
 */
public final class SimulatedFork {

    public static final String PROP_MODE = "phoenixfire.sim.mode";
    public static final String MODE_DISCOVER = "discover";
    public static final String MODE_EXECUTE_PASS = "execute-pass";
    public static final String MODE_EXECUTE_FAIL = "execute-fail";
    public static final String MODE_BAD_COMMAND = "bad-command";
    public static final String MODE_NO_BYE = "no-bye";
    public static final String MODE_HANG = "hang";

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty(IpcProtocol.PROP_PORT));
        String token = System.getProperty(IpcProtocol.PROP_TOKEN, "");
        String forkId = System.getProperty(IpcProtocol.PROP_FORK_ID, "sim");
        String mode = System.getProperty(PROP_MODE, MODE_DISCOVER);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            send(out, hello(forkId, token));
            Map<String, Object> command = Json.parseObject(in.readLine());
            String type = String.valueOf(command.get(IpcProtocol.FIELD_TYPE));

            if (MODE_HANG.equals(mode)) {
                Thread.sleep(60_000);
                return;
            }

            if (IpcProtocol.MSG_DISCOVER.equals(type)) {
                send(out, discovered("uid-1", "sim.FooTest", "testOne()"));
                send(out, discovered("uid-2", "sim.BarTest", "testTwo()"));
            } else if (IpcProtocol.MSG_EXECUTE.equals(type)) {
                @SuppressWarnings("unchecked")
                List<Object> ids = (List<Object>) command.get(IpcProtocol.FIELD_TEST_IDS);
                for (Object raw : ids) {
                    String uid = raw.toString();
                    send(out, testStarted(uid));
                    if (MODE_EXECUTE_FAIL.equals(mode)) {
                        send(out, testFinished(uid, IpcProtocol.STATUS_FAILED, "boom", "stack"));
                    } else {
                        send(out, testFinished(uid, IpcProtocol.STATUS_PASSED, null, null));
                    }
                }
            } else if (MODE_BAD_COMMAND.equals(mode)) {
                // ignore command
            }

            if (!MODE_NO_BYE.equals(mode)) {
                send(out, Map.of(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_BYE));
            }
        }
        System.exit(0);
    }

    private static Map<String, Object> hello(String forkId, String token) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HELLO);
        m.put(IpcProtocol.FIELD_FORK_ID, forkId);
        m.put(IpcProtocol.FIELD_TOKEN, token);
        return m;
    }

    private static Map<String, Object> discovered(String uid, String className, String display) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_DISCOVERED);
        m.put(IpcProtocol.FIELD_UNIQUE_ID, uid);
        m.put(IpcProtocol.FIELD_CLASS_NAME, className);
        m.put(IpcProtocol.FIELD_DISPLAY_NAME, display);
        return m;
    }

    private static Map<String, Object> testStarted(String uid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_TEST_STARTED);
        m.put(IpcProtocol.FIELD_UNIQUE_ID, uid);
        m.put(IpcProtocol.FIELD_CLASS_NAME, "sim.FooTest");
        m.put(IpcProtocol.FIELD_DISPLAY_NAME, "test()");
        return m;
    }

    private static Map<String, Object> testFinished(String uid, String status, String msg, String stack) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_TEST_FINISHED);
        m.put(IpcProtocol.FIELD_UNIQUE_ID, uid);
        m.put(IpcProtocol.FIELD_CLASS_NAME, "sim.FooTest");
        m.put(IpcProtocol.FIELD_DISPLAY_NAME, "test()");
        m.put(IpcProtocol.FIELD_STATUS, status);
        m.put(IpcProtocol.FIELD_DURATION_MILLIS, 1L);
        if (msg != null) {
            m.put(IpcProtocol.FIELD_FAILURE_MESSAGE, msg);
            m.put(IpcProtocol.FIELD_STACK_TRACE, stack);
        }
        return m;
    }

    private static void send(OutputStream out, Map<String, Object> message) throws IOException {
        out.write((Json.encode(message) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private SimulatedFork() {
    }
}
