package org.awesoma.notification.config;

import java.util.List;
import org.awesoma.notification.application.model.DeliverySettings;
import org.awesoma.notification.application.port.ChannelStore;
import org.awesoma.notification.application.port.MonitorDirectory;
import org.awesoma.notification.application.port.NotificationSender;
import org.awesoma.notification.application.port.NotificationStore;
import org.awesoma.notification.application.port.ProjectGuard;
import org.awesoma.notification.application.port.UnitOfWork;
import org.awesoma.notification.application.usecase.ChannelService;
import org.awesoma.notification.application.usecase.NotificationDeliveryService;
import org.awesoma.notification.application.usecase.NotificationEventHandler;
import org.awesoma.notification.application.usecase.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationUseCases {
    @Bean ChannelService channelService(ChannelStore channels, MonitorDirectory monitors, UnitOfWork work, ProjectGuard projects) {
        return new ChannelService(channels, monitors, work, projects);
    }
    @Bean NotificationService notificationService(NotificationStore notifications, ChannelStore channels,
            MonitorDirectory monitors, UnitOfWork work, ProjectGuard projects) {
        return new NotificationService(notifications, channels, monitors, work, projects);
    }
    @Bean NotificationEventHandler notificationEventHandler(UnitOfWork work, ProjectGuard projects,
            ChannelService channels, NotificationService notifications) {
        return new NotificationEventHandler(work, projects, channels, notifications);
    }
    @Bean NotificationDeliveryService notificationDeliveryService(NotificationStore notifications, UnitOfWork work,
            DeliveryProperties properties, List<NotificationSender> senders) {
        return new NotificationDeliveryService(notifications, work,
                new DeliverySettings(properties.batchSize(), properties.concurrency(), properties.leaseDuration(),
                        properties.maxAttempts(), properties.retryDelay()), senders);
    }
}
