package io.phoenixfire.api.ipc;

/**
 * Shared constants for the controller/fork IPC protocol.
 *
 * <p>Messages are newline-delimited JSON objects (NDJSON) exchanged over a loopback TCP socket. The
 * controller is the server; each fork connects, authenticates with a one-time token, receives a
 * command, then streams events and heartbeats until it sends {@link #MSG_BYE} or the connection
 * drops.
 */
public final class IpcProtocol {

    private IpcProtocol() {
    }

    // System properties passed to each fork.
    public static final String PROP_PORT = "phoenixfire.controller.port";
    public static final String PROP_TOKEN = "phoenixfire.fork.token";
    public static final String PROP_FORK_ID = "phoenixfire.fork.id";
    /** Path-separated list of classpath roots (test output directories) to scan during discovery. */
    public static final String PROP_SCAN_ROOTS = "phoenixfire.scan.roots";

    // Common message envelope field.
    public static final String FIELD_TYPE = "type";

    // Fork -> controller.
    public static final String MSG_HELLO = "HELLO";
    public static final String MSG_DISCOVERED = "DISCOVERED";
    public static final String MSG_TEST_STARTED = "TEST_STARTED";
    public static final String MSG_TEST_FINISHED = "TEST_FINISHED";
    public static final String MSG_HEARTBEAT = "HEARTBEAT";
    public static final String MSG_LOG = "LOG";
    public static final String MSG_BYE = "BYE";

    // Controller -> fork.
    public static final String MSG_DISCOVER = "DISCOVER";
    public static final String MSG_EXECUTE = "EXECUTE";

    // HELLO fields.
    public static final String FIELD_FORK_ID = "forkId";
    public static final String FIELD_TOKEN = "token";

    // DISCOVER / EXECUTE fields.
    public static final String FIELD_INCLUDES = "includes";
    public static final String FIELD_EXCLUDES = "excludes";
    public static final String FIELD_TEST_IDS = "testIds";

    // DISCOVERED / TEST_* fields.
    public static final String FIELD_UNIQUE_ID = "uniqueId";
    public static final String FIELD_CLASS_NAME = "className";
    public static final String FIELD_DISPLAY_NAME = "displayName";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_FAILURE_MESSAGE = "failureMessage";
    public static final String FIELD_STACK_TRACE = "stackTrace";
    public static final String FIELD_DURATION_MILLIS = "durationMillis";
    public static final String FIELD_MESSAGE = "message";

    // TEST_FINISHED status values.
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
}
