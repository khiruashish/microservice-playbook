package dev.foodfleet.ch40.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch40.cors.CorsPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FetchMetadataPolicyTest {

    final FetchMetadataPolicy policy = new FetchMetadataPolicy(CorsPolicy.of(Set.of("https://www.foodfleet.in"),
            Set.of("GET", "POST"), Set.of(), List.of(), true, Duration.ofMinutes(1)));

    @Test
    void sameOriginAndUserInitiatedRequestsPass() {
        assertThat(policy.check("POST", "same-origin", "cors", "empty", "https://www.foodfleet.in").allowed()).isTrue();
        assertThat(policy.check("GET", "none", "navigate", "document", null).allowed()).isTrue();
    }

    @Test
    void crossSiteStateChangesAreRefused() {
        assertThat(policy.check("POST", "cross-site", "navigate", "document", "https://evil.example").allowed()).isFalse();
        assertThat(policy.check("POST", "cross-site", "no-cors", "empty", "https://evil.example").allowed()).isFalse();
        assertThat(policy.check("GET", "cross-site", "no-cors", "image", null).allowed()).isFalse();
    }

    @Test
    void followingALinkFromAnotherSiteStillWorks() {
        assertThat(policy.check("GET", "cross-site", "navigate", "document", null).allowed()).isTrue();
        assertThat(policy.check("GET", "cross-site", "navigate", "object", null).allowed()).isFalse();
    }

    @Test
    void siblingSubdomainsAreNotTrustedJustForBeingSameSite() {
        assertThat(policy.check("POST", "same-site", "navigate", "document", "https://reviews.foodfleet.in").allowed()).isFalse();
        assertThat(policy.check("POST", "same-site", "cors", "empty", "https://www.foodfleet.in").allowed()).isTrue();
    }

    @Test
    void withoutFetchMetadataTheOriginDecidesWhenThereIsOne() {
        assertThat(policy.check("POST", null, null, null, "https://evil.example").allowed()).isFalse();
        assertThat(policy.check("POST", null, null, null, "https://www.foodfleet.in").allowed()).isTrue();
        assertThat(policy.check("POST", null, null, null, null).allowed()).isTrue();   // curl: the CSRF token decides
    }
}
