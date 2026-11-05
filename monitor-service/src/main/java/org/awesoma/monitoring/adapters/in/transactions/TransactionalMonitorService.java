package org.awesoma.monitoring.adapters.in.transactions;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorPresenter;
import org.awesoma.monitoring.application.port.command.MonitorCreateRequest;
import org.awesoma.monitoring.application.port.command.MonitorUpdateRequest;
import org.awesoma.monitoring.application.model.view.MonitorResponse;
import org.awesoma.monitoring.application.usecase.ProjectService;
import org.awesoma.monitoring.application.usecase.MonitorService;
import org.awesoma.monitoring.application.usecase.TagService;
import java.util.Set;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.domain.entity.Monitor;
@Component
@Transactional(readOnly = true)
public class TransactionalMonitorService extends MonitorService {
    public TransactionalMonitorService(MonitorRepositoryPort monitors,ProjectService projects,TagService tags,
         MonitorPresenter mapper,DomainEvents events) { super(monitors,projects,tags,mapper,events); }
    @Override
    @Transactional
    public MonitorResponse create(Long projectId,MonitorCreateRequest request) {
        return super.create(projectId,request);
    }
    @Override
    @Transactional
    public MonitorResponse update(Long id,MonitorUpdateRequest request) {
        return super.update(id,request);
    }
    @Override
    @Transactional
    public MonitorResponse replaceTags(Long id,Set<String> names) {
        return super.replaceTags(id,names);
    }
    @Override
    @Transactional
    public void delete(Long id) {
        super.delete(id);
    }
    @Override
    @Transactional
    public void save(Monitor monitor) {
        super.save(monitor);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<MonitorResponse> listByProject(Long id,String tag,PageRequest page) {
        return super.listByProject(id,tag,page);
    }
    @Override
    @Transactional(readOnly = true)
    public MonitorResponse get(Long id) {
        return super.get(id);
    }
    @Override
    @Transactional(readOnly = true)
    public boolean exists(Long id) {
        return super.exists(id);
    }
    @Override
    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        super.requireExists(id);
    }
    @Override
    @Transactional(readOnly = true)
    public Monitor require(Long id) {
        return super.require(id);
    }
}
