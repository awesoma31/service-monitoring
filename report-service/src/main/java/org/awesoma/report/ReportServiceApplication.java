package org.awesoma.report;

import org.awesoma.report.adapters.out.http.ProjectLookupClient;
import org.awesoma.report.adapters.out.http.ReportDataClients;
import org.awesoma.security.access.ResourceAccessClient;
import org.awesoma.security.config.ServiceTokenConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients(clients = {ResourceAccessClient.class, ProjectLookupClient.class,
    ReportDataClients.MonitorData.class, ReportDataClients.CheckData.class})
@Import(ServiceTokenConfiguration.class)
public class ReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportServiceApplication.class, args);
    }
}
