package org.andresoviedo.util.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;

public class CallbackOutputStream extends OutputStream {

    private final OutputStream os;
    private final Function<Void, Exception> closeCallback;

    public CallbackOutputStream(OutputStream os, Function closeCallback) {
        this.os = os;
        this.closeCallback = closeCallback;
    }

    @Override
    public void write(int b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        os.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        os.flush();
    }

    @Override
    public void close() throws IOException {
        os.close();
        Exception apply = closeCallback.apply(null);
        if (apply != null) {
            throw new IOException(apply);
        }
    }
}
