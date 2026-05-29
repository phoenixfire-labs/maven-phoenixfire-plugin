package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JUnit Platform listener that streams per-test lifecycle events back to the controller in real
 * time. Streaming (rather than reporting only at the end) is what lets the controller identify the
 * single in-flight test when a fork dies mid-execution.
 */
final class StreamingExecutionListener implements TestExecutionListener {

    private final ForkIpcClient client;
    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    StreamingExecutionListener(ForkIpcClient client) {
        this.client = client;
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!testIdentifier.isTest()) {
            return;
        }
        startTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
        Map<String, Object> msg = base(IpcProtocol.MSG_TEST_STARTED, testIdentifier);
        sendQuietly(msg);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) {
            return;
        }
        Map<String, Object> msg = base(IpcProtocol.MSG_TEST_FINISHED, testIdentifier);
        String status;
        switch (result.getStatus()) {
            case SUCCESSFUL:
                status = IpcProtocol.STATUS_PASSED;
                break;
            case ABORTED:
                // A JUnit "aborted" test (failed assumption) is treated as skipped.
                status = IpcProtocol.STATUS_SKIPPED;
                break;
            case FAILED:
            default:
                status = IpcProtocol.STATUS_FAILED;
                break;
        }
        msg.put(IpcProtocol.FIELD_STATUS, status);
        result.getThrowable().ifPresent(t -> {
            msg.put(IpcProtocol.FIELD_FAILURE_MESSAGE, String.valueOf(t.getMessage()));
            msg.put(IpcProtocol.FIELD_STACK_TRACE, stackTraceOf(t));
        });
        Long start = startTimes.remove(testIdentifier.getUniqueId());
        if (start != null) {
            msg.put(IpcProtocol.FIELD_DURATION_MILLIS, System.currentTimeMillis() - start);
        }
        sendQuietly(msg);
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (!testIdentifier.isTest()) {
            return;
        }
        Map<String, Object> msg = base(IpcProtocol.MSG_TEST_FINISHED, testIdentifier);
        msg.put(IpcProtocol.FIELD_STATUS, IpcProtocol.STATUS_SKIPPED);
        msg.put(IpcProtocol.FIELD_FAILURE_MESSAGE, reason);
        msg.put(IpcProtocol.FIELD_DURATION_MILLIS, 0L);
        sendQuietly(msg);
    }

    private Map<String, Object> base(String type, TestIdentifier id) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put(IpcProtocol.FIELD_TYPE, type);
        msg.put(IpcProtocol.FIELD_UNIQUE_ID, id.getUniqueId());
        msg.put(IpcProtocol.FIELD_DISPLAY_NAME, id.getDisplayName());
        msg.put(IpcProtocol.FIELD_CLASS_NAME, classNameOf(id));
        return msg;
    }

    static String classNameOf(TestIdentifier id) {
        if (id.getSource().isPresent()) {
            Object source = id.getSource().get();
            if (source instanceof MethodSource) {
                return ((MethodSource) source).getClassName();
            }
            if (source instanceof ClassSource) {
                return ((ClassSource) source).getClassName();
            }
        }
        return parseClassNameFromUniqueId(id.getUniqueId());
    }

    static String parseClassNameFromUniqueId(String uniqueId) {
        int classIdx = uniqueId.indexOf("[class:");
        if (classIdx >= 0) {
            int start = classIdx + "[class:".length();
            int end = uniqueId.indexOf(']', start);
            if (end > start) {
                return uniqueId.substring(start, end);
            }
        }
        return "UnknownClass";
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private void sendQuietly(Map<String, Object> msg) {
        try {
            client.send(msg);
        } catch (Exception e) {
            // If the controller connection is gone the fork will exit anyway.
        }
    }
}
