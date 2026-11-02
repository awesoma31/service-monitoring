package org.awesoma.report.application.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class MeasuredInputStreamTest {
    private static final byte[] CONTENT = "monitor_id,total\n11,42\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void countsAndHashesTheBytesThatPassThrough() throws Exception {
        var stream = new MeasuredInputStream(new ByteArrayInputStream(CONTENT), CONTENT.length);

        assertThat(stream.read()).isEqualTo('m');
        byte[] rest = stream.readAllBytes();

        assertThat(rest).hasSize(CONTENT.length - 1);
        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.size()).isEqualTo(CONTENT.length);
        assertThat(stream.sha256()).isEqualTo(sha256(CONTENT));
    }

    @Test
    void hashOfNothingIsTheEmptyHash() throws Exception {
        var stream = new MeasuredInputStream(new ByteArrayInputStream(new byte[0]), 10);

        assertThat(stream.readAllBytes()).isEmpty();
        assertThat(stream.size()).isZero();
        assertThat(stream.sha256()).isEqualTo(sha256(new byte[0]));
    }

    @Test
    void stopsOnceTheLimitIsPassed() {
        var stream = new MeasuredInputStream(new ByteArrayInputStream(CONTENT), CONTENT.length - 1);

        assertThatThrownBy(stream::readAllBytes)
                .isInstanceOf(ContentTooLargeException.class)
                .extracting("limit").isEqualTo((long) CONTENT.length - 1);
    }

    @Test
    void neitherSkipsNorRereadsBytes() throws IOException {
        var stream = new MeasuredInputStream(new ByteArrayInputStream(CONTENT), CONTENT.length);

        assertThat(stream.skip(5)).isZero();
        assertThat(stream.markSupported()).isFalse();
        stream.mark(10);
        assertThatThrownBy(stream::reset).isInstanceOf(IOException.class);
        assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
