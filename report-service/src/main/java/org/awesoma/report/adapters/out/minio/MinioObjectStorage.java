package org.awesoma.report.adapters.out.minio;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.content.ContentRejectedException;
import org.awesoma.report.application.port.ObjectNotFoundException;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.StoredObject;
import org.awesoma.report.config.MinioProperties;
import org.springframework.stereotype.Component;

/**
 * Report objects in one private bucket. Content is streamed in both directions and never
 * collected into a byte array. Only an S3 "no such key" answer counts as a missing object;
 * any other failure, a timeout included, is reported as the storage being unavailable.
 */
@Component
@RequiredArgsConstructor
public class MinioObjectStorage implements ObjectStorage {
    /** Parts of an upload of unknown length; S3 needs at least 5 MiB per part. */
    static final long PART_SIZE = 10L * 1024 * 1024;
    private static final Set<String> MISSING = Set.of("NoSuchKey", "NoSuchObject");

    private final MinioClient minio;
    private final MinioProperties properties;

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .stream(content, size >= 0 ? size : -1, size >= 0 ? -1 : PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception error) {
            // The client may wrap what the caller's stream raised; rejected content is not an outage.
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ContentRejectedException rejected) throw rejected;
            }
            throw unavailable("store", key, error);
        }
    }

    @Override
    public StoredObject open(String key) {
        try {
            GetObjectResponse response = minio.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket()).object(key).build());
            String length = response.headers().get("Content-Length");
            return new StoredObject(response, length == null ? -1 : Long.parseLong(length));
        } catch (ErrorResponseException error) {
            if (missing(error)) throw new ObjectNotFoundException(key);
            throw unavailable("read", key, error);
        } catch (Exception error) {
            throw unavailable("read", key, error);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
        } catch (Exception error) {
            throw unavailable("delete", key, error);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            minio.statObject(StatObjectArgs.builder().bucket(properties.bucket()).object(key).build());
            return true;
        } catch (ErrorResponseException error) {
            if (missing(error)) return false;
            throw unavailable("check", key, error);
        } catch (Exception error) {
            throw unavailable("check", key, error);
        }
    }

    private static boolean missing(ErrorResponseException error) {
        return error.errorResponse() != null && MISSING.contains(error.errorResponse().code());
    }

    private static ObjectStorageUnavailableException unavailable(String action, String key, Exception error) {
        return new ObjectStorageUnavailableException("Cannot " + action + " object " + key, error);
    }
}
