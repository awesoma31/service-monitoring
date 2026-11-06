package org.awesoma.configserver.changes;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "config.changes.enabled", havingValue = "true")
class ConfigChangesConfiguration {

    @Bean
    ConfigChangePublisher configChangePublisher(@Value("${config.changes.repository-path}") Path repository,
            EnvironmentRepository environments, KafkaTemplate<String, String> kafka) {
        return new ConfigChangePublisher(new ConfigRepositoryScanner(repository), new ConfigSnapshots(environments),
                kafka, Clock.systemUTC());
    }
}
