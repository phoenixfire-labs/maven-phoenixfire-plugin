package io.phoenixfire.api.spi;

import io.phoenixfire.api.model.FailureMode;

/**
 * Maps the observable signals of a dead fork (exit code, heartbeat status, handshake status,
 * diagnostic text) onto a {@link FailureMode}.
 *
 * <p>Pluggable via {@link java.util.ServiceLoader} so that platform-specific exit-code conventions
 * or custom OOM markers can be recognised.
 */
public interface FailureClassifier {

    /**
     * @param exitCode           the process exit code, or {@link Integer#MIN_VALUE} if unknown
     * @param heartbeatTimedOut  true if the controller killed the fork for missing heartbeats
     * @param handshakeCompleted true if the fork connected and authenticated before dying
     * @param diagnostic         optional captured diagnostic text (e.g. tail of stderr), may be null
     * @return the classified failure mode
     */
    FailureMode classify(int exitCode, boolean heartbeatTimedOut, boolean handshakeCompleted, String diagnostic);
}
