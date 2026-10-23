package org.awesoma.security.serviceauth;

@FunctionalInterface
public interface ServiceTokenProvider {
    String token();
}
