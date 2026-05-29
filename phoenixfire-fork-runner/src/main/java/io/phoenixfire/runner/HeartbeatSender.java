package io.phoenixfire.runner;

import io.phoenixfire.api.ipc.IpcProtocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Background daemon thread that emits periodic HEARTBEAT messages so the controller can distinguish
 * a live-but-busy fork from a deadlocked/hung one.
 */
final class HeartbeatSender implements AutoCloseable {

    private final ForkIpcClient client;
    private final long intervalMillis;
    private volatile boolean running = true;
    private Thread thread;

    HeartbeatSender(ForkIpcClient client, long intervalMillis) {
        this.client = client;
        this.intervalMillis = Math.max(250L, intervalMillis);
    }

    void start() {
        thread = new Thread(this::loop, "phoenixfire-heartbeat");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        Map<String, Object> heartbeat = new LinkedHashMap<>();
        heartbeat.put(IpcProtocol.FIELD_TYPE, IpcProtocol.MSG_HEARTBEAT);
        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                client.send(heartbeat);
            } catch (Exception e) {
                // Controller is gone; nothing useful we can do from the fork.
                return;
            }
        }
    }

    @Override
    public void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
