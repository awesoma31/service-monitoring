package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.junit.jupiter.api.Test;

class CheckResultTypeTest {

    @Test
    void onlySuccessIsNotAFailure() {
        assertThat(CheckResultType.SUCCESS.isFailure()).isFalse();
        assertThat(CheckResultType.TIMEOUT.isFailure()).isTrue();
        assertThat(CheckResultType.BAD_STATUS.isFailure()).isTrue();
        assertThat(CheckResultType.CONNECTION_ERROR.isFailure()).isTrue();
    }
}
