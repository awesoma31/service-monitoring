package org.awesoma.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.awesoma.notification.config.EmailProperties;
import org.awesoma.notification.domain.ChannelType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    @Mock private JavaMailSender mailSender;

    @Test
    void sendsThePersistedMessageThroughSmtp() {
        EmailNotificationSender sender = new EmailNotificationSender(
                mailSender, new EmailProperties(true, "monitoring@example.com"));

        sender.send(task()).block();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("monitoring@example.com");
        assertThat(captor.getValue().getTo()).containsExactly("owner@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Incident opened");
        assertThat(captor.getValue().getText()).isEqualTo("The service is unavailable");
    }

    @Test
    void reportsDisabledEmailWithoutContactingSmtp() {
        EmailNotificationSender sender =
                new EmailNotificationSender(mailSender, new EmailProperties(false, "from@example.com"));

        StepVerifier.create(sender.send(task()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DeliveryException.class)
                        .hasMessage("Email delivery is disabled"))
                .verify();
    }

    private DeliveryTask task() {
        return new DeliveryTask(
                1L,
                UUID.randomUUID(),
                ChannelType.EMAIL,
                "owner@example.com",
                "Incident opened",
                "The service is unavailable",
                0);
    }
}
