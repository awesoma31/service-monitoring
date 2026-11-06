package org.awesoma.report.adapters.out.render;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.awesoma.report.application.port.ReportRenderer;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.content.MonitorRow;
import org.awesoma.report.domain.content.ReportContent;
import org.springframework.stereotype.Component;

/**
 * One row of aggregates per monitor under a fixed header, in UTF-8 with commas and CRLF line
 * ends. A missing metric is an empty field. Text from users — names and URLs — that starts
 * with {@code = + - @} gets an apostrophe in front, so a spreadsheet does not run it as a
 * formula; numbers stay plain numbers.
 */
@Component
public class CsvReportRenderer implements ReportRenderer {
    static final String HEADER = "project_id,project_name,monitor_id,monitor_name,monitor_url,period_from_utc,"
            + "period_to_utc,requested_at_utc,generated_at_utc,total_checks,success_count,bad_status_count,"
            + "timeout_count,connection_error_count,success_ratio_percent,response_ms_min,response_ms_avg,"
            + "response_ms_max,incidents_total,incidents_low,incidents_medium,incidents_high,incidents_critical,"
            + "incidents_open_at_end";

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public void render(ReportContent content, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(HEADER);
        writer.write("\r\n");
        for (MonitorRow row : content.rows()) {
            writer.write(String.join(",", fields(content, row)));
            writer.write("\r\n");
        }
        writer.flush();
    }

    private static List<String> fields(ReportContent content, MonitorRow row) {
        var checks = row.checks();
        var incidents = row.incidents();
        List<String> fields = new ArrayList<>();
        fields.add(Long.toString(content.project().id()));
        fields.add(text(content.project().name()));
        fields.add(Long.toString(row.monitor().id()));
        fields.add(text(row.monitor().name()));
        fields.add(text(row.monitor().url()));
        fields.add(content.period().from().toString());
        fields.add(content.period().to().toString());
        fields.add(content.requestedAt().toString());
        fields.add(content.generatedAt().toString());
        fields.add(Long.toString(checks.total()));
        fields.add(Long.toString(checks.success()));
        fields.add(Long.toString(checks.badStatus()));
        fields.add(Long.toString(checks.timeout()));
        fields.add(Long.toString(checks.connectionError()));
        fields.add(number(checks.successRatioPercent()));
        fields.add(number(checks.responseMsMin()));
        fields.add(number(checks.responseMsAvg()));
        fields.add(number(checks.responseMsMax()));
        fields.add(Long.toString(incidents.total()));
        fields.add(Long.toString(incidents.low()));
        fields.add(Long.toString(incidents.medium()));
        fields.add(Long.toString(incidents.high()));
        fields.add(Long.toString(incidents.critical()));
        fields.add(Long.toString(incidents.openAtEnd()));
        return fields;
    }

    static String text(String value) {
        if (value == null) return "";
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean quote = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                || !safe.equals(safe.strip());
        return quote ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }

    private static String number(Object value) {
        if (value == null) return "";
        return value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    }
}
