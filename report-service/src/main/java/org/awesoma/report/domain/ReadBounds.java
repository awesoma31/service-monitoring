package org.awesoma.report.domain;

import java.time.Instant;

/**
 * Where the sources stopped reading for an attempt: rows with a higher ID than recorded here
 * are left out. These bounds are not one atomic snapshot across services.
 */
public record ReadBounds(Instant observedAt, Long checksUpperId, Long incidentsUpperId) {
}
