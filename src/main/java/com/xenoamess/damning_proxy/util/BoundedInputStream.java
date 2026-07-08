package com.xenoamess.damning_proxy.util;

import java.io.IOException;
import java.io.InputStream;

public class BoundedInputStream extends InputStream {

    private final InputStream in;
    private final long maxBytes;
    private long readBytes;

    public BoundedInputStream(InputStream in, long maxBytes) {
        if (in == null) {
            throw new IllegalArgumentException("Input stream is required");
        }
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.in = in;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b >= 0) {
            readBytes++;
            if (readBytes > maxBytes) {
                throw new IOException("Input stream exceeded maximum allowed size of " + maxBytes + " bytes");
            }
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = in.read(b, off, len);
        if (read > 0) {
            readBytes += read;
            if (readBytes > maxBytes) {
                throw new IOException("Input stream exceeded maximum allowed size of " + maxBytes + " bytes");
            }
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
