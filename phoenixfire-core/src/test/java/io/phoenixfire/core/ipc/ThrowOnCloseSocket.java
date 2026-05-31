package io.phoenixfire.core.ipc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/** Delegating socket whose {@link #close()} always fails. */
final class ThrowOnCloseSocket extends Socket {

    private final Socket delegate;

    ThrowOnCloseSocket(Socket delegate) {
        this.delegate = delegate;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        throw new IOException("simulated close failure");
    }
}
