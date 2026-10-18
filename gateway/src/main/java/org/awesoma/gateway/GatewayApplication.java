package org.awesoma.gateway;

import org.springframework.context.annotation.Import;
import org.awesoma.security.ReactiveSecurityConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Import(ReactiveSecurityConfiguration.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
