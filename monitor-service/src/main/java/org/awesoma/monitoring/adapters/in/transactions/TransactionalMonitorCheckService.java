package org.awesoma.monitoring.adapters.in.transactions;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;


import org.awesoma.monitoring.application.usecase.MonitorCheckService;
import java.time.OffsetDateTime;
import java.util.List;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
@Component
public class TransactionalMonitorCheckService extends MonitorCheckService {
    public TransactionalMonitorCheckService(MonitorRepositoryPort monitors,IncidentRepositoryPort incidents,DomainEvents events) {
        super(monitors,incidents,events);
    }
    @Override
    @Transactional
    public List<MonitorTarget> claimDueTargets(int limit,int concurrency) {
        return super.claimDueTargets(limit,concurrency);
    }
    @Override
    @Transactional
    public void record(Long id,ProbeOutcome outcome) {
        super.record(id,outcome);
    }
    @Override
    @Transactional
    public void record(Long id,ProbeOutcome outcome,OffsetDateTime at) {
        super.record(id,outcome,at);
    }
}
