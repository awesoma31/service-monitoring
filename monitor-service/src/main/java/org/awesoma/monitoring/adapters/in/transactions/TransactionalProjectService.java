package org.awesoma.monitoring.adapters.in.transactions;

import org.springframework.stereotype.Component;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.domain.entity.Project;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.application.port.CurrentActor;
import org.awesoma.monitoring.application.port.DomainEvents;
import org.awesoma.monitoring.application.port.UserDirectoryPort;
import org.awesoma.monitoring.application.port.ProjectRepositoryPort;
import org.awesoma.monitoring.application.port.MemberRepositoryPort;
import org.awesoma.monitoring.application.port.MonitorRepositoryPort;
import org.awesoma.monitoring.application.port.ProjectPresenter;
import org.awesoma.monitoring.application.port.command.ProjectCreateRequest;
import org.awesoma.monitoring.application.port.command.ProjectUpdateRequest;
import org.awesoma.monitoring.application.port.command.OwnerNotificationsUpdateRequest;
import org.awesoma.monitoring.application.port.command.ProjectMemberRequest;
import org.awesoma.monitoring.application.model.view.ProjectResponse;
import org.awesoma.monitoring.application.model.view.ProjectMemberResponse;
import org.awesoma.monitoring.application.usecase.ProjectService;
@Component
@Transactional(readOnly = true)
public class TransactionalProjectService extends ProjectService {
    public TransactionalProjectService(CurrentActor access, ProjectRepositoryPort projects, MemberRepositoryPort members,
         UserDirectoryPort users, ProjectPresenter mapper, MonitorRepositoryPort monitors, DomainEvents events) {
    super(access,projects,members,users,mapper,monitors,events);
    }
    @Override
    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        return super.create(request);
    }
    @Override
    @Transactional
    public ProjectResponse update(Long id, ProjectUpdateRequest request) {
        return super.update(id,request);
    }
    @Override
    @Transactional
    public ProjectResponse updateOwnerNotifications(Long id, OwnerNotificationsUpdateRequest request) {
        return super.updateOwnerNotifications(id,request);
    }
    @Override
    @Transactional
    public void delete(Long id) {
        super.delete(id);
    }
    @Override
    @Transactional
    public ProjectMemberResponse addMember(Long id, ProjectMemberRequest request) {
        return super.addMember(id,request);
    }
    @Override
    @Transactional
    public void removeMember(Long id, Long userId) {
        super.removeMember(id,userId);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(PageRequest page) {
        return super.list(page);
    }
    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return super.get(id);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<ProjectMemberResponse> listMembers(Long id,PageRequest page) {
        return super.listMembers(id,page);
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
    public Project require(Long id) {
        return super.require(id);
    }
}
