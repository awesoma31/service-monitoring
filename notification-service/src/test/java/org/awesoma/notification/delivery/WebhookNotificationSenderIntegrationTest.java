package org.awesoma.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

/** Runs in the application context so the body is serialized with the service's own settings. */
class WebhookNotificationSenderIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WebhookNotificationSender sender;

    private final AtomicReference<String> body = new AtomicReference<>();
    private DisposableServer server;

    @AfterEach
    void stopServer() {
        server.disposeNow();
    }

    @Test
    void postsTheNotificationInSnakeCase() {
        startServer(204);

        sender.send(task()).block();

        assertThat(body.get())
                .contains("\"notification_id\":1")
                .contains("\"subject\":\"Incident opened\"")
                .contains("\"message\":\"The service is unavailable\"")
                .doesNotContain("notificationId");
    }

    @Test
    void anErrorStatusIsADeliveryFailure() {
        startServer(500);

        StepVerifier.create(sender.send(task()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DeliveryException.class)
                        .hasMessage("Webhook returned HTTP 500"))
                .verify();
    }

    private void startServer(int status) {
        server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .flatMap(content -> {
                            body.set(content);
                            return response.status(status).send().then();
                        }))
                .bindNow();
    }

    private DeliveryTask task() {
        return new DeliveryTask(
                1L,
                UUID.randomUUID(),
                ChannelType.WEBHOOK,
                "http://127.0.0.1:" + server.port() + "/hook",
                "Incident opened",
                "The service is unavailable",
                0);
    }
}
