package dev.foodfleet.ch15.app.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("foodfleet.outbox")
public record OutboxProperties(@DefaultValue("500") long pollIntervalMs,
                               @DefaultValue("100") int batchSize,
                               @DefaultValue("7") int retentionDays) {
}
