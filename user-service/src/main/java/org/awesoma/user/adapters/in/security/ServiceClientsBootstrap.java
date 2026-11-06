package org.awesoma.user.adapters.in.security;

import lombok.RequiredArgsConstructor;
import org.awesoma.user.application.usecase.ClientCredentialsSetup;
import org.awesoma.user.config.ServiceClientsProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
@EnableConfigurationProperties(ServiceClientsProperties.class)
public class ServiceClientsBootstrap implements ApplicationRunner {
    private final ServiceClientsProperties properties;
    private final ClientCredentialsSetup setup;

    @Override
    public void run(ApplicationArguments args) {
        setup.ensure(properties.secrets());
    }
}
