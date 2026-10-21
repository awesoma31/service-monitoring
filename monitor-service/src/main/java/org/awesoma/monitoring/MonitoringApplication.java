package org.awesoma.monitoring;

import org.awesoma.security.JwtConfiguration;
import org.awesoma.security.ServiceTokenConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@EnableFeignClients
@SpringBootApplication
@Import({JwtConfiguration.class, ServiceTokenConfiguration.class})
public class MonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringApplication.class, args);
    }
}
