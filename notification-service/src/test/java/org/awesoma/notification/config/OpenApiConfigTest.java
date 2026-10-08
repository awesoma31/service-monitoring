package org.awesoma.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void swaggerExecutesRequestsThroughTheCurrentGatewayOrigin() {
        var openApi = new OpenApiConfig().notificationServiceOpenApi();

        assertThat(openApi.getServers()).singleElement().extracting("url").isEqualTo("/");
    }
}
