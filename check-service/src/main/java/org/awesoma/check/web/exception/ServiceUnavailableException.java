package org.awesoma.check.web.exception;

/** A service this one depends on cannot answer right now; reported to the client as 503. */
public class ServiceUnavailableException extends RuntimeException {

    private final String service;

    public ServiceUnavailableException(String service, Throwable cause) {
        super("%s is temporarily unavailable".formatted(service), cause);
        this.service = service;
    }

    public String service() {
        return service;
    }
}
