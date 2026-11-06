package org.awesoma.notification.application.port;

import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.application.model.DeliveryTask;
import reactor.core.publisher.Mono;

public interface NotificationSender {

    ChannelType supportedType();

    Mono<Void> send(DeliveryTask task);
}
