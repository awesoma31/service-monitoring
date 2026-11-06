package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FileNameTest {

    @Test
    void keepsOnlyTheLastPathSegment() {
        assertThat(FileName.fromClient("C:\\Users\\me\\uptime.csv").value()).isEqualTo("uptime.csv");
        assertThat(FileName.fromClient("../../etc/report.pdf").value()).isEqualTo("report.pdf");
    }

    @Test
    void acceptsUnicodeNames() {
        assertThat(FileName.fromClient("Отчёт за октябрь.pdf").value()).isEqualTo("Отчёт за октябрь.pdf");
    }

    @Test
    void readsTheExtensionCaseInsensitively() {
        assertThat(FileName.fromClient("Report.CSV").extension()).isEqualTo("csv");
        assertThat(FileName.fromClient("archive.tar.pdf").extension()).isEqualTo("pdf");
        assertThat(FileName.fromClient("README").extension()).isEmpty();
        assertThat(FileName.fromClient(".csv").extension()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "dir/", ".", "..", " report.csv", "report.csv ", "line\nbreak.csv",
            "bell\u0007.csv", "nul\u0000.pdf", "rtl\u202Efdp.csv", "sep\u2028.csv"})
    void rejectsUnsafeOrMissingNames(String raw) {
        assertThatThrownBy(() -> FileName.fromClient(raw))
                .isInstanceOf(InvalidReportRequestException.class)
                .extracting("field").isEqualTo("file");
    }

    @Test
    void rejectsOverlongNames() {
        String name = "a".repeat(FileName.MAX_LENGTH - 3) + ".csv";

        assertThatThrownBy(() -> FileName.fromClient(name)).isInstanceOf(InvalidReportRequestException.class);
        assertThat(FileName.fromClient(name.substring(1)).value()).hasSize(FileName.MAX_LENGTH);
    }
}
