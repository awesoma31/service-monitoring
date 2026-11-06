package org.awesoma.report.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Connection to the object storage. The access key belongs to an account limited to the
 * report bucket; the root credentials are used only by minio-init and never reach the service.
 * Every call is bounded: {@code ioTimeout} applies to each read or write of the stream.
 */
@Validated
@ConfigurationProperties("storage.minio")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotNull @DefaultValue("PT5S") Duration connectTimeout,
        @NotNull @DefaultValue("PT1M") Duration ioTimeout) {

    @Override
    public String toString() {
        return "MinioProperties[endpoint=" + endpoint + ", bucket=" + bucket + "]";
    }
}
