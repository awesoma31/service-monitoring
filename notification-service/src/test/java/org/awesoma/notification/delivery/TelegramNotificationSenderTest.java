package org.awesoma.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.awesoma.notification.config.TelegramProperties;
import org.awesoma.notification.domain.ChannelType;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class TelegramNotificationSenderTest {

    @Test
    void callsTelegramSendMessageWithChatAndText() {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .flatMap(content -> {
                            path.set(request.uri());
                            body.set(content);
                            return response.status(200)
                                    .header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"ok\":true}"))
                                    .then();
                        }))
                .bindNow();
        try {
            TelegramProperties properties = new TelegramProperties(
                    true,
                    "123:test-token",
                    "http://127.0.0.1:" + server.port(),
                    "",
                    3128,
                    Duration.ofSeconds(2));
            TelegramNotificationSender sender = new TelegramNotificationSender(
                    WebClient.builder().baseUrl(properties.apiBaseUrl()).build(), properties);

            sender.send(task()).block();

            assertThat(path.get()).isEqualTo("/bot123:test-token/sendMessage");
            assertThat(body.get())
                    .contains("\"chat_id\":\"-100123\"")
                    .contains("Incident opened")
                    .contains("service is unavailable");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void failsBeforeCallingTelegramWhenTheTokenIsMissing() {
        TelegramProperties properties = new TelegramProperties(
                true, "", "http://localhost", "", 3128, Duration.ofSeconds(2));
        TelegramNotificationSender sender = new TelegramNotificationSender(
                WebClient.create(properties.apiBaseUrl()), properties);

        StepVerifier.create(sender.send(task()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DeliveryException.class)
                        .hasMessage("Telegram bot token is not configured"))
                .verify();
    }

    private DeliveryTask task() {
        return new DeliveryTask(
                1L,
                UUID.randomUUID(),
                ChannelType.TELEGRAM,
                "-100123",
                "Incident opened",
                "The service is unavailable",
                0);
    }
}
