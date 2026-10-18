package org.awesoma.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Permission lookup uses service credentials and an identity from a verified user JWT. */
public class RemoteAccess {
    private final ResourceAccessClient client;

    public RemoteAccess(ResourceAccessClient client) { this.client = client; }

    public Mono<Void> require(String resource, Long id, Jwt jwt, boolean write) {
        return Mono.fromCallable(() -> client.allowed(resource, id, Long.valueOf(jwt.getSubject()), write))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Access permissions cannot currently be verified", error))
                .flatMap(allowed -> allowed ? Mono.empty()
                        : Mono.error(new AccessDeniedException("No access to this resource")));
    }
}
