package org.awesoma.report.application.port;

public class ObjectNotFoundException extends RuntimeException {
    public ObjectNotFoundException(String key) {
        super("Object " + key + " does not exist");
    }
}
