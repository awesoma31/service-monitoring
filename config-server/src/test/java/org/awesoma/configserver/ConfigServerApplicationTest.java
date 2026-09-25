package org.awesoma.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTest {

    @Autowired private TestRestTemplate http;

    @Test
    void servesTheServiceFileTogetherWithTheSharedOne() {
        JsonNode config = http.getForObject("/monitor-service/default", JsonNode.class);

        assertThat(config.get("name").asText()).isEqualTo("monitor-service");
        assertThat(config.get("propertySources").findValuesAsText("name"))
                .anyMatch(source -> source.endsWith("config-repo/monitor-service.yml"))
                .anyMatch(source -> source.endsWith("config-repo/application.yml"));
        assertThat(config.toString())
                .contains("checker.interval-ms")
                .contains("spring.jackson.property-naming-strategy");
    }

    @Test
    void servesTheGatewayRoutes() {
        JsonNode config = http.getForObject("/gateway/default", JsonNode.class);

        assertThat(config.toString()).contains("lb://monitor-service");
    }
}
