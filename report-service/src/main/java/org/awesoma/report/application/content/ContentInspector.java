package org.awesoma.report.application.content;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.awesoma.report.domain.ReportFormat;

/**
 * Checks the content while it streams: a CSV must decode as UTF-8, a PDF must start with
 * the PDF signature. The signature is a basic check of the format, not a malware scan and
 * not proof that the whole document is valid. {@link #verifyComplete()} must be called once
 * the transfer ends, because the stream may not have been read up to its end.
 */
public abstract class ContentInspector extends FilterInputStream {

    private ContentInspector(InputStream in) {
        super(in);
    }

    public static ContentInspector of(InputStream in, ReportFormat format) {
        return format == ReportFormat.CSV ? new Utf8Text(in) : new PdfSignature(in);
    }

    /** @throws InvalidContentException when the content read so far is empty or incomplete */
    public abstract void verifyComplete();

    protected abstract void inspect(byte[] bytes, int offset, int length);

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) inspect(new byte[] {(byte) b}, 0, 1);
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) inspect(buffer, offset, read);
        return read;
    }

    @Override
    public long skip(long n) {
        return 0;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private static final class Utf8Text extends ContentInspector {
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        private final CharBuffer chars = CharBuffer.allocate(8192);
        /** Bytes of a character split between two reads, at most three. */
        private ByteBuffer pending = ByteBuffer.allocate(0);
        private long total;

        Utf8Text(InputStream in) {
            super(in);
        }

        @Override
        protected void inspect(byte[] bytes, int offset, int length) {
            total += length;
            ByteBuffer input = ByteBuffer.allocate(pending.remaining() + length);
            input.put(pending).put(bytes, offset, length).flip();
            decode(input, false);
            pending = input.slice();
        }

        @Override
        public void verifyComplete() {
            if (total == 0) throw new InvalidContentException("File is empty");
            decode(pending, true);
            chars.clear();
            if (decoder.flush(chars).isError()) throw notUtf8();
        }

        private void decode(ByteBuffer input, boolean endOfInput) {
            while (true) {
                chars.clear();
                CoderResult result = decoder.decode(input, chars, endOfInput);
                if (result.isError()) throw notUtf8();
                if (!result.isOverflow()) return;
            }
        }

        private static InvalidContentException notUtf8() {
            return new InvalidContentException("CSV file is not valid UTF-8 text");
        }
    }

    private static final class PdfSignature extends ContentInspector {
        private static final byte[] SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        private final byte[] head = new byte[SIGNATURE.length];
        private int seen;

        PdfSignature(InputStream in) {
            super(in);
        }

        @Override
        protected void inspect(byte[] bytes, int offset, int length) {
            if (seen >= head.length) return;
            int copy = Math.min(length, head.length - seen);
            System.arraycopy(bytes, offset, head, seen, copy);
            seen += copy;
            if (seen == head.length && !Arrays.equals(head, SIGNATURE)) throw notPdf();
        }

        @Override
        public void verifyComplete() {
            if (seen == 0) throw new InvalidContentException("File is empty");
            if (seen < head.length) throw notPdf();
        }

        private static InvalidContentException notPdf() {
            return new InvalidContentException("File does not start with the PDF signature");
        }
    }
}
