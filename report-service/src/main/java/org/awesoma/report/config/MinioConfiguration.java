package org.awesoma.report.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        long io = properties.ioTimeout().toMillis();
        client.setTimeout(properties.connectTimeout().toMillis(), io, io);
        return client;
    }
}
