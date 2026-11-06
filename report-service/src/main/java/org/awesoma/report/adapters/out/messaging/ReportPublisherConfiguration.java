package org.awesoma.report.adapters.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.report.adapters.out.http.NotificationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
@Slf4j
class ReportPublisherConfiguration {
    static final String TOPIC = "reports.events.v1";

    @Bean
    @ConditionalOnProperty(prefix = "integration.report-events", name = "transport", havingValue = "http",
            matchIfMissing = true)
    ReportEventPublisher httpReportEventPublisher(NotificationClient client) {
        return new ReportEventPublisher() {
            @Override
            public String transport() {
                return "http";
            }

            @Override
            public void publish(IntegrationEvent event) {
                client.integrationEvent(event);
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "integration.report-events", name = "transport", havingValue = "kafka")
    ReportEventPublisher kafkaReportEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        return new ReportEventPublisher() {
            @Override
            public String transport() {
                return "kafka";
            }

            @Override
            public void publish(IntegrationEvent event) {
                try {
                    var sent = kafka.send(TOPIC, "project-" + event.projectId(), json.writeValueAsString(event))
                            .get(20, TimeUnit.SECONDS);
                    log.info("Kafka event published: topic={} partition={} offset={} event_id={} type={} report_id={}",
                            TOPIC, sent.getRecordMetadata().partition(), sent.getRecordMetadata().offset(),
                            event.eventId(), event.type(), event.reportId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted publishing integration event", e);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot publish integration event", e);
                }
            }
        };
    }
}
