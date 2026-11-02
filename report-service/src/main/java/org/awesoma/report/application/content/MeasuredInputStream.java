package org.awesoma.report.application.content;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Counts and hashes the bytes that actually pass through, as they are streamed to storage,
 * and stops at the limit whatever length the sender declared. The SHA-256 is computed here
 * because an object's ETag is not a hash of its content.
 */
public class MeasuredInputStream extends FilterInputStream {
    private final long limit;
    private final MessageDigest digest;
    private long size;

    public MeasuredInputStream(InputStream in, long limit) {
        super(in);
        this.limit = limit;
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", error);
        }
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            count(1);
            digest.update((byte) b);
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) {
            count(read);
            digest.update(buffer, offset, read);
        }
        return read;
    }

    /** Skipped bytes would be stored but not hashed, so skipping is not supported. */
    @Override
    public long skip(long n) {
        return 0;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readLimit) {
        // Not supported: a reset would hash the same bytes twice.
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    public long size() {
        return size;
    }

    /** The hash of everything read so far; call it once the stream is exhausted. */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(((MessageDigest) digest.clone()).digest());
        } catch (CloneNotSupportedException error) {
            throw new IllegalStateException("SHA-256 digest cannot be copied", error);
        }
    }

    private void count(int read) {
        size += read;
        if (size > limit) {
            throw new ContentTooLargeException(limit);
        }
    }
}
