package org.awesoma.monitoring.integration;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/** A failed announcement or cleanup is logged, never thrown at the committed change. */
class FallbackFactoriesTest {

    private final IllegalStateException cause = new IllegalStateException("connection refused");

    @Test
    void anIncidentThatCannotBeAnnouncedIsDroppedWithoutFailing() {
        NotificationClient fallback = new NotificationClientFallbackFactory().create(cause);

        assertThatCode(() -> fallback.incidentChanged(
                new IncidentChanged(1L, 2L, IncidentChanged.Kind.OPENED))).doesNotThrowAnyException();
        assertThatCode(() -> fallback.deleteChannels(2L)).doesNotThrowAnyException();
    }

    @Test
    void historyThatCannotBeDeletedIsLeftWithoutFailing() {
        CheckHistoryClient fallback = new CheckHistoryClientFallbackFactory().create(cause);

        assertThatCode(() -> fallback.deleteHistory(3L)).doesNotThrowAnyException();
    }
}
