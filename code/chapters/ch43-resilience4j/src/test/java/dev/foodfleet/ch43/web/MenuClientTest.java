package dev.foodfleet.ch43.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

class MenuClientTest {

    private final MenuCache cache = new MenuCache();
    private final MenuClient client = new MenuClient(null, cache);

    @Test
    void fallbackServesTheLastKnownMenuMarkedStale() {
        cache.put(new Menu("r1", List.of("masala dosa"), Instant.parse("2025-08-12T10:00:00Z"), false));
        Menu menu = client.lastKnownMenu("r1", new IOException("down"));
        assertThat(menu.stale()).isTrue();
        assertThat(menu.items()).containsExactly("masala dosa");
    }

    @Test
    void withoutACachedMenuTheFailureSurfaces() {
        assertThatThrownBy(() -> client.lastKnownMenu("r2", new IOException("down")))
                .isInstanceOf(MenuUnavailableException.class).hasMessageContaining("r2");
    }

    @Test
    void only4xxThatAreAboutTheRequestCountAsClientErrors() {
        assertThat(ResilienceConfig.clientError(HttpStatusCode.valueOf(404))).isTrue();
        assertThat(ResilienceConfig.clientError(HttpStatusCode.valueOf(422))).isTrue();
        assertThat(ResilienceConfig.clientError(HttpStatusCode.valueOf(408))).isFalse();
        assertThat(ResilienceConfig.clientError(HttpStatusCode.valueOf(429))).isFalse();
        assertThat(ResilienceConfig.clientError(HttpStatusCode.valueOf(503))).isFalse();
    }
}
