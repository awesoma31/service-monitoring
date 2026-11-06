package org.awesoma.monitoring.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.application.event.IncidentChanged;
import org.awesoma.monitoring.application.port.IncidentRepositoryPort;
import org.awesoma.monitoring.adapters.in.web.mapper.IncidentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.awesoma.monitoring.application.port.DomainEvents;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock private IncidentRepositoryPort incidents;
    @Mock private MonitorService monitors;
    @Mock private IncidentMapper mapper;
    @Mock private DomainEvents events;
    @InjectMocks private IncidentService service;

    @Test
    void resolvingByHandPublishesAResolvedIncidentEvent() {
        Project project = new Project();
        project.setId(7L);
        Monitor monitor = new Monitor();
        monitor.setId(5L);
        monitor.setProject(project);
        monitor.setCurrentState(MonitorState.DOWN);
        Incident incident = new Incident();
        incident.setId(3L);
        incident.setMonitor(monitor);
        when(incidents.findById(3L)).thenReturn(Optional.of(incident));

        service.resolve(3L);

        verify(events).publish(new IncidentChanged(3L, 7L, IncidentChanged.Kind.RESOLVED));
    }

    @Test
    void resolvingByHandDoesNotAnnounceWhenOwnerNotificationsAreDisabled() {
        Project project = new Project();
        project.setId(7L);
        project.setOwnerNotificationsEnabled(false);
        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setCurrentState(MonitorState.DOWN);
        Incident incident = new Incident();
        incident.setId(3L);
        incident.setMonitor(monitor);
        when(incidents.findById(3L)).thenReturn(Optional.of(incident));

        service.resolve(3L);

        verify(events, never()).publish(any());
    }
}
