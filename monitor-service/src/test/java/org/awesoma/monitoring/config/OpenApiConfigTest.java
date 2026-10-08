package org.awesoma.monitoring.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void swaggerExecutesRequestsThroughTheCurrentGatewayOrigin() {
        var openApi = new OpenApiConfig().serviceMonitoringOpenApi();

        assertThat(openApi.getServers()).singleElement().extracting("url").isEqualTo("/");
    }
}
