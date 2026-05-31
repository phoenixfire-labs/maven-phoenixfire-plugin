package io.phoenixfire.core.ipc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Accepts connections and returns sockets whose {@link #close()} fails. */
final class ThrowOnCloseAcceptSocket extends ServerSocket {

    ThrowOnCloseAcceptSocket() throws IOException {
        super(0, 1, InetAddress.getLoopbackAddress());
    }

    @Override
    public Socket accept() throws IOException {
        return new ThrowOnCloseSocket(super.accept());
    }
}
