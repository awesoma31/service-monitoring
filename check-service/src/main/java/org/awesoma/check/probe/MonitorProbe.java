package org.awesoma.check.probe;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Performs one HTTP probe without blocking. Any status is an answer worth recording; only a
 * transport failure or the monitor's own timeout turns into an error outcome.
 */
@Component
public class MonitorProbe {

    private final WebClient webClient;

    public MonitorProbe(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public Mono<ProbeOutcome> probe(MonitorTarget target) {
        return Mono.fromSupplier(System::nanoTime).flatMap(startedAt -> status(target)
                // The whole exchange, connecting included, must fit in the monitor's timeout.
                .timeout(Duration.ofMillis(target.timeoutMs()))
                .map(status -> status == target.expectedStatus()
                        ? ProbeOutcome.success(millisSince(startedAt), status)
                        : ProbeOutcome.badStatus(millisSince(startedAt), status, target.expectedStatus()))
                .onErrorResume(TimeoutException.class, timeout -> Mono.just(ProbeOutcome.timeout(
                        millisSince(startedAt), "No answer within %d ms".formatted(target.timeoutMs()))))
                .onErrorResume(failure -> Mono.just(ProbeOutcome.connectionError(
                        millisSince(startedAt), describe(failure)))));
    }

    /**
     * Deferred so that a URL the client cannot even build a request for fails inside the
     * pipeline, where it becomes a connection error like any other unreachable address.
     */
    private Mono<Integer> status(MonitorTarget target) {
        return Mono.defer(() -> webClient
                .method(HttpMethod.valueOf(target.httpMethod().name()))
                .uri(target.url())
                .exchangeToMono(response ->
                        response.releaseBody().thenReturn(response.statusCode().value())));
    }

    private String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private int millisSince(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000);
    }
}
