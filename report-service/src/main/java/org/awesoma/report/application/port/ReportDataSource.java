package org.awesoma.report.application.port;

import java.util.List;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.source.CheckRecord;
import org.awesoma.report.domain.source.IncidentRecord;

/**
 * Monitoring data read from the services that own it, page by page. A {@code null} cursor
 * starts a walk; a cursor is valid only for the query that produced it. A temporary failure
 * is {@link SourceUnavailableException}; a query refused for good is
 * {@link SourceRejectedException}.
 */
public interface ReportDataSource {

    MonitorPage monitors(long projectId, MonitorSelection selection, String cursor, int limit);

    SourcePage<CheckRecord> checks(long projectId, List<Long> monitorIds, ReportPeriod period, String cursor,
            int limit);

    SourcePage<IncidentRecord> incidents(long projectId, List<Long> monitorIds, ReportPeriod period,
            String cursor, int limit);
}
