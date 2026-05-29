package io.phoenixfire.core.supervisor;

import io.phoenixfire.api.model.FailureMode;
import io.phoenixfire.api.spi.FailureClassifier;

/**
 * Default mapping of fork death signals to {@link FailureMode}. Handles the common POSIX
 * 128+signal exit codes (e.g. 137 = SIGKILL, 143 = SIGTERM) and scans diagnostics for OOM markers.
 */
public final class DefaultFailureClassifier implements FailureClassifier {

    public static final int UNKNOWN_EXIT = Integer.MIN_VALUE;

    @Override
    public FailureMode classify(int exitCode, boolean heartbeatTimedOut, boolean handshakeCompleted, String diagnostic) {
        if (heartbeatTimedOut) {
            return FailureMode.HEARTBEAT_TIMEOUT;
        }
        if (!handshakeCompleted) {
            return FailureMode.HANDSHAKE_FAILURE;
        }
        if (diagnostic != null) {
            String d = diagnostic.toLowerCase();
            if (d.contains("outofmemoryerror") || d.contains("out of memory")
                    || d.contains("gc overhead limit")) {
                return FailureMode.OOM;
            }
        }
        switch (exitCode) {
            case 0:
                return FailureMode.NONE;
            case 137: // 128 + 9 (SIGKILL), also typical Linux OOM-killer code
                return FailureMode.SIGKILL;
            case 143: // 128 + 15 (SIGTERM)
                return FailureMode.SIGTERM;
            default:
                if (exitCode == UNKNOWN_EXIT) {
                    return FailureMode.UNKNOWN;
                }
                return FailureMode.ABNORMAL_EXIT;
        }
    }
}
