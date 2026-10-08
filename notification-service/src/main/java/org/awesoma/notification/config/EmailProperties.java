package org.awesoma.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.email")
public record EmailProperties(boolean enabled, String from) {}
