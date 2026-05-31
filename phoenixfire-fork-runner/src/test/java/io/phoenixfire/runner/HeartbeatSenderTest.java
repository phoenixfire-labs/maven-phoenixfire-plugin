package io.phoenixfire.runner;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class HeartbeatSenderTest {

    @Test
    void sendsHeartbeatsUntilClosed() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread accept = new Thread(() -> {
                try (Socket s = server.accept()) {
                    Thread.sleep(800);
                } catch (Exception ignored) {
                }
            }, "accept");
            accept.setDaemon(true);
            accept.start();

            try (ForkIpcClient client = new ForkIpcClient(port, "f", "t")) {
                try (HeartbeatSender sender = new HeartbeatSender(client, 250L)) {
                    sender.start();
                    Thread.sleep(600);
                }
            }
        }
    }

    @Test
    void closeWithoutStartIsSafe() {
        assertDoesNotThrow(() -> {
            try (HeartbeatSender sender = new HeartbeatSender(null, 250L)) {
                sender.close();
            }
        });
    }

    @Test
    void stopsWhenSendFails() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread accept = new Thread(() -> {
                try (Socket s = server.accept()) {
                    Thread.sleep(50);
                } catch (Exception ignored) {
                }
            }, "accept");
            accept.setDaemon(true);
            accept.start();

            try (ForkIpcClient client = new ForkIpcClient(port, "f", "t")) {
                try (HeartbeatSender sender = new HeartbeatSender(client, 250L)) {
                    sender.start();
                    Thread.sleep(900);
                }
            }
        }
    }

    @Test
    void loopExitsWhenRunningBecomesFalse() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread accept = new Thread(() -> {
                try (java.net.Socket s = server.accept()) {
                    Thread.sleep(2_000);
                } catch (Exception ignored) {
                }
            }, "accept");
            accept.setDaemon(true);
            accept.start();

            try (ForkIpcClient client = new ForkIpcClient(port, "f", "t")) {
                HeartbeatSender sender = new HeartbeatSender(client, 100L);
                sender.start();
                Field running = HeartbeatSender.class.getDeclaredField("running");
                running.setAccessible(true);
                Thread.sleep(150);
                running.setBoolean(sender, false);
                Thread.sleep(250);
                sender.close();
            }
        }
    }
}
