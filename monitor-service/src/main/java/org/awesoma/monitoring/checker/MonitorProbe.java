package org.awesoma.monitoring.checker;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Performs a single HTTP probe. Holds no state and touches no database, which is what lets
 * the whole package move to a service of its own later.
 */
@Component
public class MonitorProbe {

    public ProbeOutcome probe(MonitorTarget target) {
        long startedAt = System.nanoTime();
        try {
            int status = call(target);
            int elapsed = millisSince(startedAt);
            return status == target.expectedStatus()
                    ? ProbeOutcome.success(elapsed, status)
                    : ProbeOutcome.badStatus(elapsed, status, target.expectedStatus());
        } catch (ResourceAccessException exception) {
            int elapsed = millisSince(startedAt);
            return isTimeout(exception)
                    ? ProbeOutcome.timeout(elapsed, describe(exception))
                    : ProbeOutcome.connectionError(elapsed, describe(exception));
        } catch (RuntimeException exception) {
            return ProbeOutcome.connectionError(millisSince(startedAt), describe(exception));
        }
    }

    private int call(MonitorTarget target) {
        return clientFor(target)
                .method(HttpMethod.valueOf(target.httpMethod().name()))
                .uri(target.url())
                .retrieve()
                // Any status is an answer worth recording; only transport failures throw.
                .onStatus(status -> true, (request, response) -> { })
                .toBodilessEntity()
                .getStatusCode()
                .value();
    }

    /**
     * A client per probe, because the timeout belongs to the monitor rather than to the
     * client. Building one is cheap next to the network call it wraps.
     */
    private RestClient clientFor(MonitorTarget target) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(target.timeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    private boolean isTimeout(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
            if (cause instanceof IOException && cause.getMessage() != null
                    && cause.getMessage().toLowerCase().contains("timed out")) {
                return true;
            }
        }
        return false;
    }

    private String describe(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }

    private int millisSince(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000);
    }
}
