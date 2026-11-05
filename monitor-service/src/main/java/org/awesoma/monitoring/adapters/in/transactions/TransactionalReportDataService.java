package org.awesoma.monitoring.adapters.in.transactions;

import org.springframework.stereotype.Component;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.awesoma.integration.reportdata.ReportDataResponses.MonitorItem;
import org.awesoma.integration.reportdata.ReportDataResponses.IncidentItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Ownership;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;


import org.awesoma.monitoring.application.usecase.ReportDataService;
@Component
@Transactional(readOnly = true)
public class TransactionalReportDataService extends ReportDataService {
    public TransactionalReportDataService(ProjectRepositoryPort projects,MonitorRepositoryPort monitors,IncidentRepositoryPort incidents) {
        super(projects,monitors,incidents);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<MonitorItem> monitors(long id,MonitorSelectionRequest request) {
        return super.monitors(id,request);
    }
    @Override
    @Transactional(readOnly = true)
    public Ownership ownership(long id,MonitorOwnershipRequest request) {
        return super.ownership(id,request);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<IncidentItem> incidents(long id,PeriodSearchRequest request) {
        return super.incidents(id,request);
    }
}
