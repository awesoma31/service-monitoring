package org.awesoma.check;

import org.springframework.context.annotation.Import;
import org.awesoma.security.ReactiveSecurityConfiguration;
import org.awesoma.security.ServiceTokenConfiguration;
import org.awesoma.security.RemoteAccessConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
@Import({ReactiveSecurityConfiguration.class, ServiceTokenConfiguration.class, RemoteAccessConfiguration.class})
public class CheckServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckServiceApplication.class, args);
    }
}
