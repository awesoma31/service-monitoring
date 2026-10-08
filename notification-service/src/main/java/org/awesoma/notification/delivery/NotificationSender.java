package org.awesoma.notification.delivery;

import org.awesoma.notification.domain.ChannelType;
import reactor.core.publisher.Mono;

public interface NotificationSender {

    ChannelType supportedType();

    Mono<Void> send(DeliveryTask task);
}
