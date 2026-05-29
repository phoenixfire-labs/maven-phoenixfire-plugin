package io.phoenixfire.core.ipc;

import java.util.Map;

/**
 * Callback interface implemented by the supervisor for a single fork's lifecycle. The IPC server
 * invokes these methods from the connection-handling thread.
 */
public interface ForkSession {

    /** Authentication token expected from the fork's HELLO message. */
    String token();

    /** Invoked once the fork connects and successfully authenticates. */
    void onConnected(ForkChannel channel);

    /** Invoked for each inbound message after the HELLO handshake. */
    void onMessage(Map<String, Object> message);

    /** Invoked exactly once when the connection ends (clean or abrupt). */
    void onDisconnected();
}
