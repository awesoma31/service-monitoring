package org.awesoma.security;

@FunctionalInterface
public interface ServiceTokenProvider {
    String token();
}
