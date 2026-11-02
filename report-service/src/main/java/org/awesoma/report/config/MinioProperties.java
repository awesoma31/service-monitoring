package org.awesoma.report.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection to the object storage. The access key belongs to an account limited to the
 * report bucket; the root credentials are used only by minio-init and never reach the service.
 */
@Validated
@ConfigurationProperties("storage.minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey) {

    @Override
    public String toString() {
        return "MinioProperties[endpoint=" + endpoint + ", bucket=" + bucket + "]";
    }
}
