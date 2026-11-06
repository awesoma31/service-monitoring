package org.awesoma.report.application.port;

import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;

/** A page of selected monitors together with the project they belong to. */
public record MonitorPage(ProjectInfo project, SourcePage<MonitorInfo> monitors) {
}
