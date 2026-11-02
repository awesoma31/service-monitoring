package org.awesoma.report.application.port;

import java.time.Instant;
import java.util.List;

/**
 * One page of a keyset walk. The source fixes {@code upperId} and {@code capturedAt} when the
 * walk starts; {@code nextCursor} is opaque and is {@code null} on the last page.
 */
public record SourcePage<T>(List<T> items, String nextCursor, boolean hasNext, Instant capturedAt, Long upperId) {

    public SourcePage {
        items = List.copyOf(items);
    }
}
