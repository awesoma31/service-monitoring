package org.awesoma.notification.delivery;

import lombok.RequiredArgsConstructor;
import org.awesoma.notification.config.EmailProperties;
import org.awesoma.notification.domain.ChannelType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    @Override
    public ChannelType supportedType() {
        return ChannelType.EMAIL;
    }

    @Override
    public Mono<Void> send(DeliveryTask task) {
        return Mono.fromRunnable(() -> {
                    if (!properties.enabled()) {
                        throw new DeliveryException("Email delivery is disabled");
                    }
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(properties.from());
                    mail.setTo(task.target());
                    mail.setSubject(task.subject());
                    mail.setText(task.message());
                    mailSender.send(mail);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        error -> !(error instanceof DeliveryException),
                        error -> new DeliveryException("Email delivery failed", error))
                .then();
    }
}
