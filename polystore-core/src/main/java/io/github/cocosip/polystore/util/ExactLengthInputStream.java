package io.github.cocosip.polystore.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Non-closing stream view that exposes exactly a declared number of bytes.
 *
 * <p>Reads after the declared length return end-of-stream without touching the underlying stream.
 * An underlying end-of-stream before the declared length raises {@link EOFException}.</p>
 */
public final class ExactLengthInputStream extends InputStream {
    private final InputStream delegate;
    private final long expectedLength;
    private long remaining;

    /** Creates a bounded, non-closing view over a caller-owned stream. */
    public ExactLengthInputStream(InputStream delegate, long length) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        this.expectedLength = length;
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (remaining == 0) {
            return -1;
        }
        int value = delegate.read();
        if (value < 0) {
            throw earlyEof();
        }
        remaining--;
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        if (remaining == 0) {
            return -1;
        }
        int requested = (int) Math.min(length, remaining);
        int count = delegate.read(buffer, offset, requested);
        if (count < 0) {
            throw earlyEof();
        }
        remaining -= count;
        return count;
    }

    @Override
    public long skip(long count) throws IOException {
        if (count <= 0 || remaining == 0) {
            return 0;
        }
        long skipped = delegate.skip(Math.min(count, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(remaining, delegate.available());
    }

    /** Verifies that the complete declared range has been consumed. */
    public void verifyComplete() throws EOFException {
        if (remaining != 0) {
            throw earlyEof();
        }
    }

    /** Returns the number of declared bytes not yet consumed. */
    public long getRemaining() {
        return remaining;
    }

    /** Does not close the caller-owned underlying stream. */
    @Override
    public void close() {
        // Caller owns the delegate.
    }

    private EOFException earlyEof() {
        return new EOFException(
                "Stream ended before the declared content length: expected " + expectedLength + " bytes");
    }
}
