package org.awesoma.report.application.content;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.awesoma.report.domain.ReportFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContentChecksTest {

    @Test
    void acceptsUtf8SplitAcrossReadsAndAByteOrderMark() throws IOException {
        byte[] text = "﻿монитор,доля\nсайт,99.5\n".getBytes(StandardCharsets.UTF_8);
        var inspector = ContentInspector.of(new ByteArrayInputStream(text), ReportFormat.CSV);

        byte[] buffer = new byte[3];
        while (inspector.read(buffer, 0, buffer.length) > 0) {
            // A three-byte buffer cuts two-byte Cyrillic letters in half.
        }

        assertThatCode(inspector::verifyComplete).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidAndTruncatedUtf8() {
        assertThatThrownBy(() -> readAll(new byte[] {'a', (byte) 0xFF}, ReportFormat.CSV))
                .isInstanceOf(InvalidContentException.class);

        var truncated = ContentInspector.of(new ByteArrayInputStream(new byte[] {'a', (byte) 0xD0}), ReportFormat.CSV);
        assertThatCode(() -> truncated.readAllBytes()).doesNotThrowAnyException();
        assertThatThrownBy(truncated::verifyComplete).isInstanceOf(InvalidContentException.class);
    }

    @Test
    void checksThePdfSignature() throws IOException {
        var pdf = ContentInspector.of(new ByteArrayInputStream("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII)),
                ReportFormat.PDF);
        while (pdf.read() >= 0) {
            // Byte by byte, the signature is still assembled.
        }
        assertThatCode(pdf::verifyComplete).doesNotThrowAnyException();

        assertThatThrownBy(() -> readAll("%PDX-1.4".getBytes(StandardCharsets.US_ASCII), ReportFormat.PDF))
                .isInstanceOf(InvalidContentException.class);
        var tooShort = ContentInspector.of(new ByteArrayInputStream("%PD".getBytes(StandardCharsets.US_ASCII)),
                ReportFormat.PDF);
        tooShort.readAllBytes();
        assertThatThrownBy(tooShort::verifyComplete).isInstanceOf(InvalidContentException.class);
    }

    @Test
    void anEmptyFileIsRejectedInEitherFormat() throws IOException {
        for (ReportFormat format : ReportFormat.values()) {
            var empty = ContentInspector.of(InputStream.nullInputStream(), format);
            empty.readAllBytes();
            assertThatThrownBy(empty::verifyComplete).hasMessage("File is empty");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"application/octet-stream", "text/csv", "TEXT/CSV", "text/csv; charset=UTF-8",
        "text/csv;charset=\"utf-8\"", "text/csv; header=present"})
    void acceptsDeclaredTypesThatDoNotContradictACsv(String declared) {
        assertThatCode(() -> DeclaredContentType.requireCompatible(declared, ReportFormat.CSV))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "text/plain", "text/csv; charset=windows-1251", "text/csv; charset"})
    void rejectsDeclaredTypesThatContradictACsv(String declared) {
        assertThatThrownBy(() -> DeclaredContentType.requireCompatible(declared, ReportFormat.CSV))
                .isInstanceOf(UnsupportedContentTypeException.class);
    }

    @Test
    void aPdfTakesNoCharset() {
        assertThatCode(() -> DeclaredContentType.requireCompatible("Application/PDF", ReportFormat.PDF))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DeclaredContentType.requireCompatible("application/pdf; charset=UTF-8",
                ReportFormat.PDF)).isInstanceOf(UnsupportedContentTypeException.class);
        assertThatThrownBy(() -> DeclaredContentType.requireCompatible("text/csv", ReportFormat.PDF))
                .isInstanceOf(UnsupportedContentTypeException.class);
    }

    private static void readAll(byte[] content, ReportFormat format) throws IOException {
        var inspector = ContentInspector.of(new ByteArrayInputStream(content), format);
        inspector.readAllBytes();
        inspector.verifyComplete();
    }
}
