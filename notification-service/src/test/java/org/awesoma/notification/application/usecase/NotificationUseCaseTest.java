package org.awesoma.notification.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.awesoma.notification.application.exception.NotFoundException;
import org.awesoma.notification.application.model.DeliverySettings;
import org.awesoma.notification.application.port.ChannelStore;
import org.awesoma.notification.application.port.MonitorDirectory;
import org.awesoma.notification.application.port.NotificationSender;
import org.awesoma.notification.application.port.NotificationStore;
import org.awesoma.notification.application.port.ProjectGuard;
import org.awesoma.notification.application.port.UnitOfWork;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.domain.IncidentKind;
import org.awesoma.notification.domain.Notification;
import org.awesoma.notification.domain.NotificationStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class NotificationUseCaseTest {
    private final ChannelStore channels = mock(ChannelStore.class);
    private final NotificationStore notifications = mock(NotificationStore.class);
    private final MonitorDirectory monitors = mock(MonitorDirectory.class);
    private final ProjectGuard guard = mock(ProjectGuard.class);
    private final UnitOfWork work = new UnitOfWork() {
        public <T> Mono<T> read(Supplier<T> action) { return Mono.fromCallable(action::get); }
        public <T> Mono<T> write(Supplier<T> action) { return Mono.fromCallable(action::get); }
    };

    @Test
    void projectDeletionFencePreventsCreatingAChannelAfterAnEarlierRemoteExistenceCheck() {
        when(monitors.projectExists(42L)).thenReturn(Mono.just(true));
        when(guard.deleted(42L)).thenReturn(true);
        var useCase = new ChannelService(channels, monitors, work, guard);

        StepVerifier.create(useCase.create(42L, ChannelType.EMAIL, "owner@example.com"))
                .expectError(NotFoundException.class).verify();
        verify(guard).lock(42L);
        verify(channels, never()).save(any());
    }

    @Test
    void failedDeliveryPersistsTheRetryStateAndRedactsTheToken() {
        Channel channel = new Channel();
        channel.setId(5L);
        channel.setType(ChannelType.TELEGRAM);
        channel.setTarget("123");
        var notification = new Notification(7L, channel, IncidentKind.OPENED, "title", "message");
        notification.setId(9L);
        when(notifications.findReadyForClaim(10)).thenReturn(List.of(notification));
        when(notifications.findForUpdateById(9L)).thenReturn(Optional.of(notification));
        NotificationSender sender = mock(NotificationSender.class);
        when(sender.supportedType()).thenReturn(ChannelType.TELEGRAM);
        when(sender.send(any())).thenReturn(Mono.error(new IllegalStateException("bot123:secret/sendMessage")));
        var settings = new DeliverySettings(10, 1, Duration.ofMinutes(1), 3, Duration.ofSeconds(5));
        var useCase = new NotificationDeliveryService(notifications, work, settings, List.of(sender));

        StepVerifier.create(useCase.dispatchOnce()).expectNext(1).verifyComplete();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getLastError()).doesNotContain("secret").contains("bot<redacted>");
        assertThat(notification.getDeliveryClaimToken()).isNull();
        verify(notifications, times(2)).save(notification);
    }
}
