package org.awesoma.report.application.port;

import java.io.IOException;
import java.io.InputStream;

/** An open object stream; the caller must close it whether or not the transfer completes. */
public record StoredObject(InputStream content, long size) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
