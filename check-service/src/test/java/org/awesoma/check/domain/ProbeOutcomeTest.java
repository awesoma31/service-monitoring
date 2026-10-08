package org.awesoma.check.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProbeOutcomeTest {

    @Test
    void limitsErrorMessagesToTheReceivingServicesColumnSize() {
        ProbeOutcome outcome = ProbeOutcome.connectionError(10, "x".repeat(501));

        assertThat(outcome.errorMessage()).hasSize(500);
    }

    @Test
    void keepsShortAndAbsentErrorMessagesUnchanged() {
        assertThat(ProbeOutcome.timeout(10, "timeout").errorMessage()).isEqualTo("timeout");
        assertThat(ProbeOutcome.connectionError(10, null).errorMessage()).isNull();
    }
}
