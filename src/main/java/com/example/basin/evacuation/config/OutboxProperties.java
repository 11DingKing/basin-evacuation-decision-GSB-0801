package com.example.basin.evacuation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.outbox")
public record OutboxProperties(int batchSize) {
}
