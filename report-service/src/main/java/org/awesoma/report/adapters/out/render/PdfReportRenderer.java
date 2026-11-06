package org.awesoma.report.adapters.out.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.awesoma.report.application.port.ReportRenderer;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.content.MonitorRow;
import org.awesoma.report.domain.content.ReportContent;
import org.springframework.stereotype.Component;

/**
 * The same fields and metrics as the CSV, as a table on landscape A4 pages. Liberation Sans
 * (SIL Open Font License) is embedded as a subset, so Cyrillic shows on any viewer; cells wrap
 * long text, and the table header is repeated on every page.
 */
@Component
public class PdfReportRenderer implements ReportRenderer {
    private static final String REGULAR = "/liberation/LiberationSans-Regular.ttf";
    private static final String BOLD = "/liberation/LiberationSans-Bold.ttf";
    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 30;
    private static final float TEXT_SIZE = 7;
    private static final float PADDING = 2.5f;
    private static final float MONITOR_WIDTH = 190;
    private static final float HEADER_SIZE = 6.5f;
    /** Short labels broken where a reader expects it; the CSV header names the same fields in full. */
    private static final List<String> HEADERS = List.of("Монитор (ID, имя, URL)", "Проверок", "SUCCESS",
            "BAD_\nSTATUS", "TIMEOUT", "CONN_\nERROR", "Успешных,\n%", "Мин.,\nмс", "Сред.,\nмс", "Макс.,\nмс",
            "Инци-\nдентов", "LOW", "MEDIUM", "HIGH", "CRITICAL", "Открыто\nна конец");

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public void render(ReportContent content, OutputStream out) throws IOException {
        try (PDDocument document = new PDDocument()) {
            var layout = new Layout(document, load(document, REGULAR), load(document, BOLD));
            layout.title(content);
            layout.row(HEADERS, true);
            for (MonitorRow row : content.rows()) layout.row(cells(row), false);
            layout.close();
            document.save(out);
        }
    }

    private static List<String> cells(MonitorRow row) {
        var checks = row.checks();
        var incidents = row.incidents();
        return List.of("#" + row.monitor().id() + " " + text(row.monitor().name()) + "\n" + text(row.monitor().url()),
                Long.toString(checks.total()), Long.toString(checks.success()), Long.toString(checks.badStatus()),
                Long.toString(checks.timeout()), Long.toString(checks.connectionError()),
                metric(checks.successRatioPercent()), metric(checks.responseMsMin()), metric(checks.responseMsAvg()),
                metric(checks.responseMsMax()), Long.toString(incidents.total()), Long.toString(incidents.low()),
                Long.toString(incidents.medium()), Long.toString(incidents.high()),
                Long.toString(incidents.critical()), Long.toString(incidents.openAtEnd()));
    }

    private static String metric(Object value) {
        if (value == null) return "N/A";
        return value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static PDFont load(PDDocument document, String resource) throws IOException {
        try (InputStream font = PdfReportRenderer.class.getResourceAsStream(resource)) {
            if (font == null) throw new IOException("Font " + resource + " is missing from the classpath");
            return PDType0Font.load(document, font);
        }
    }

    /** Writes rows top-down and starts a new page, with the header again, when one does not fit. */
    private static final class Layout {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private final float[] widths;
        private PDPageContentStream stream;
        private List<String> header;
        private float y;

        Layout(PDDocument document, PDFont regular, PDFont bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            int numeric = HEADERS.size() - 1;
            float rest = (PAGE.getWidth() - 2 * MARGIN - MONITOR_WIDTH) / numeric;
            widths = new float[HEADERS.size()];
            widths[0] = MONITOR_WIDTH;
            for (int i = 1; i < widths.length; i++) widths[i] = rest;
            newPage();
        }

        void title(ReportContent content) throws IOException {
            line("Отчёт о мониторинге", bold, 14);
            line("Проект: " + text(content.project().name()) + " (ID " + content.project().id() + ")",
                    regular, 9);
            line("Период (UTC): " + content.period().from() + " — " + content.period().to()
                    + " (начало включено, конец исключён)", regular, 9);
            line("Запрошен: " + content.requestedAt() + "    Сформирован: " + content.generatedAt(),
                    regular, 9);
            line("Доля успешных — доля проверок с результатом SUCCESS, а не время доступности.",
                    regular, 8);
            y -= 6;
        }

        void row(List<String> cells, boolean isHeader) throws IOException {
            if (isHeader) header = cells;
            PDFont font = isHeader ? bold : regular;
            float size = isHeader ? HEADER_SIZE : TEXT_SIZE;
            float leading = size * 1.25f;
            List<List<String>> lines = new ArrayList<>();
            int height = 1;
            for (int i = 0; i < cells.size(); i++) {
                var wrapped = wrap(cells.get(i), font, widths[i] - 2 * PADDING, size);
                lines.add(wrapped);
                height = Math.max(height, wrapped.size());
            }
            float rowHeight = height * leading + 2 * PADDING;
            if (y - rowHeight < MARGIN) {
                newPage();
                if (!isHeader) row(header, true);
            }
            float x = MARGIN;
            for (int i = 0; i < cells.size(); i++) {
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.stroke();
                float lineY = y - PADDING - size;
                for (String text : lines.get(i)) {
                    stream.beginText();
                    stream.setFont(font, size);
                    stream.newLineAtOffset(x + PADDING, lineY);
                    stream.showText(text);
                    stream.endText();
                    lineY -= leading;
                }
                x += widths[i];
            }
            y -= rowHeight;
        }

        void close() throws IOException {
            stream.close();
        }

        private void line(String text, PDFont font, float size) throws IOException {
            for (String part : wrap(text, font, PAGE.getWidth() - 2 * MARGIN, size)) {
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y - size);
                stream.showText(part);
                stream.endText();
                y -= size * 1.4f;
            }
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            var page = new PDPage(PAGE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            stream.setLineWidth(0.4f);
            y = PAGE.getHeight() - MARGIN;
        }

        /** Breaks at spaces where possible and inside a word when it alone is too wide. */
        private List<String> wrap(String text, PDFont font, float width, float size) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : printable(text, font).split("\n", -1)) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ", -1)) {
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (width(candidate, font, size) <= width) {
                        line.setLength(0);
                        line.append(candidate);
                        continue;
                    }
                    if (!line.isEmpty()) lines.add(line.toString());
                    line.setLength(0);
                    for (int i = 0; i < word.length(); ) {
                        int end = i + 1;
                        while (end < word.length() && width(word.substring(i, end + 1), font, size) <= width) end++;
                        String piece = word.substring(i, end);
                        if (end < word.length()) lines.add(piece);
                        else line.append(piece);
                        i = end;
                    }
                }
                lines.add(line.toString());
            }
            return lines;
        }

        private static float width(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }

        /** Characters the font cannot show, and control characters, become a question mark. */
        private static String printable(String text, PDFont font) {
            StringBuilder result = new StringBuilder();
            text.codePoints().forEach(code -> {
                String character = new String(Character.toChars(code));
                if (code == '\n') {
                    result.append('\n');
                    return;
                }
                if (Character.isISOControl(code)) {
                    result.append('?');
                    return;
                }
                try {
                    font.encode(character);
                    result.append(character);
                } catch (IOException | IllegalArgumentException missing) {
                    result.append('?');
                }
            });
            return result.toString();
        }
    }
}
