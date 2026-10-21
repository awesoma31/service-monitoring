package org.awesoma.user.config;

import org.awesoma.user.migration.LegacyUserImporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class LegacyImportConfiguration {
    @Bean @Order(0)
    public ApplicationRunner importLegacyUsers(LegacyUserImporter importer,
            @Value("${security.migration.source-url:}") String url,
            @Value("${security.migration.source-user:}") String user,
            @Value("${security.migration.source-password:}") String password) {
        return args -> importer.importFrom(url, user, password);
    }
}
