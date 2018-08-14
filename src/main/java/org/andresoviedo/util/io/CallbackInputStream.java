package org.andresoviedo.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

public class CallbackInputStream extends InputStream {

    private final InputStream is;
    private final Function<Void, Exception> closeCallback;

    public CallbackInputStream(InputStream is, Function closeCallback) {
        this.is = is;
        this.closeCallback = closeCallback;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return is.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return is.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return is.skip(n);
    }

    @Override
    public int available() throws IOException {
        return is.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        is.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        is.reset();
    }

    @Override
    public boolean markSupported() {
        return is.markSupported();
    }

    @Override
    public int read() throws IOException {
        return is.read();
    }

    @Override
    public void close() throws IOException {
        is.close();
        Exception apply = closeCallback.apply(null);
        if (apply != null) {
            throw new IOException(apply);
        }
    }
}
