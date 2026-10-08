package org.awesoma.notification.delivery;

import java.time.Duration;
import org.awesoma.notification.domain.ChannelType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WebhookNotificationSender implements NotificationSender {

    private final WebClient webClient;

    /** The builder Spring configures, so the body is snake_case like the rest of the API. */
    public WebhookNotificationSender(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    @Override
    public ChannelType supportedType() {
        return ChannelType.WEBHOOK;
    }

    @Override
    public Mono<Void> send(DeliveryTask task) {
        return webClient
                .post()
                .uri(task.target())
                .bodyValue(new WebhookRequest(
                        task.notificationId(), task.subject(), task.message()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new DeliveryException(
                        "Webhook returned HTTP " + response.statusCode().value())))
                .toBodilessEntity()
                .then()
                .timeout(Duration.ofSeconds(15))
                .onErrorMap(
                        error -> !(error instanceof DeliveryException),
                        error -> new DeliveryException("Webhook delivery failed", error));
    }

    private record WebhookRequest(Long notificationId, String subject, String message) {}
}
