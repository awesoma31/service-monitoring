package org.awesoma.report.adapters.out.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.content.CheckStats;
import org.awesoma.report.domain.content.IncidentStats;
import org.awesoma.report.domain.content.MonitorRow;
import org.awesoma.report.domain.content.ReportContent;
import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;
import org.junit.jupiter.api.Test;

class ReportRenderersTest {
    private static final ReportPeriod PERIOD = new ReportPeriod(Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-10-02T00:00:00Z"));
    private static final Instant REQUESTED = Instant.parse("2026-11-02T12:00:00Z");
    private static final Instant GENERATED = Instant.parse("2026-11-02T12:00:05.250Z");
    private static final CheckStats MEASURED = new CheckStats(6, 3, 1, 1, 1, new BigDecimal("50.00"), 0L,
            new BigDecimal("100.25"), 251L);
    private static final CheckStats EMPTY = new CheckStats(0, 0, 0, 0, 0, null, null, null, null);
    private static final IncidentStats INCIDENTS = new IncidentStats(4, 1, 1, 1, 1, 2);

    @Test
    void csvHasTheFixedHeaderAndOneRowPerMonitor() throws Exception {
        String csv = csv(content(List.of(
                new MonitorRow(new MonitorInfo(11, "Сайт, главный", "https://example.com/?q=\"x\""), MEASURED,
                        INCIDENTS),
                new MonitorRow(new MonitorInfo(12, "=HYPERLINK(\"evil\")", "-1+2"), EMPTY,
                        new IncidentStats(0, 0, 0, 0, 0, 0)))));

        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo(CsvReportRenderer.HEADER);
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).isEqualTo("42,Проект,11,\"Сайт, главный\",\"https://example.com/?q=\"\"x\"\"\","
                + "2026-10-01T00:00:00Z,2026-10-02T00:00:00Z,2026-11-02T12:00:00Z,2026-11-02T12:00:05.250Z,"
                + "6,3,1,1,1,50.00,0,100.25,251,4,1,1,1,1,2");
        assertThat(lines[2]).startsWith("42,Проект,12,\"'=HYPERLINK(\"\"evil\"\")\",'-1+2,")
                .endsWith(",0,0,0,0,0,,,,,0,0,0,0,0,0");
    }

    @Test
    void csvQuotesLineBreaksAndKeepsNumbersPlain() {
        assertThat(CsvReportRenderer.text("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(CsvReportRenderer.text(" padded")).isEqualTo("\" padded\"");
        assertThat(CsvReportRenderer.text("@mention")).isEqualTo("'@mention");
        assertThat(CsvReportRenderer.text("+")).isEqualTo("'+");
        assertThat(CsvReportRenderer.text("")).isEmpty();
        assertThat(CsvReportRenderer.text(null)).isEmpty();
    }

    @Test
    void pdfShowsTheSameMetricsInCyrillicOnSeveralPagesWithTheHeaderRepeated() throws Exception {
        List<MonitorRow> rows = new ArrayList<>();
        rows.add(new MonitorRow(new MonitorInfo(11, "Главный сайт ✓\u0007",
                "https://example.com/" + "очень-длинный-путь/".repeat(12)), MEASURED, INCIDENTS));
        for (int i = 0; i < 80; i++) {
            rows.add(new MonitorRow(new MonitorInfo(100 + i, "Монитор " + i, "https://m" + i + ".example.com"),
                    EMPTY, new IncidentStats(0, 0, 0, 0, 0, 0)));
        }
        var out = new ByteArrayOutputStream();
        new PdfReportRenderer().render(content(rows), out);

        try (var document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            var stripper = new PDFTextStripper();
            String all = stripper.getText(document);
            assertThat(all).contains("Отчёт о мониторинге", "Проект (ID 42)", "Главный сайт", "100.25", "50.00",
                    "N/A", "Монитор 79", "2026-10-01T00:00:00Z");
            stripper.setStartPage(document.getNumberOfPages());
            stripper.setEndPage(document.getNumberOfPages());
            assertThat(stripper.getText(document)).as("header repeated on the last page")
                    .contains("Открыто", "SUCCESS", "CRITICAL", "TIMEOUT", "Проверок")
                    .doesNotContain("SUCCES\nS");
            var font = document.getPage(0).getResources().getFont(
                    document.getPage(0).getResources().getFontNames().iterator().next());
            assertThat(font).isInstanceOf(PDType0Font.class);
            assertThat(font.isEmbedded()).isTrue();
        }
    }

    private static ReportContent content(List<MonitorRow> rows) {
        return new ReportContent(new ProjectInfo(42, "Проект"), PERIOD, REQUESTED, GENERATED, rows);
    }

    private static String csv(ReportContent content) throws Exception {
        var out = new ByteArrayOutputStream();
        new CsvReportRenderer().render(content, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
