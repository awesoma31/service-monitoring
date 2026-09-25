package org.awesoma.gateway;

import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import java.util.Set;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Where a route's circuit breaker forwards a request that its service could not serve: the
 * service is down, not registered yet, too slow, or its circuit is open. The answer uses the
 * same RFC 7807 shape as the services behind the gateway.
 */
@Hidden
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<ProblemDetail> unavailable(
            @PathVariable String service, ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "%s is temporarily unavailable, try again later".formatted(service));
        problem.setTitle("Service unavailable");
        problem.setInstance(originalPath(exchange));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    private URI originalPath(ServerWebExchange exchange) {
        Set<URI> originals = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        URI original = originals == null || originals.isEmpty()
                ? exchange.getRequest().getURI()
                : originals.iterator().next();
        return URI.create(original.getPath());
    }
}
