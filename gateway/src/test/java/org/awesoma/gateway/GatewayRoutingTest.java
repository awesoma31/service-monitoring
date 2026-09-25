package org.awesoma.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Routes come from the config repository. With no instance of the target service registered,
 * a routed path answers 503 from the load balancer, while an unrouted one is simply not found.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {

    @Autowired private WebTestClient http;

    @Test
    void apiPathsAreRoutedToTheMonitorService() {
        http.get().uri("/api/v1/users").exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void checkHistoryIsRoutedToTheCheckServiceBeforeTheCatchAllRoute() {
        http.get().uri("/actuator/gateway/routes").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].route_id").isEqualTo("check-service")
                .jsonPath("$[0].uri").isEqualTo("lb://check-service")
                .jsonPath("$[1].route_id").isEqualTo("monitor-service");
    }

    @Test
    void otherPathsAreNotRouted() {
        http.get().uri("/unknown").exchange().expectStatus().isNotFound();
    }
}
