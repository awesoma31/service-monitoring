package org.awesoma.report.application.usecase;

import java.util.UUID;
import org.awesoma.report.application.port.ReportPage;
import org.awesoma.report.domain.Report;

/** The operations the service offers, each checking the caller's right to the project. */
public final class ReportUseCases {

    private ReportUseCases() {
    }

    public interface UploadReport {
        /** Returns the report once its file is stored and it is READY. */
        Report upload(UploadReportCommand command);
    }

    public interface GenerateReport {
        /** Returns the queued job, or the existing one for a repeated idempotency key. */
        Report generate(GenerateReportCommand command);
    }

    public interface GetReportMetadata {
        Report get(UUID reportId, long userId);
    }

    public interface DownloadReport {
        ReportDownload download(UUID reportId, long userId);
    }

    public interface ListReports {
        ReportPage list(ListReportsQuery query);
    }

    public interface DeleteReport {
        /** Accepts the deletion; the object is removed in the background. */
        void delete(UUID reportId, long userId);
    }
}
