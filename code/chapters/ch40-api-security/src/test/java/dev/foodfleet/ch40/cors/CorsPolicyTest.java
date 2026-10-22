package dev.foodfleet.ch40.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CorsPolicyTest {

    static CorsPolicy policy() {
        return CorsPolicy.of(Set.of("https://www.foodfleet.in", "https://partners.foodfleet.in:8443"),
                Set.of("GET", "POST", "PATCH"), Set.of("Authorization", "Content-Type"), List.of("RateLimit"), true,
                Duration.ofMinutes(10));
    }

    @Test
    void onlyTheListedOriginsExactly() {
        CorsPolicy p = policy();
        assertThat(p.allowsOrigin("https://www.foodfleet.in")).isTrue();
        assertThat(p.allowsOrigin("https://WWW.FoodFleet.in:443")).isTrue();
        assertThat(p.allowsOrigin("https://partners.foodfleet.in:8443")).isTrue();
        for (String o : new String[] {"https://partners.foodfleet.in", "http://www.foodfleet.in",
                "https://evil-www.foodfleet.in", "https://www.foodfleet.in.evil.example", "https://evilfoodfleet.in",
                "https://foodfleet.in", "null", "https://www.foodfleet.in/", "https://www.foodfleet.in@evil.example",
                "https://*.foodfleet.in", "", " https://www.foodfleet.in"}) {
            assertThat(p.allowsOrigin(o)).as(o).isFalse();
        }
        assertThat(p.allowsOrigin(null)).isFalse();
    }

    @Test
    void actualResponsesEchoTheOriginAndAlwaysVary() {
        Map<String, String> ok = policy().actual("https://www.foodfleet.in");
        assertThat(ok.get("Access-Control-Allow-Origin")).isEqualTo("https://www.foodfleet.in");
        assertThat(ok.get("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(ok.get("Vary")).isEqualTo("Origin");
        Map<String, String> no = policy().actual("https://evil.example");
        assertThat(no.containsKey("Access-Control-Allow-Origin")).isFalse();
        assertThat(no.get("Vary")).isEqualTo("Origin");
    }

    @Test
    void preflightChecksMethodAndHeaders() {
        CorsPolicy p = policy();
        assertThat(p.preflight("https://www.foodfleet.in", "PATCH", "authorization, content-type")).isPresent();
        assertThat(p.preflight("https://www.foodfleet.in", "DELETE", null)).isEmpty();
        assertThat(p.preflight("https://www.foodfleet.in", "POST", "x-admin-override")).isEmpty();
        assertThat(p.preflight("https://evil.example", "GET", null)).isEmpty();
        assertThat(p.preflight("https://www.foodfleet.in", "GET", null).get().get("Access-Control-Max-Age")).isEqualTo("600");
    }

    @Test
    void refusesConfigurationThatIsNotAnExactHttpsOrigin() {
        for (String bad : new String[] {"*", "null", "https://*.foodfleet.in", "https://www.foodfleet.in/app",
                "http://www.foodfleet.in", "www.foodfleet.in"}) {
            assertThatThrownBy(() -> CorsPolicy.of(Set.of(bad), Set.of("GET"), Set.of(), List.of(), true,
                    Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(CorsPolicy.of(Set.of("http://localhost:4200"), Set.of("GET"), Set.of(), List.of(), true,
                Duration.ofMinutes(1)).allowsOrigin("http://localhost:4200")).isTrue();
    }
}
