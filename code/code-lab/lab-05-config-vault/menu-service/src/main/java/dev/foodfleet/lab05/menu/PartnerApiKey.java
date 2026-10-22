package dev.foodfleet.lab05.menu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A third-party API key, read from OpenBao's KV store (secret/menu-service, key partner-api-key) through
 * spring.config.import=vault://. It's held in memory only: never logged, never in an environment variable
 * (chapter 39 read environment variables back through /proc) and never in a heap dump endpoint.
 */
@Component
public class PartnerApiKey {

    private final String value;

    public PartnerApiKey(@Value("${partner-api-key:}") String value) {
        this.value = value;
    }

    public boolean present() {
        return !value.isBlank();
    }

    String value() {
        return value;
    }

    @Override
    public String toString() {
        return "PartnerApiKey[****]";
    }
}
