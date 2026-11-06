package org.awesoma.notification.application.model;

import java.util.UUID;

public record ReportUploadNotice(long projectId, UUID reportId, String fileName, long uploadedBy) {
}
