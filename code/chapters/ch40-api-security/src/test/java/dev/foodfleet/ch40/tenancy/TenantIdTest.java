package dev.foodfleet.ch40.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void acceptsSlugs() {
        assertThat(new TenantId("foodfleet-in").value()).isEqualTo("foodfleet-in");
        assertThat(new TenantId("spice-route2").toString()).isEqualTo("spice-route2");
    }

    @Test
    void rejectsAnythingElse() {
        for (String bad : new String[] {"", "a", "Foodfleet", "x'; set app.tenant_id='y", "a b", "-lead", "trail-",
                "a".repeat(41), "foodfleet_in"}) {
            assertThatThrownBy(() -> new TenantId(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new TenantId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
