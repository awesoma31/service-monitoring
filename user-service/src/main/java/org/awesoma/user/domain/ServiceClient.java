package org.awesoma.user.domain;

public record ServiceClient(String id, String secretHash, boolean enabled) {}
