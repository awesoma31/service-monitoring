package org.awesoma.monitoring.adapters.in.transactions;

import org.awesoma.monitoring.application.port.UserDirectoryPort;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.application.usecase.PermissionPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalPermissionPolicy extends PermissionPolicy {
    public TransactionalPermissionPolicy(UserDirectoryPort users, ProjectRepositoryPort projects,
            MemberRepositoryPort members, MonitorRepositoryPort monitors, IncidentRepositoryPort incidents) {
        super(users, projects, members, monitors, incidents);
    }
    @Override @Transactional(readOnly = true)
    public boolean owner(Long projectId, Long userId) {
        return super.owner(projectId, userId);
    }
    @Override @Transactional(readOnly = true)
    public boolean allowed(String resource, Long id, Long userId, boolean write) {
        return super.allowed(resource, id, userId, write);
    }
}
