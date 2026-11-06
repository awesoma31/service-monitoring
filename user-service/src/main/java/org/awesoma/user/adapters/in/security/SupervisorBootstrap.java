package org.awesoma.user.adapters.in.security;

import lombok.RequiredArgsConstructor;
import org.awesoma.user.application.usecase.SupervisorSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(20)
public class SupervisorBootstrap implements ApplicationRunner {
    private final SupervisorSetup setup;
    @Value("${security.bootstrap.email:supervisor@example.com}") private String email;
    @Value("${security.bootstrap.password:}") private String password;

    @Override
    public void run(ApplicationArguments args) {
        setup.ensure(email, password);
    }
}
