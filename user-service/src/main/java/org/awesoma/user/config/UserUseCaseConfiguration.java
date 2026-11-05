package org.awesoma.user.config;

import java.time.Duration;
import org.awesoma.security.jwt.JwtTokens;
import org.awesoma.user.application.model.Token;
import org.awesoma.user.application.port.ClientStore;
import org.awesoma.user.application.port.PasswordHasher;
import org.awesoma.user.application.port.TokenIssuer;
import org.awesoma.user.application.port.Transactions;
import org.awesoma.user.application.port.UserStore;
import org.awesoma.user.application.usecase.AuthService;
import org.awesoma.user.application.usecase.ClientCredentialsSetup;
import org.awesoma.user.application.usecase.ServiceAuthenticationService;
import org.awesoma.user.application.usecase.SupervisorSetup;
import org.awesoma.user.application.usecase.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Binds application ports to Spring Security, JPA and transaction adapters. */
@Configuration(proxyBeanMethods = false)
public class UserUseCaseConfiguration {
    @Bean
    PasswordHasher passwordHasher(PasswordEncoder encoder) {
        return new PasswordHasher() {
            @Override
            public String encode(String raw) {
                return encoder.encode(raw);
            }

            @Override
            public boolean matches(String raw, String hash) {
                return encoder.matches(raw, hash);
            }
        };
    }

    @Bean
    TokenIssuer tokenIssuer(JwtTokens tokens) {
        return (subject, role) -> {
            var jwt = tokens.issue(subject, role);
            return new Token(jwt.getTokenValue(), "Bearer",
                    Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        };
    }

    @Bean
    SupervisorSetup supervisorSetup(UserStore users, PasswordHasher passwords, Transactions transactions) {
        return new SupervisorSetup(users, passwords, transactions);
    }

    @Bean
    ClientCredentialsSetup clientCredentialsSetup(ClientStore clients, PasswordHasher passwords,
            Transactions transactions) {
        return new ClientCredentialsSetup(clients, passwords, transactions);
    }

    @Bean
    UserService userService(UserStore users, PasswordHasher passwords, Transactions transactions) {
        return new UserService(users, passwords, transactions);
    }

    @Bean
    AuthService authService(UserStore users, PasswordHasher passwords, TokenIssuer issuer,
            Transactions transactions) {
        return new AuthService(users, passwords, issuer, transactions);
    }

    @Bean
    ServiceAuthenticationService serviceAuthenticationService(ClientStore clients, PasswordHasher passwords,
            TokenIssuer issuer, Transactions transactions) {
        return new ServiceAuthenticationService(clients, passwords, issuer, transactions);
    }
}
