package org.awesoma.report.adapters.out.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import org.awesoma.report.application.content.ContentTooLargeException;
import org.awesoma.report.application.content.MeasuredInputStream;
import org.awesoma.report.application.port.ObjectNotFoundException;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.config.MinioConfiguration;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.awesoma.report.support.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MinioObjectStorageTest extends AbstractIntegrationTest {
    @Autowired private MinioObjectStorage storage;

    @Test
    void storesAndReturnsTheSameBytesWithTheirHash() throws Exception {
        byte[] content = randomBytes(256 * 1024);
        String key = key();
        var measured = new MeasuredInputStream(new ByteArrayInputStream(content), content.length);

        storage.put(key, measured, content.length, "application/pdf");

        assertThat(measured.size()).isEqualTo(content.length);
        assertThat(measured.sha256()).isEqualTo(sha256(content));
        try (var stored = storage.open(key)) {
            assertThat(stored.size()).isEqualTo(content.length);
            assertThat(stored.content().readAllBytes()).isEqualTo(content);
        }
        var stat = rootClient().statObject(StatObjectArgs.builder().bucket(BUCKET).object(key).build());
        assertThat(stat.contentType()).isEqualTo("application/pdf");
        assertThat(stat.etag()).as("an ETag is not the SHA-256").isNotEqualTo(sha256(content));
    }

    @Test
    void streamsContentOfUnknownLength() throws Exception {
        byte[] content = randomBytes((int) MinioObjectStorage.PART_SIZE + 4096);
        String key = key();

        storage.put(key, new ByteArrayInputStream(content), -1, "text/csv; charset=UTF-8");

        try (var stored = storage.open(key)) {
            assertThat(stored.size()).isEqualTo(content.length);
            assertThat(sha256(stored.content().readAllBytes())).isEqualTo(sha256(content));
        }
    }

    @Test
    void refusesContentPastTheLimitWithoutStoringIt() {
        byte[] content = randomBytes(2048);
        String key = key();

        assertThatThrownBy(() -> storage.put(key,
                new MeasuredInputStream(new ByteArrayInputStream(content), 1024), -1, "application/pdf"))
                .isInstanceOf(ContentTooLargeException.class);
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void deletesIdempotently() {
        String key = key();
        storage.put(key, new ByteArrayInputStream(new byte[] {1, 2, 3}), 3, "application/pdf");
        assertThat(storage.exists(key)).isTrue();

        storage.delete(key);
        storage.delete(key);

        assertThat(storage.exists(key)).isFalse();
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void anUnreachableStorageIsUnavailableNotEmpty() {
        var properties = storageProperties("http://127.0.0.1:1", BUCKET, "user", "password");
        var unreachable = new MinioObjectStorage(new MinioConfiguration().minioClient(properties), properties);
        InputStream content = new ByteArrayInputStream(new byte[] {1});

        assertThatThrownBy(() -> unreachable.exists("any")).isInstanceOf(ObjectStorageUnavailableException.class);
        assertThatThrownBy(() -> unreachable.open("any")).isInstanceOf(ObjectStorageUnavailableException.class);
        assertThatThrownBy(() -> unreachable.delete("any")).isInstanceOf(ObjectStorageUnavailableException.class);
        assertThatThrownBy(() -> unreachable.put("any", content, 1, "application/pdf"))
                .isInstanceOf(ObjectStorageUnavailableException.class);
    }

    @Test
    void aMissingBucketIsAnOutageNotAMissingObject() {
        var properties = storageProperties(Containers.MINIO.getS3URL(), "no-such-bucket",
                Containers.MINIO.getUserName(), Containers.MINIO.getPassword());
        var misconfigured = new MinioObjectStorage(rootClient(), properties);

        assertThatThrownBy(() -> misconfigured.exists("any")).isInstanceOf(ObjectStorageUnavailableException.class);
        assertThatThrownBy(() -> misconfigured.open("any")).isInstanceOf(ObjectStorageUnavailableException.class);
    }

    private static String key() {
        return "projects/1/reports/" + UUID.randomUUID();
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
