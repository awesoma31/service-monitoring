package org.awesoma.user.security;

import lombok.RequiredArgsConstructor;
import org.awesoma.user.config.ServiceClientsProperties;
import org.awesoma.user.domain.entity.ServiceClient;
import org.awesoma.user.repository.ServiceClientRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@RequiredArgsConstructor
@EnableConfigurationProperties(ServiceClientsProperties.class)
public class ServiceClientsBootstrap implements ApplicationRunner {
    private final ServiceClientsProperties properties;
    private final ServiceClientRepository clients;
    private final PasswordEncoder passwords;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        properties.secrets().forEach((id, secret) -> {
            if (secret.isBlank()) return;
            if (secret.length() < 32) {
                throw new IllegalArgumentException("Service client secrets must contain at least 32 characters");
            }
            var client = clients.findById(id).orElseGet(() -> {
                var created = new ServiceClient();
                created.setId(id);
                return created;
            });
            if (client.getSecretHash() == null || !passwords.matches(secret, client.getSecretHash())) {
                client.setSecretHash(passwords.encode(secret));
                clients.save(client);
            }
        });
    }
}
