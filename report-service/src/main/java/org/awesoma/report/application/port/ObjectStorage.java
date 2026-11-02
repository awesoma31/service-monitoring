package org.awesoma.report.application.port;

import java.io.InputStream;

/**
 * Report contents by object key. Every call is bounded in time; a timeout or an unreachable
 * storage is {@link ObjectStorageUnavailableException}, never a statement about the object.
 */
public interface ObjectStorage {

    /** Streams {@code size} bytes, or an unknown length when {@code size} is negative. */
    void put(String key, InputStream content, long size, String contentType);

    /** @throws ObjectNotFoundException when the storage confirms the key does not exist */
    StoredObject open(String key);

    /** Deleting a key that does not exist succeeds. */
    void delete(String key);

    boolean exists(String key);
}
