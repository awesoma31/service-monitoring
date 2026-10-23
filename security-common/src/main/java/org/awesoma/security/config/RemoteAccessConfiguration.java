package org.awesoma.security.config;

import org.awesoma.security.access.RemoteAccess;
import org.awesoma.security.access.ResourceAccessClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = ResourceAccessClient.class)
public class RemoteAccessConfiguration {
    @Bean
    public RemoteAccess remoteAccess(ResourceAccessClient client) {
        return new RemoteAccess(client);
    }
}
