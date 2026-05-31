package io.phoenixfire.core.ipc;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ForkChannelTest {

    @Test
    void closeIgnoresIOException() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread accept = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    ForkChannel channel = new ForkChannel(new ThrowOnCloseSocket(socket));
                    assertDoesNotThrow(channel::close);
                } catch (Exception ignored) {
                }
            });
            accept.start();
            try (Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                accept.join(5_000);
            }
        }
    }
}
