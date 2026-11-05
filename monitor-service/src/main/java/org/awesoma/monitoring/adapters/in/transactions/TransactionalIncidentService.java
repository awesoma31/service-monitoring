package org.awesoma.monitoring.adapters.in.transactions;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.application.port.IncidentPresenter;

import org.awesoma.monitoring.application.model.view.IncidentResponse;
import org.awesoma.monitoring.application.usecase.MonitorService;
import org.awesoma.monitoring.application.usecase.IncidentService;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
@Component
@Transactional(readOnly = true)
public class TransactionalIncidentService extends IncidentService {
    public TransactionalIncidentService(IncidentRepositoryPort incidents,MonitorService monitors,IncidentPresenter mapper,DomainEvents events) {
        super(incidents,monitors,mapper,events);
    }
    @Override
    @Transactional
    public IncidentResponse resolve(Long id) {
        return super.resolve(id);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> listByMonitor(Long id, IncidentStatus status,PageRequest page) {
        return super.listByMonitor(id,status,page);
    }
    @Override
    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        return super.get(id);
    }
    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return super.exists(id);
    }
}
