package org.awesoma.monitoring.application.model;

/** A page request independent of the delivery and persistence frameworks. */
public record PageRequest(int page, int size) {
    public static PageRequest of(int page, int size) {
        return new PageRequest(page,size);
    }
    public static PageRequest unpaged() {
        return new PageRequest(0,Integer.MAX_VALUE);
    }
    public PageRequest {
        if (page < 0 || size < 1) throw new IllegalArgumentException("Invalid page");
    }
}
