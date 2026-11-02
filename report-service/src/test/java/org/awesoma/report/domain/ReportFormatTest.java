package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportFormatTest {

    @Test
    void recognisesOnlyCsvAndPdf() {
        assertThat(ReportFormat.fromExtension("CSV")).contains(ReportFormat.CSV);
        assertThat(ReportFormat.fromExtension("pdf")).contains(ReportFormat.PDF);
        assertThat(ReportFormat.fromExtension("xlsx")).isEmpty();
        assertThat(ReportFormat.fromExtension("")).isEmpty();
        assertThat(ReportFormat.fromExtension(null)).isEmpty();
    }

    @Test
    void namesTheCanonicalContentTypes() {
        assertThat(ReportFormat.CSV.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(ReportFormat.PDF.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void buildsObjectKeysFromServerIdsOnly() {
        var report = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var attempt = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(ObjectKeys.upload(42, report))
                .isEqualTo("projects/42/reports/00000000-0000-0000-0000-000000000001");
        assertThat(ObjectKeys.attempt(42, report, attempt))
                .isEqualTo("projects/42/reports/00000000-0000-0000-0000-000000000001/attempts/"
                        + "00000000-0000-0000-0000-000000000002");
        assertThat(ObjectKeys.upload(42, report)).startsWith(ObjectKeys.projectPrefix(42));
    }
}
