package org.awesoma.notification;

import org.springframework.context.annotation.Import;
import org.awesoma.security.ReactiveSecurityConfiguration;
import org.awesoma.security.ServiceTokenConfiguration;
import org.awesoma.security.RemoteAccessConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
@Import({ReactiveSecurityConfiguration.class, ServiceTokenConfiguration.class, RemoteAccessConfiguration.class})
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
