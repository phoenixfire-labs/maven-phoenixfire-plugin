package io.phoenixfire.core.ipc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Server socket whose {@link #accept()} always fails for coverage of accept error paths. */
final class IoExceptionAcceptSocket extends ServerSocket {

    IoExceptionAcceptSocket() throws IOException {
        super(0, 1, InetAddress.getLoopbackAddress());
    }

    @Override
    public Socket accept() throws IOException {
        throw new IOException("simulated accept failure");
    }
}
