package org.awesoma.report.application.content;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Refuses to write past the limit, so a rendered report can never outgrow the file limit. */
public class LimitedOutputStream extends FilterOutputStream {
    private final long limit;
    private long written;

    public LimitedOutputStream(OutputStream out, long limit) {
        super(out);
        this.limit = limit;
    }

    @Override
    public void write(int b) throws IOException {
        count(1);
        out.write(b);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        count(length);
        out.write(bytes, offset, length);
    }

    private void count(int length) {
        written += length;
        if (written > limit) throw new ContentTooLargeException(limit);
    }
}
