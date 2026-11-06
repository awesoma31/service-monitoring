package org.awesoma.configupdates;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

/** Off by default: without Kafka a service only takes its settings from the config server on start. */
@AutoConfiguration
@ConditionalOnProperty(name = "config.updates.enabled", havingValue = "true")
public class ConfigUpdatesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Restarter configRestarter(ApplicationContext context) {
        return new ProcessRestarter(context);
    }

    @Bean
    ConfigUpdateCoordinator configUpdateCoordinator(ConfigurableEnvironment environment, ApplicationContext context,
            Restarter restarter) {
        var executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "config-updates");
            thread.setDaemon(true);
            return thread;
        });
        return new ConfigUpdateCoordinator(environment, context, restarter, executor,
                environment.getRequiredProperty("spring.application.name"),
                List.of(environment.getProperty("config.updates.hot-prefixes", String[].class, new String[0])),
                Duration.parse(environment.getProperty("config.updates.settle-delay", "PT5S")),
                Duration.parse(environment.getProperty("config.updates.restart-delay", "PT0S")));
    }

    @Bean
    ConfigUpdateListener configUpdateListener(ConfigUpdateCoordinator coordinator) {
        return new ConfigUpdateListener(coordinator);
    }
}
