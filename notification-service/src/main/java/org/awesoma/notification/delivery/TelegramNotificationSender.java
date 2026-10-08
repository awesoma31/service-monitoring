package org.awesoma.notification.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.awesoma.notification.config.TelegramProperties;
import org.awesoma.notification.domain.ChannelType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class TelegramNotificationSender implements NotificationSender {

    private final WebClient webClient;
    private final TelegramProperties properties;

    public TelegramNotificationSender(
            @Qualifier("telegramWebClient") WebClient webClient,
            TelegramProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public ChannelType supportedType() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public Mono<Void> send(DeliveryTask task) {
        if (!properties.enabled()) {
            return Mono.error(new DeliveryException("Telegram delivery is disabled"));
        }
        if (!StringUtils.hasText(properties.botToken())) {
            return Mono.error(new DeliveryException("Telegram bot token is not configured"));
        }

        String text = task.subject() + "\n\n" + task.message();
        return webClient
                .post()
                .uri("/bot" + properties.botToken() + "/sendMessage")
                .bodyValue(new TelegramRequest(task.target(), text))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new DeliveryException(
                        "Telegram API returned HTTP " + response.statusCode().value())))
                .bodyToMono(TelegramResponse.class)
                .switchIfEmpty(Mono.error(new DeliveryException("Telegram API returned an empty response")))
                .flatMap(response -> response.ok()
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new DeliveryException("Telegram API rejected the message: "
                                + response.safeDescription())))
                .onErrorMap(
                        error -> !(error instanceof DeliveryException),
                        error -> new DeliveryException("Telegram delivery failed", error));
    }

    private record TelegramRequest(
            @JsonProperty("chat_id") String chatId,
            String text) {}

    private record TelegramResponse(boolean ok, String description) {

        private String safeDescription() {
            return StringUtils.hasText(description) ? description : "unknown error";
        }
    }
}
