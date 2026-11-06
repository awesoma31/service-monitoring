package org.awesoma.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.notification.adapters.in.messaging.ResourceEventMapper;
import org.awesoma.notification.application.model.ReportUploadNotice;
import org.awesoma.notification.application.port.ChannelStore;
import org.awesoma.notification.application.port.MonitorDirectory;
import org.awesoma.notification.application.port.NotificationStore;
import org.awesoma.notification.application.port.ProjectGuard;
import org.awesoma.notification.application.port.UnitOfWork;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.domain.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportUploadNotificationTest {
    private static final UUID REPORT = UUID.randomUUID();
    private static final ReportUploadNotice NOTICE = new ReportUploadNotice(42, REPORT, "uptime.pdf", 7);

    private final NotificationStore notifications = mock(NotificationStore.class);
    private final ChannelStore channels = mock(ChannelStore.class);
    private final ProjectGuard projects = mock(ProjectGuard.class);
    private final NotificationService service = new NotificationService(notifications, channels,
            mock(MonitorDirectory.class), mock(UnitOfWork.class), projects);

    @BeforeEach
    void setUp() {
        when(notifications.saveAll(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static Channel channel(ChannelType type, String target) {
        Channel channel = new Channel();
        channel.setType(type);
        channel.setTarget(target);
        return channel;
    }

    @Test
    void writesToTheEmailChannelsOfTheProjectOnly() {
        when(channels.findByProjectIdAndEnabledTrue(42L)).thenReturn(List.of(
                channel(ChannelType.EMAIL, "author@example.com"),
                channel(ChannelType.WEBHOOK, "https://example.com/hook"),
                channel(ChannelType.TELEGRAM, "123")));

        assertThat(service.recordReportUpload(NOTICE)).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(saved.capture());
        Notification notification = saved.getValue().getFirst();
        assertThat(notification.getChannel().getTarget()).isEqualTo("author@example.com");
        assertThat(notification.getReportId()).isEqualTo(REPORT);
        assertThat(notification.getIncidentId()).isNull();
        assertThat(notification.getIncidentKind()).isNull();
        assertThat(notification.getSubject()).contains("uptime.pdf");
    }

    @Test
    void aProjectWithoutAnEmailChannelGetsNothing() {
        when(channels.findByProjectIdAndEnabledTrue(42L)).thenReturn(List.of(channel(ChannelType.WEBHOOK, "https://x")));

        assertThat(service.recordReportUpload(NOTICE)).isZero();
    }

    @Test
    void aDeletedProjectGetsNothing() {
        when(projects.deleted(42L)).thenReturn(true);

        assertThat(service.recordReportUpload(NOTICE)).isZero();
        verify(channels, never()).findByProjectIdAndEnabledTrue(any());
    }

    @Test
    void mapsAValidEvent() {
        var event = IntegrationEvent.reportUploaded(UUID.randomUUID(), OffsetDateTime.now(), 42, REPORT, "uptime.pdf", 7);

        assertThat(ResourceEventMapper.from(event).report()).isEqualTo(NOTICE);
    }

    @Test
    void refusesAFileNameThatCouldBreakTheMailHeader() {
        var event = IntegrationEvent.reportUploaded(UUID.randomUUID(), OffsetDateTime.now(), 42, REPORT, "a\nBcc: x", 7);

        assertThatThrownBy(() -> ResourceEventMapper.from(event)).isInstanceOf(IllegalArgumentException.class);
    }
}
